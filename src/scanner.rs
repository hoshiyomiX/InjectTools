use colored::Colorize;
use indicatif::{ProgressBar, ProgressStyle};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::time::Duration;
use std::net::{TcpStream, ToSocketAddrs};
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
fn ping_test(host: &str, timeout_secs: u64) -> Option<u128> {
    let output = Command::new("ping")
        .arg("-c")
        .arg("1")
        .arg("-W")
        .arg(timeout_secs.to_string())
        .arg(host)
        .output();
    
    if let Ok(result) = output {
        if result.status.success() {
            if let Ok(stdout) = String::from_utf8(result.stdout) {
                if let Some(time_start) = stdout.find("time=") {
                    let time_str = &stdout[time_start + 5..];
                    if let Some(space_pos) = time_str.find(" ") {
                        let time_value = &time_str[..space_pos];
                        if let Ok(ms) = time_value.parse::<f64>() {
                            return Some(ms as u128);
                        }
                    }
                }
                return Some(0);
            }
        }
    }
    
    None
}

// Test SSL/TLS connection using openssl s_client
// Returns true if SSL handshake successful
fn test_ssl_connection(ip: &str, port: u16, servername: &str, timeout_secs: u64) -> bool {
    let connect_addr = format!("{}:{}", ip, port);
    
    // openssl s_client -connect <ip>:443 -servername <hostname> -brief < /dev/null
    let output = Command::new("timeout")
        .arg(timeout_secs.to_string())
        .arg("openssl")
        .arg("s_client")
        .arg("-connect")
        .arg(&connect_addr)
        .arg("-servername")
        .arg(servername)
        .arg("-brief")
        .stdin(std::process::Stdio::null())
        .output();
    
    if let Ok(result) = output {
        if result.status.success() {
            // Parse output untuk verify SSL handshake
            if let Ok(stdout) = String::from_utf8(result.stdout) {
                // Check for successful handshake indicators
                if stdout.contains("CONNECTION ESTABLISHED") 
                    || stdout.contains("Verification: OK")
                    || stdout.contains("Cipher:")
                    || result.status.code() == Some(0) {
                    return true;
                }
            }
            
            // stderr juga bisa contain success indicators
            if let Ok(stderr) = String::from_utf8(result.stderr) {
                if stderr.contains("Verify return code: 0")
                    || stderr.contains("SSL handshake has read")
                    || !stderr.contains("error") {
                    return true;
                }
            }
            
            // Exit code 0 = success
            return true;
        }
    }
    
    false
}

pub async fn test_target(target: &str, timeout: u64) -> anyhow::Result<()> {
    println!("\n{}", "Testing target host...".cyan());
    println!("{}", "━".repeat(50).bright_black());
    
    // Step 1: DNS Resolution Test
    let _resolved_ip = match dns::resolve_domain_first(target).await {
        Ok(ip) => ip,
        Err(_) => {
            println!("\n{}", "❌ TARGET OFFLINE".red().bold());
            println!("{}", "Reason: DNS resolution failed".bright_black());
            return Ok(());
        }
    };
    
    // Step 2: Ping Test
    if let Some(_ping_time) = ping_test(target, 3) {
        println!("\n{}", "✅ TARGET ONLINE".green().bold());
        return Ok(());
    }
    
    // Step 3: SSL/TLS Test (HTTPS port 443)
    if test_ssl_connection(target, 443, target, timeout) {
        println!("\n{}", "✅ TARGET ONLINE".green().bold());
        return Ok(());
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
            println!("\n{}", "═".repeat(50).red());
            println!("{}", "❌ DNS RESOLUTION FAILED".red().bold());
            println!("\n{} {}", "Subdomain:".bright_black(), subdomain.red());
            println!("{} {}", "Error:".bright_black(), e.to_string().red());
            println!("{}", "═".repeat(50).red());
            return Ok(());
        }
    };
    
    print!("\n{} Testing SSL/TLS handshake...", "🔌".cyan());
    
    // Try HTTPS with openssl s_client (port 443)
    // Connect ke subdomain IP, tapi SNI servername ke target
    if test_ssl_connection(&ip, 443, target, timeout) {
        print!("\r\x1B[K");
        
        println!("\n{}", "═".repeat(50));
        println!("{}", "✅ WORKING BUG INJECT!".green().bold());
        println!("\n{} {}", "Subdomain:".bright_black(), subdomain.green());
        println!("{} {}", "IP:".bright_black(), ip.green());
        println!("{} {} (port 443)", "Protocol:".bright_black(), "HTTPS".green());
        
        if is_cf {
            println!("{} {}", "Provider:".bright_black(), "Cloudflare".cyan());
        } else {
            println!("{} {}", "Provider:".bright_black(), "Non-Cloudflare".yellow());
        }
        
        println!("\n{}", "Connection Details:".bright_black());
        println!("  {} openssl s_client -connect {}:443", "→".bright_black(), ip.cyan());
        println!("  {} SNI servername: {}", "→".bright_black(), target.cyan());
        println!("  {} SSL handshake: {}", "→".bright_black(), "SUCCESS".green());
        
        println!("{}", "═".repeat(50));
        return Ok(());
    }
    
    // SSL handshake failed = NOT WORKING
    print!("\r\x1B[K");
    
    println!("\n{}", "═".repeat(50).red());
    println!("{}", "❌ BUG INJECT NOT WORKING".red().bold());
    println!("\n{} {}", "Subdomain:".bright_black(), subdomain.red());
    println!("{} {}", "IP:".bright_black(), ip.red());
    
    if is_cf {
        println!("{} {}", "Provider:".bright_black(), "Cloudflare".cyan());
    } else {
        println!("{} {}", "Provider:".bright_black(), "Non-Cloudflare".yellow());
    }
    
    println!("\n{} {}", "Reason:".bright_black(), "SSL handshake failed".red());
    println!("{} Subdomain tidak bisa inject ke target", "Note:".bright_black());
    println!("{}", "═".repeat(50).red());
    
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

    for subdomain in subdomains {
        if !running.load(Ordering::SeqCst) {
            pb.finish_with_message("Cancelled");
            break;
        }

        pb.set_message(format!("Testing: {}", subdomain));
        
        // DNS resolution untuk dapat IP
        if let Ok(ip) = dns::resolve_domain_first(subdomain).await {
            let is_cf = dns::is_cloudflare_ip(&ip);
            
            // Try HTTPS dengan openssl s_client (port 443)
            // Connect ke subdomain IP, SNI servername ke target
            // SSL success = WORKING, SSL failed = NOT WORKING (simple)
            if test_ssl_connection(&ip, 443, target, timeout) {
                results.push(ScanResult {
                    subdomain: subdomain.clone(),
                    ip: ip.clone(),
                    is_cloudflare: is_cf,
                    is_working: is_cf, // CF + SSL success = working
                    status_code: Some(200), // Dummy status for SSL success
                    error_msg: None,
                });
            } else {
                // SSL handshake failed = NOT WORKING
                results.push(ScanResult {
                    subdomain: subdomain.clone(),
                    ip: ip.clone(),
                    is_cloudflare: is_cf,
                    is_working: false,
                    status_code: None,
                    error_msg: Some("SSL handshake failed".to_string()),
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
    let failed: Vec<_> = results.iter().filter(|r| !r.is_working).collect();
    
    println!("\n{}", "═".repeat(60).cyan());
    ui::center_text("HASIL SCAN");
    println!("{}", "═".repeat(60).cyan());
    
    if working.is_empty() {
        println!("\n{}", "⚠️  Tidak ada working bug ditemukan".yellow());
    } else {
        println!("\n{} Working Bugs (SSL Handshake Success):", "✅".green());
        for result in &working {
            println!("  {} {} ({})", "🟢".green(), result.subdomain.green(), result.ip.bright_black());
        }
    }
    
    if !failed.is_empty() {
        println!("\n{} Not Working (SSL Handshake Failed):", "❌".red());
        for result in failed.iter().take(5) {
            println!("  {} {} ({})", "🔴".red(), result.subdomain.dimmed(), result.ip.bright_black());
        }
        if failed.len() > 5 {
            println!("  ... dan {} lagi", failed.len() - 5);
        }
    }
    
    println!("\n{}", "─".repeat(60).bright_black());
    println!("{}", "Statistik:");
    println!("  Scanned: {}/{} ({}%)", results.len(), total, (results.len() * 100 / total.max(1)));
    println!("  Working (SSL Success): {} | Failed (SSL Failed): {}", 
             working.len().to_string().green(), 
             failed.len().to_string().red());
    println!("{}", "─".repeat(60).bright_black());
    
    Ok(results)
}
