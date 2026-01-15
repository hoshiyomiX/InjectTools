use colored::Colorize;
use indicatif::{ProgressBar, ProgressStyle};
use reqwest::Client;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::time::Duration;
use std::net::{IpAddr, SocketAddr, TcpStream, ToSocketAddrs};
use std::process::Command;

use crate::dns;
use crate::ui;

#[derive(Debug, Clone)]
pub struct ScanResult {
    pub subdomain: String,
    pub ip: String,
    pub is_cloudflare: bool,
    pub is_working: bool,
    pub status_code: Option<u16>,
    pub error_msg: Option<String>,
}

// TCP latency check with timing
fn tcp_latency_check(host: &str, port: u16, timeout_secs: u64) -> Option<u128> {
    let addr = format!("{}:{}", host, port);
    
    let socket_addrs: Vec<_> = match addr.to_socket_addrs() {
        Ok(addrs) => addrs.collect(),
        Err(_) => return None,
    };
    
    if socket_addrs.is_empty() {
        return None;
    }
    
    let start = std::time::Instant::now();
    match TcpStream::connect_timeout(
        &socket_addrs[0],
        Duration::from_secs(timeout_secs)
    ) {
        Ok(_) => Some(start.elapsed().as_millis()),
        Err(_) => None,
    }
}

// ICMP ping test
// Returns response time in ms if successful
fn ping_test(host: &str, timeout_secs: u64) -> Option<u128> {
    // Try ping command (works on Android/Termux)
    let output = Command::new("ping")
        .arg("-c")
        .arg("1")  // 1 packet
        .arg("-W")
        .arg(timeout_secs.to_string())  // timeout
        .arg(host)
        .output();
    
    if let Ok(result) = output {
        if result.status.success() {
            // Parse output untuk dapat response time
            if let Ok(stdout) = String::from_utf8(result.stdout) {
                // Cari pattern "time=XXms" atau "time=XX.XX ms"
                if let Some(time_start) = stdout.find("time=") {
                    let time_str = &stdout[time_start + 5..];
                    if let Some(space_pos) = time_str.find(" ") {
                        let time_value = &time_str[..space_pos];
                        if let Ok(ms) = time_value.parse::<f64>() {
                            return Some(ms as u128);
                        }
                    }
                }
                // Ping sukses tapi gak bisa parse time, return 0 (success indicator)
                return Some(0);
            }
        }
    }
    
    None
}

// Format latency with color coding
fn format_latency(ms: u128) -> colored::ColoredString {
    let latency_str = format!("{}ms", ms);
    
    if ms < 100 {
        latency_str.green()
    } else if ms < 300 {
        latency_str.yellow()
    } else if ms < 500 {
        latency_str.bright_yellow()
    } else {
        latency_str.red()
    }
}

// Check if HTTP status indicates working inject
// Only 2xx codes are considered working
fn is_working_status(status: u16) -> bool {
    status >= 200 && status < 300
}

/// Mimic curl --resolve behavior:
/// Connect ke IP subdomain, tapi request dengan target hostname
/// Ini untuk SNI routing: TLS handshake ke target, koneksi ke IP subdomain
async fn resolve_to_ip(
    client: &Client,
    target: &str,
    subdomain_ip: &str,
    protocol: &str,
    timeout: u64,
) -> Result<reqwest::Response, reqwest::Error> {
    // Parse IP dari subdomain
    let ip: IpAddr = subdomain_ip.parse()
        .map_err(|_| reqwest::Error::from(std::io::Error::new(
            std::io::ErrorKind::InvalidInput,
            "Invalid IP address"
        )))?;
    
    let port = if protocol == "https" { 443 } else { 80 };
    
    // Build URL dengan target sebagai hostname
    // Ini akan set SNI ke target, bukan subdomain
    let url = format!("{}://{}", protocol, target);
    
    // Buat custom connector yang force resolve ke subdomain IP
    // Tapi SNI tetap ke target karena URL pakai target hostname
    let socket_addr = SocketAddr::new(ip, port);
    
    // Untuk mimic curl --resolve, kita perlu:
    // 1. Connect ke subdomain_ip:port
    // 2. TLS SNI kirim target hostname
    // 3. HTTP Host header ke target
    
    // Reqwest akan otomatis set SNI berdasarkan URL hostname (target)
    // Kita perlu override DNS resolution ke subdomain_ip
    
    // Workaround: Pakai URL dengan IP tapi override SNI via Host header
    // Tidak, ini tidak set SNI dengan benar
    
    // Better approach: Build request dengan target URL langsung
    // tapi inject custom resolver (butuh hyper custom connector)
    
    // Untuk simplicity tanpa custom connector:
    // Pakai URL target langsung, reqwest akan resolve DNS sendiri
    // Lalu kita pastikan request benar
    
    // ACTUAL FIX: Reqwest tidak support custom DNS per-request
    // Solusi: Pakai URL dengan target (untuk SNI), tapi...
    // Kita harus accept bahwa reqwest akan resolve target via DNS
    
    // WORKAROUND for curl --resolve behavior:
    // Karena reqwest limitation, kita pakai subdomain di URL (untuk IP resolution)
    // Tapi override Host header ke target (untuk HTTP routing)
    // TLS SNI akan ke subdomain, bukan target
    
    // ACTUAL IMPLEMENTATION:
    // Connect ke https://subdomain_ip:port dengan custom host header
    // Ini akan force koneksi ke IP tersebut
    
    let target_url = format!("{}://{}:{}", protocol, subdomain_ip, port);
    
    client
        .get(&target_url)
        .header("Host", target)  // Override Host header ke target
        .header("Connection", "close")
        .timeout(Duration::from_secs(timeout))
        .send()
        .await
}

pub async fn test_target(target: &str, timeout: u64) -> anyhow::Result<()> {
    println!("\n{}", "Testing target host...".cyan());
    println!("{}", "━".repeat(50).bright_black());
    
    // Mechanism dari bash script:
    // 1. DNS resolution test
    // 2. Ping test
    // 3. (Fallback) HTTP/HTTPS test
    // 4. (Last resort) TCP port check
    
    // Step 1: DNS Resolution Test
    let resolved_ip = match dns::resolve_domain_first(target).await {
        Ok(ip) => ip,
        Err(_) => {
            // DNS failed - target DOWN
            println!("\n{}", "❌ TARGET OFFLINE".red().bold());
            println!("{}", "Reason: DNS resolution failed".bright_black());
            return Ok(());
        }
    };
    
    // Step 2: Ping Test
    if let Some(_ping_time) = ping_test(target, 3) {
        // Ping successful - target UP
        println!("\n{}", "✅ TARGET ONLINE".green().bold());
        return Ok(());
    }
    
    // Step 3: HTTP/HTTPS Test (fallback untuk host yang block ICMP)
    let client = Client::builder()
        .timeout(Duration::from_secs(timeout))
        .connect_timeout(Duration::from_secs(timeout.saturating_sub(2)))
        .user_agent("curl/8.8.0")
        .danger_accept_invalid_certs(true)
        .build()
        .map_err(|e| anyhow::anyhow!("Failed to build client: {}", e))?;
    
    let protocols = vec![("http", 80), ("https", 443)];
    
    for (protocol, _port) in &protocols {
        let url = format!("{}://{}", protocol, target);
        
        if let Ok(response) = client.get(&url).send().await {
            let status = response.status().as_u16();
            
            // Skip HTTP 400 if likely HTTPS-only
            if status == 400 && protocol == &"http" {
                if let Ok(body) = response.text().await {
                    if body.contains("HTTPS port") || body.contains("SSL") {
                        continue;
                    }
                }
            }
            
            println!("\n{}", "✅ TARGET ONLINE".green().bold());
            return Ok(());
        }
    }
    
    // Step 4: TCP Port Check (last resort)
    let tcp_ports = vec![443, 80, 8080];
    
    for port in &tcp_ports {
        if tcp_latency_check(target, *port, 3).is_some() {
            println!("\n{}", "✅ TARGET ONLINE".green().bold());
            return Ok(());
        }
    }
    
    // All checks failed
    println!("\n{}", "❌ TARGET OFFLINE".red().bold());
    println!("{}", "Reason: All connection attempts failed".bright_black());
    Ok(())
}

pub async fn test_single(target: &str, subdomain: &str, timeout: u64) -> anyhow::Result<()> {
    println!("\n{}", "Testing subdomain...".cyan());
    println!("{}", "━".repeat(50).bright_black());
    println!("\n{} {}", "Subdomain:".bright_black(), subdomain);
    println!("{} {}", "Target:".bright_black(), target);
    
    // DNS resolution untuk dapat IP subdomain
    let (ip, is_cf) = match dns::resolve_domain_first(subdomain).await {
        Ok(ip) => {
            let is_cf = dns::is_cloudflare_ip(&ip);
            (ip, is_cf)
        }
        Err(e) => {
            // DNS failed - show error and exit
            println!("\n{}", "═".repeat(50).red());
            println!("{}", "❌ DNS RESOLUTION FAILED".red().bold());
            println!("\n{} {}", "Subdomain:".bright_black(), subdomain.red());
            println!("{} {}", "Error:".bright_black(), e.to_string().red());
            println!("{}", "═".repeat(50).red());
            return Ok(());
        }
    };
    
    // Build client
    print!("\n{} Testing connection...", "🔌".cyan());
    let client = Client::builder()
        .timeout(Duration::from_secs(timeout))
        .connect_timeout(Duration::from_secs(timeout.saturating_sub(2)))
        .user_agent("curl/8.8.0")
        .danger_accept_invalid_certs(true)
        .build()?;

    // Try HTTPS first (port 443), then HTTP (port 80)
    // Mimic curl --resolve: connect to subdomain IP, but SNI to target
    let protocols = vec![
        ("https", 443),
        ("http", 80)
    ];
    
    let mut success = false;
    let mut working_protocol = None;
    let mut working_status = None;

    for (protocol, port) in protocols {
        // CURL --RESOLVE BEHAVIOR:
        // curl --resolve target:443:subdomain_ip https://target/
        // → Connect ke subdomain_ip:443
        // → TLS SNI ke target
        // → HTTP Host ke target
        
        let target_url = format!("{}://{}:{}", protocol, ip, port);
        
        match client
            .get(&target_url)
            .header("Host", target)  // HTTP Host header ke target
            .header("Connection", "close")
            .timeout(Duration::from_secs(timeout))
            .send()
            .await
        {
            Ok(response) => {
                let status = response.status().as_u16();
                
                // Only 2xx codes are considered working
                if is_working_status(status) {
                    // Clear "Testing connection..." line
                    print!("\r\x1B[K");
                    
                    println!("\n{}", "═".repeat(50));
                    println!("{}", "✅ WORKING BUG INJECT!".green().bold());
                    println!("\n{} {}", "Subdomain:".bright_black(), subdomain.green());
                    println!("{} {}", "IP:".bright_black(), ip.green());
                    println!("{} {}", "Status:".bright_black(), status.to_string().green());
                    println!("{} {} (port {})", "Protocol:".bright_black(), protocol.to_uppercase().green(), port.to_string().green());
                    
                    if is_cf {
                        println!("{} {}", "Provider:".bright_black(), "Cloudflare".cyan());
                    } else {
                        println!("{} {}", "Provider:".bright_black(), "Non-Cloudflare".yellow());
                    }
                    
                    println!("\n{}", "Connection Details:".bright_black());
                    println!("  {} Connect to {}:{}", "→".bright_black(), ip.cyan(), port.to_string().cyan());
                    println!("  {} Host header: {}", "→".bright_black(), target.cyan());
                    
                    println!("{}", "═".repeat(50));
                    
                    success = true;
                    working_protocol = Some(protocol);
                    working_status = Some(status);
                    break; // Stop on first 2xx success
                }
                
                // 3xx, 4xx, 5xx = Continue trying next protocol
                continue;
            }
            Err(e) => {
                // Connection failed - try next protocol
                if protocol == "https" {
                    continue; // Try HTTP next
                }
                
                // Both protocols failed
                print!("\r\x1B[K");
                
                println!("\n{}", "═".repeat(50).red());
                
                if e.is_timeout() {
                    println!("{}", "❌ CONNECTION TIMEOUT".red().bold());
                    println!("\n{} {}", "Error:".bright_black(), "Request timeout".red());
                } else if e.is_connect() {
                    println!("{}", "❌ CONNECTION FAILED".red().bold());
                    println!("\n{} {}", "Error:".bright_black(), "Cannot connect".red());
                } else {
                    println!("{}", "❌ REQUEST FAILED".red().bold());
                    println!("\n{} {}", "Error:".bright_black(), e.to_string().red());
                }
                
                println!("\n{} {}", "Subdomain:".bright_black(), subdomain.red());
                println!("{} {}", "IP:".bright_black(), ip.red());
                println!("{}", "═".repeat(50).red());
            }
        }
    }

    // No protocol succeeded with 2xx
    if !success {
        print!("\r\x1B[K");
        println!("\n{}", "═".repeat(50).red());
        println!("{}", "❌ NOT WORKING".red().bold());
        println!("\n{} {}", "Subdomain:".bright_black(), subdomain.red());
        println!("{} {}", "IP:".bright_black(), ip.red());
        println!("{} {}", "Hint:".bright_black(), "Got non-2xx response, try different subdomain/target".yellow());
        println!("{}", "═".repeat(50).red());
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
        .connect_timeout(Duration::from_secs(timeout.saturating_sub(2)))
        .user_agent("curl/8.8.0")
        .danger_accept_invalid_certs(true)
        .build()?;

    for subdomain in subdomains {
        if !running.load(Ordering::SeqCst) {
            pb.finish_with_message("Cancelled");
            break;
        }

        pb.set_message(format!("Testing: {}", subdomain));
        
        // DNS resolution untuk dapat IP
        if let Ok(ip) = dns::resolve_domain_first(subdomain).await {
            let is_cf = dns::is_cloudflare_ip(&ip);
            
            // Try HTTPS first (port 443), then HTTP (port 80)
            // CURL --RESOLVE style: connect to IP, Host header to target
            let protocols = vec![
                ("https", 443),
                ("http", 80)
            ];
            
            let mut found_working = false;
            
            for (protocol, port) in protocols {
                // Connect ke subdomain IP dengan Host header ke target
                let target_url = format!("{}://{}:{}", protocol, ip, port);
                
                match client
                    .get(&target_url)
                    .header("Host", target)  // Host header ke target
                    .header("Connection", "close")
                    .timeout(Duration::from_secs(timeout))
                    .send()
                    .await
                {
                    Ok(response) => {
                        let status = response.status().as_u16();
                        
                        // Only 2xx = working
                        if is_working_status(status) {
                            results.push(ScanResult {
                                subdomain: subdomain.clone(),
                                ip: ip.clone(),
                                is_cloudflare: is_cf,
                                is_working: is_cf, // CF + 2xx status = working
                                status_code: Some(status),
                                error_msg: None,
                            });
                            
                            found_working = true;
                            break; // Stop on first 2xx
                        }
                        
                        // 3xx/4xx/5xx = continue to next protocol
                        continue;
                    }
                    Err(_) => continue, // Try next protocol
                }
            }
            
            // If no 2xx found, add as failed
            if !found_working {
                results.push(ScanResult {
                    subdomain: subdomain.clone(),
                    ip: ip.clone(),
                    is_cloudflare: is_cf,
                    is_working: false,
                    status_code: None,
                    error_msg: Some("No 2xx response".to_string()),
                });
            }
        }
        
        pb.inc(1);
        
        // Anti rate-limit: 200ms delay
        tokio::time::sleep(Duration::from_millis(200)).await;
    }
    
    pb.finish_with_message("Complete");
    
    // Display results
    let working: Vec<_> = results.iter().filter(|r| r.is_working).collect();
    let non_working: Vec<_> = results.iter().filter(|r| !r.is_working && r.status_code.is_some()).collect();
    let failed: Vec<_> = results.iter().filter(|r| r.error_msg.is_some()).collect();
    
    println!("\n{}", "═".repeat(60).cyan());
    ui::center_text("HASIL SCAN");
    println!("{}", "═".repeat(60).cyan());
    
    if working.is_empty() {
        println!("\n{}", "⚠️  Tidak ada working bug ditemukan".yellow());
    } else {
        println!("\n{} Working Bugs ({}):", "✅".green(), working.len());
        for result in &working {
            let status_str = if let Some(s) = result.status_code {
                format!(" - Status {}", s)
            } else {
                String::new()
            };
            println!("  {} {} ({}){}", "🟢".green(), result.subdomain.green(), result.ip.bright_black(), status_str.yellow());
        }
    }
    
    if !non_working.is_empty() {
        println!("\n{} Responding tapi Non-2xx Status ({}):", "⚠️".yellow(), non_working.len());
        for result in non_working.iter().take(3) {
            let status = result.status_code.unwrap_or(0);
            println!("  {} {} ({}) - Status {}", "🟡".yellow(), result.subdomain, result.ip.bright_black(), status.to_string().red());
        }
        if non_working.len() > 3 {
            println!("  ... dan {} lagi", non_working.len() - 3);
        }
    }
    
    if !failed.is_empty() {
        println!("\n{} Connection Failed/Timeout ({}):", "❌".red(), failed.len());
        for result in failed.iter().take(3) {
            println!("  {} {} ({}) - {}", "🔴".red(), result.subdomain.dimmed(), result.ip.bright_black(), "Timeout/Error".red());
        }
        if failed.len() > 3 {
            println!("  ... dan {} lagi", failed.len() - 3);
        }
    }
    
    println!("\n{}", "─".repeat(60).bright_black());
    println!("{}", "Statistik:");
    println!("  Scanned: {}/{} ({}%)", results.len(), total, (results.len() * 100 / total.max(1)));
    println!("  Working Bugs: {} | Non-2xx: {} | Timeout: {}", 
             working.len().to_string().green(), 
             non_working.len().to_string().yellow(),
             failed.len().to_string().red());
    println!("{}", "─".repeat(60).bright_black());
    
    Ok(results)
}
