use colored::Colorize;
use indicatif::{ProgressBar, ProgressStyle};
use reqwest::Client;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::time::Duration;
use std::net::{TcpStream, ToSocketAddrs};
use std::str::FromStr;
use std::net::Ipv4Addr;

use crate::dns;
use crate::ui;

#[derive(Debug, Clone)]
pub struct ScanResult {
    pub subdomain: String,
    pub ip: String,
    pub is_cloudflare: bool,
    pub is_working: bool,
    pub status_code: Option<u16>,
}

// Check if IP is in reserved/private range
fn is_reserved_ip(ip: &str) -> (bool, &'static str) {
    let parsed_ip = match Ipv4Addr::from_str(ip) {
        Ok(addr) => addr,
        Err(_) => return (false, ""),
    };
    
    let octets = parsed_ip.octets();
    
    // Check common reserved ranges
    match octets[0] {
        10 => (true, "Private (10.0.0.0/8)"),
        172 if octets[1] >= 16 && octets[1] <= 31 => (true, "Private (172.16.0.0/12)"),
        192 if octets[1] == 168 => (true, "Private (192.168.0.0/16)"),
        198 if octets[1] == 18 || octets[1] == 19 => (true, "Benchmark/VPN (198.18.0.0/15)"),
        100 if octets[1] >= 64 && octets[1] <= 127 => (true, "CGNAT (100.64.0.0/10)"),
        127 => (true, "Loopback (127.0.0.0/8)"),
        0 => (true, "Reserved (0.0.0.0/8)"),
        _ => (false, ""),
    }
}

// TCP port check with timeout
fn tcp_port_check(host: &str, port: u16, timeout_secs: u64) -> bool {
    let addr = format!("{}:{}", host, port);
    
    // Try to resolve socket addresses
    let socket_addrs: Vec<_> = match addr.to_socket_addrs() {
        Ok(addrs) => addrs.collect(),
        Err(_) => return false,
    };
    
    if socket_addrs.is_empty() {
        return false;
    }
    
    // Try TCP connect with timeout
    match TcpStream::connect_timeout(
        &socket_addrs[0],
        Duration::from_secs(timeout_secs)
    ) {
        Ok(_) => true,
        Err(_) => false,
    }
}

pub async fn test_target(target: &str, timeout: u64) -> anyhow::Result<()> {
    println!("\n{}", "Testing target host...".cyan());
    println!("{}", "━".repeat(50).bright_black());
    
    // DNS check first
    println!("\n{} Checking DNS resolution...", "🔍".cyan());
    let resolved_ip = match dns::resolve_domain_first(target).await {
        Ok(ip) => {
            println!("{} {} → {}", "✓".green(), target.green(), ip.bright_black());
            ip
        }
        Err(e) => {
            println!("{} DNS resolution failed: {}", "✗".red(), e.to_string().red());
            println!("\n{}", "Possible causes:".yellow());
            println!("  - Domain tidak exist atau typo");
            println!("  - DNS server bermasalah (coba: pkg install dnsutils)");
            println!("  - Tidak ada koneksi internet");
            return Err(e);
        }
    };
    
    // Check if reserved/private IP
    let (is_reserved, ip_type) = is_reserved_ip(&resolved_ip);
    
    if is_reserved {
        println!("\n{} {} {}", "⚠️".yellow(), "Reserved IP Range:".yellow().bold(), ip_type.yellow());
        println!("   This is likely a VPN/Tunnel/Private server");
        println!("   HTTP requests might not work (normal behavior)");
        
        // Try TCP port check as alternative
        println!("\n{} Checking TCP port availability...", "🔌".cyan());
        
        let ports = vec![
            (443, "HTTPS"),
            (80, "HTTP"),
            (8080, "HTTP-Alt"),
            (8443, "HTTPS-Alt"),
        ];
        
        let mut any_port_open = false;
        let mut open_ports = Vec::new();
        
        for (port, name) in &ports {
            print!("   Port {} ({}): ", port, name);
            std::io::Write::flush(&mut std::io::stdout()).ok();
            
            if tcp_port_check(target, *port, 3) {
                println!("{}", "OPEN".green());
                any_port_open = true;
                open_ports.push((*port, *name));
            } else {
                println!("{}", "CLOSED".dimmed());
            }
        }
        
        // Status summary
        println!("\n{}", "═".repeat(50).cyan());
        
        if any_port_open {
            println!("{}", "✅ SERVER ONLINE (TCP Ports Responding)".green().bold());
            println!("\n{}", "Open Ports:".bright_black());
            for (port, name) in open_ports {
                println!("  {} {} ({})", "🟢".green(), port.to_string().green(), name);
            }
            println!("\n{}", "Note:".yellow());
            println!("  VPN/Tunnel servers tidak respond HTTP directly");
            println!("  Gunakan menu 'Test Single Subdomain' untuk inject testing");
        } else {
            println!("{}", "❌ SERVER DOWN OR FIREWALLED".red().bold());
            println!("\n{}", "Possible causes:".yellow());
            println!("  - Server sedang down/maintenance");
            println!("  - Strict firewall blocking all ports");
            println!("  - Network connectivity issues");
            println!("  - Try different network/VPN");
        }
        
        println!("{}", "═".repeat(50).cyan());
        
        // Return OK to allow subdomain testing
        return Ok(());
    }
    
    // Non-reserved IP: Try normal HTTP/HTTPS
    // Check if Cloudflare IP
    if dns::is_cloudflare_ip(&resolved_ip) {
        println!("{} Cloudflare IP detected", "☁️".cyan());
    }
    
    // Build client with proper settings
    println!("\n{} Building HTTP client...", "🔧".bright_black());
    let client = Client::builder()
        .timeout(Duration::from_secs(timeout))
        .connect_timeout(Duration::from_secs(5))
        .danger_accept_invalid_certs(true)
        .build()
        .map_err(|e| {
            eprintln!("{} Failed to build client: {}", "✗".red(), e);
            e
        })?;
    
    // Try HTTPS first (modern default), then HTTP
    let protocols = vec![
        ("https", 443),
        ("http", 80),
    ];
    
    let mut last_error = None;
    
    for (protocol, port) in protocols {
        let url = format!("{}://{}", protocol, target);
        println!("\n{} Testing: {}", "📡".cyan(), url.bright_black());
        println!("{} Timeout: {}s", "⏱️".bright_black(), timeout);
        
        match client.get(&url).send().await {
            Ok(response) => {
                let status = response.status().as_u16();
                
                // Check if 400 with HTTPS error message
                if status == 400 && protocol == "http" {
                    if let Ok(body) = response.text().await {
                        if body.contains("HTTPS port") || body.contains("SSL") {
                            println!("{} 400 Bad Request: Server requires HTTPS", "⚠️".yellow());
                            continue; // Skip to HTTPS
                        }
                    }
                }
                
                println!("{} Status: {} via {}", "✓".green(), 
                         status.to_string().green(), 
                         protocol.to_uppercase());
                
                println!("\n{}", "═".repeat(50).green());
                println!("{}", "✅ SERVER ONLINE (HTTP Responding)".green().bold());
                println!("{} {}", "Protocol:".bright_black(), protocol.to_uppercase().green());
                println!("{} {}", "Status Code:".bright_black(), status.to_string().green());
                println!("{} {}", "Port:".bright_black(), port.to_string().green());
                println!("{}", "═".repeat(50).green());
                
                return Ok(()); // Success!
            }
            Err(e) => {
                println!("{} Failed via {}: {}", "✗".yellow(), protocol.to_uppercase(), e.to_string().dimmed());
                last_error = Some(e);
                // Continue to next protocol
            }
        }
    }
    
    // HTTP/HTTPS failed - try TCP as fallback
    if let Some(e) = last_error {
        println!("\n{} HTTP/HTTPS tidak respond, checking TCP ports...", "🔌".cyan());
        
        let tcp_ports = vec![(443, "HTTPS"), (80, "HTTP")];
        let mut tcp_open = false;
        
        for (port, name) in &tcp_ports {
            print!("   Port {} ({}): ", port, name);
            std::io::Write::flush(&mut std::io::stdout()).ok();
            
            if tcp_port_check(target, *port, 3) {
                println!("{}", "OPEN".green());
                tcp_open = true;
            } else {
                println!("{}", "CLOSED".dimmed());
            }
        }
        
        println!("\n{}", "═".repeat(50).yellow());
        
        if tcp_open {
            println!("{}", "⚠️  SERVER ONLINE BUT HTTP NOT RESPONDING".yellow().bold());
            println!("\n{}", "Possible causes:".cyan());
            println!("  - Server is VPN/Tunnel (tidak serve HTTP directly)");
            println!("  - Firewall blocking HTTP but allowing TCP");
            println!("  - Server misconfiguration");
            println!("\n{}", "Recommendation:".green());
            println!("  Lanjut ke 'Test Single Subdomain' untuk inject testing");
        } else {
            println!("{}", "❌ SERVER DOWN OR UNREACHABLE".red().bold());
            
            if e.is_timeout() {
                println!("\n{}", "⏱️  Timeout Error".yellow().bold());
                println!("Request exceeded {}s limit", timeout);
                println!("\n{}", "Possible solutions:".cyan());
                println!("  1. Increase timeout: Settings → Change Timeout");
                println!("  2. Check internet connection stability");
                println!("  3. Try using VPN or different network");
                println!("  4. Verify target host is online");
            } else if e.is_connect() {
                println!("\n{}", "🔌 Connection Error".yellow().bold());
                println!("Cannot establish connection to host");
                println!("\n{}", "Possible solutions:".cyan());
                println!("  1. Verify target format: example.com or ip:port");
                println!("  2. Check if host requires authentication");
                println!("  3. Confirm host is accessible from your location");
            } else if e.is_request() {
                println!("\n{}", "📡 Request Error".yellow().bold());
                println!("Invalid request format or parameters");
            } else {
                println!("\n{}", "❓ Unknown Error".yellow().bold());
                println!("{}", e);
            }
        }
        
        println!("{}", "═".repeat(50).yellow());
        
        // Don't fail - allow subdomain testing
        return Ok(());
    }
    
    Ok(())
}

pub async fn test_single(target: &str, subdomain: &str, timeout: u64) -> anyhow::Result<()> {
    println!("\n{}", "Testing subdomain...".cyan());
    println!("{}", "━".repeat(50).bright_black());
    println!("\n{} {}", "Subdomain:".bright_black(), subdomain);
    println!("{} {}", "Target:".bright_black(), target);
    
    // DNS resolution
    print!("\n{} Resolving DNS...", "📡".cyan());
    match dns::resolve_domain_first(subdomain).await {
        Ok(ip) => {
            println!(" {} {}", "✓".green(), ip.green());
            
            let is_cf = dns::is_cloudflare_ip(&ip);
            if is_cf {
                println!("{} {} {}", "☁️".cyan(), "Cloudflare IP detected".cyan(), ip.bright_black());
            } else {
                println!("{} {} {}", "🌐".yellow(), "Non-Cloudflare IP".yellow(), ip.bright_black());
            }
            
            // HTTP test
            print!("\n{} Testing connection...", "🔌".cyan());
            let client = Client::builder()
                .timeout(Duration::from_secs(timeout))
                .connect_timeout(Duration::from_secs(5))
                .danger_accept_invalid_certs(true)
                .build()?;

            // Try HTTPS first, then HTTP
            let protocols = vec!["https", "http"];
            let mut success = false;

            for protocol in protocols {
                let url = format!("{}://{}", protocol, target);
                let request = client
                    .get(&url)
                    .header("Host", subdomain)
                    .build()?;

                match client.execute(request).await {
                    Ok(response) => {
                        let status = response.status().as_u16();
                        
                        // Skip HTTP 400 if server wants HTTPS
                        if status == 400 && protocol == "http" {
                            if let Ok(body) = response.text().await {
                                if body.contains("HTTPS port") {
                                    continue; // Try HTTPS next
                                }
                            }
                        }
                        
                        println!(" {} Status: {} via {}", "✓".green(), 
                                 status.to_string().green(),
                                 protocol.to_uppercase());
                        
                        if is_cf {
                            println!("\n{}", "═".repeat(50).green());
                            println!("{}", "✅ WORKING BUG!".green().bold());
                            println!("{} {}", "Subdomain:".bright_black(), subdomain.green());
                            println!("{} {}", "IP:".bright_black(), ip.green());
                            println!("{} {}", "Status:".bright_black(), status.to_string().green());
                            println!("{} {}", "Protocol:".bright_black(), protocol.to_uppercase().green());
                            println!("{}", "═".repeat(50).green());
                        } else {
                            println!("\n{}", "⚠️  Not a Cloudflare IP".yellow());
                        }
                        
                        success = true;
                        break; // Success, exit loop
                    }
                    Err(e) => {
                        if protocol == "http" {
                            // Don't print error for HTTP, will try HTTPS
                            continue;
                        }
                        println!(" {} {}", "✗".red(), format!("Error: {}", e).red());
                        
                        if e.is_timeout() {
                            println!("{}", "   Timeout: Request took too long".yellow());
                        } else if e.is_connect() {
                            println!("{}", "   Connection failed to target".yellow());
                        }
                    }
                }
            }

            if !success {
                println!("{}", "   Both HTTP and HTTPS failed".red());
            }
        }
        Err(e) => {
            println!(" {} {}", "✗".red(), format!("DNS resolution failed: {}", e).red());
        }
    }
    
    Ok(())
}

pub async fn batch_test(
    target: &str,
    subdomains: &[String],
    timeout: u64,
    running: Arc<AtomicBool>,
) -> anyhow::Result<Vec<ScanResult>> {
    let total = subdomains.len();
    let mut results = Vec::new();
    
    println!("\n{}", "Starting batch test...".cyan());
    println!("{} {} subdomains\n", "Total:".bright_black(), total.to_string().yellow());
    
    let pb = ProgressBar::new(total as u64);
    pb.set_style(
        ProgressStyle::default_bar()
            .template("{spinner:.green} [{bar:40.cyan/blue}] {pos}/{len} ({percent}%) {msg}")
            .unwrap()
            .progress_chars("█▓▒░"),
    );

    let client = Client::builder()
        .timeout(Duration::from_secs(timeout))
        .connect_timeout(Duration::from_secs(5))
        .danger_accept_invalid_certs(true)
        .build()?;

    for subdomain in subdomains {
        if !running.load(Ordering::SeqCst) {
            pb.finish_with_message("Cancelled");
            break;
        }

        pb.set_message(format!("Testing: {}", subdomain));
        
        // DNS resolution
        if let Ok(ip) = dns::resolve_domain_first(subdomain).await {
            let is_cf = dns::is_cloudflare_ip(&ip);
            
            // Try HTTPS first, then HTTP for batch testing
            let protocols = vec!["https", "http"];
            
            for protocol in protocols {
                let url = format!("{}://{}", protocol, target);
                let request = client
                    .get(&url)
                    .header("Host", subdomain)
                    .build();

                if let Ok(req) = request {
                    if let Ok(response) = client.execute(req).await {
                        let status = response.status().as_u16();
                        
                        // Skip HTTP 400 for HTTPS-only servers
                        if status == 400 && protocol == "http" {
                            continue;
                        }
                        
                        results.push(ScanResult {
                            subdomain: subdomain.clone(),
                            ip: ip.clone(),
                            is_cloudflare: is_cf,
                            is_working: is_cf && status < 500,
                            status_code: Some(status),
                        });
                        
                        break; // Success with this protocol
                    }
                }
            }
        }
        
        pb.inc(1);
    }
    
    pb.finish_with_message("Complete");
    
    // Display results
    let working: Vec<_> = results.iter().filter(|r| r.is_working).collect();
    let non_cf: Vec<_> = results.iter().filter(|r| !r.is_cloudflare && r.status_code.is_some()).collect();
    
    println!("\n{}", "═".repeat(60).cyan());
    ui::center_text("HASIL SCAN");
    println!("{}", "═".repeat(60).cyan());
    
    if working.is_empty() {
        println!("\n{}", "❌ Tidak ada working bug ditemukan".yellow());
    } else {
        println!("\n{} Working Bugs ({}):", "✅".green(), working.len());
        for result in &working {
            println!("  {} {} ({})", "🟢".green(), result.subdomain.green(), result.ip.bright_black());
        }
    }
    
    if !non_cf.is_empty() {
        println!("\n{} Non-CF Responses ({}):", "📝".yellow(), non_cf.len());
        for result in non_cf.iter().take(5) {
            println!("  {} {} ({})", "🟡".yellow(), result.subdomain, result.ip.bright_black());
        }
        if non_cf.len() > 5 {
            println!("  ... and {} more", non_cf.len() - 5);
        }
    }
    
    println!("\n{}", "─".repeat(60).bright_black());
    println!("{}" , "Statistik:");
    println!("  Scanned: {}/{} ({}%)", results.len(), total, (results.len() * 100 / total.max(1)));
    println!("  CF Found: {} | Non-CF: {}", working.len(), non_cf.len());
    println!("{}", "─".repeat(60).bright_black());
    
    Ok(results)
}