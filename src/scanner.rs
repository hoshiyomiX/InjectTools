use colored::Colorize;
use indicatif::{ProgressBar, ProgressStyle};
use reqwest::Client;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::time::Duration;

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

pub async fn test_target(target: &str, timeout: u64) -> anyhow::Result<()> {
    println!("\n{}", "Testing target host...".cyan());
    println!("{}", "━".repeat(50).bright_black());
    
    // DNS check first
    println!("\n{} Checking DNS resolution...", "🔍".cyan());
    match dns::resolve_domain_first(target).await {
        Ok(ip) => {
            println!("{} {} → {}", "✓".green(), target.green(), ip.bright_black());
            
            // Check if Cloudflare IP
            if dns::is_cloudflare_ip(&ip) {
                println!("{} Cloudflare IP detected", "☁️".cyan());
            }
        }
        Err(e) => {
            println!("{} DNS resolution failed: {}", "✗".red(), e.to_string().red());
            println!("\n{}", "Possible causes:".yellow());
            println!("  - Domain tidak exist atau typo");
            println!("  - DNS server bermasalah (coba: pkg install dnsutils)");
            println!("  - Tidak ada koneksi internet");
            return Err(e);
        }
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
                println!("{}", "✅ TARGET HOST IS REACHABLE".green().bold());
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
    
    // All protocols failed - detailed error
    if let Some(e) = last_error {
        println!("\n{}", "═".repeat(50).red());
        println!("{}", "❌ CONNECTION FAILED".red().bold());
        println!("{}", "═".repeat(50).red());
        
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
        
        return Err(anyhow::anyhow!("Target host unreachable"));
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