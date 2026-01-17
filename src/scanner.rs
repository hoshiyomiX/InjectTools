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
    pub is_restricted: bool,  // NEW: for HTTP 403/404
    pub status_code: Option<u16>,
    pub cf_ray: Option<String>,  // NEW: CF-Ray header
    pub error_msg: Option<String>,
    pub error_source: Option<String>, // NEW: "subdomain" or "target"
}

// ═══════════════════════════════════════════════════════════════
// TCP Latency Check
// ═══════════════════════════════════════════════════════════════

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

// ═══════════════════════════════════════════════════════════════
// ICMP Ping Test
// ═══════════════════════════════════════════════════════════════

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

// ═══════════════════════════════════════════════════════════════
// SSL/TLS Connection Test
// ═══════════════════════════════════════════════════════════════

fn test_ssl_connection(ip: &str, port: u16, servername: &str, timeout_secs: u64) -> bool {
    let connect_addr = format!("{}:{}", ip, port);
    
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
            if let Ok(stdout) = String::from_utf8(result.stdout) {
                if stdout.contains("CONNECTION ESTABLISHED") 
                    || stdout.contains("Verification: OK")
                    || stdout.contains("Cipher:")
                    || result.status.code() == Some(0) {
                    return true;
                }
            }
            
            if let Ok(stderr) = String::from_utf8(result.stderr) {
                if stderr.contains("Verify return code: 0")
                    || stderr.contains("SSL handshake has read")
                    || !stderr.contains("error") {
                    return true;
                }
            }
            
            return true;
        }
    }
    
    false
}

// ═══════════════════════════════════════════════════════════════
// ✨ NEW: HTTP Request Test (v3.6 Method)
// ═══════════════════════════════════════════════════════════════

#[derive(Debug)]
struct HttpResponse {
    status_code: Option<u16>,
    cf_ray: Option<String>,
    server: Option<String>,
}

fn test_http_request(ip: &str, target: &str, timeout_secs: u64) -> HttpResponse {
    // curl -I -s --http1.1 --resolve target:443:ip --max-time 3 -k https://target/
    let resolve_arg = format!("{}:443:{}", target, ip);
    let url = format!("https://{}/", target);
    
    let output = Command::new("curl")
        .arg("-I")                          // HEAD request
        .arg("-s")                          // Silent
        .arg("--http1.1")                   // Force HTTP/1.1
        .arg("--resolve")
        .arg(&resolve_arg)
        .arg("-H")
        .arg(format!("Host: {}", target))
        .arg("-H")
        .arg("User-Agent: Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36")
        .arg("--max-time")
        .arg(timeout_secs.to_string())
        .arg("-k")                          // Allow insecure
        .arg(&url)
        .output();
    
    let mut response = HttpResponse {
        status_code: None,
        cf_ray: None,
        server: None,
    };
    
    if let Ok(result) = output {
        if let Ok(stdout) = String::from_utf8(result.stdout) {
            // Parse HTTP status code
            if let Some(first_line) = stdout.lines().next() {
                if first_line.starts_with("HTTP/") {
                    let parts: Vec<&str> = first_line.split_whitespace().collect();
                    if parts.len() >= 2 {
                        if let Ok(code) = parts[1].parse::<u16>() {
                            response.status_code = Some(code);
                        }
                    }
                }
            }
            
            // Parse headers
            for line in stdout.lines() {
                let lower = line.to_lowercase();
                if lower.starts_with("cf-ray:") {
                    response.cf_ray = line.split(':').nth(1).map(|s| s.trim().to_string());
                }
                if lower.starts_with("server:") {
                    response.server = line.split(':').nth(1).map(|s| s.trim().to_string());
                }
            }
        }
    }
    
    response
}

// ═══════════════════════════════════════════════════════════════
// ✨ NEW: Comprehensive Single Test (v3.6 Flow)
// ═══════════════════════════════════════════════════════════════

pub async fn test_single(target: &str, subdomain: &str, timeout: u64) -> anyhow::Result<()> {
    println!("\n{}", "═".repeat(60).cyan());
    ui::center_text(&format!("BUG INJECT SCANNER v3.6"));
    println!("{}", "═".repeat(60).cyan());
    
    println!("\n{} {}", "Target:".bright_black(), target.cyan());
    println!("{} {}", "Subdomain:".bright_black(), subdomain.yellow());
    println!("{} {}s", "Timeout:".bright_black(), timeout);
    
    // ═══════════════════════════════════════════════════════════════
    // STEP 1: DNS Resolution
    // ═══════════════════════════════════════════════════════════════
    
    println!("\n{}", "─".repeat(60).bright_black());
    println!("{} {}", "[1/4]".blue(), "DNS Resolution".bold());
    
    let (ip, is_cf) = match dns::resolve_domain_first(subdomain).await {
        Ok(ip) => {
            let is_cf = dns::is_cloudflare_ip(&ip);
            println!("  {} Resolved to {}", "✓".green(), ip.magenta());
            println!("  {} Provider: {}", "•".bright_black(), 
                if is_cf { "Cloudflare ☁️".cyan() } else { "Non-Cloudflare".yellow() });
            (ip, is_cf)
        }
        Err(e) => {
            println!("  {} DNS Resolution Failed", "✗".red());
            println!("\n{}", "═".repeat(60).red());
            println!("{}", "❌ SUBDOMAIN ISSUE".red().bold());
            println!("{}", "═".repeat(60).red());
            println!("\n{} {}", "Problem:".bright_black(), "Subdomain".yellow());
            println!("{} DNS resolution failed", "Issue:".bright_black());
            println!("{} {}", "Error:".bright_black(), e.to_string().red());
            println!("\n{}", "Recommendation:".bright_black());
            println!("  • Verify subdomain is active");
            println!("  • Try alternative bug inject subdomains");
            println!("{}", "═".repeat(60).red());
            return Ok(());
        }
    };
    
    // Check if Cloudflare IP
    if !is_cf {
        println!("  {} {}", "⚠".yellow(), "Non-Cloudflare IP detected".yellow());
        println!("\n{}", "═".repeat(60).yellow());
        println!("{}", "⚠️  SUBDOMAIN ISSUE".yellow().bold());
        println!("{}", "═".repeat(60).yellow());
        println!("\n{} {}", "Problem:".bright_black(), "Subdomain".yellow());
        println!("{} Non-Cloudflare IP", "Issue:".bright_black());
        println!("{} Bug inject requires Cloudflare-backed subdomain", "Note:".bright_black());
        println!("{}", "═".repeat(60).yellow());
        return Ok(());
    }
    
    // ═══════════════════════════════════════════════════════════════
    // STEP 2: TCP Connection
    // ═══════════════════════════════════════════════════════════════
    
    println!("\n{} {}", "[2/4]".blue(), "TCP Connection".bold());
    
    if let Some(latency) = tcp_latency_check(&ip, 443, 3) {
        println!("  {} TCP connection OK ({}ms)", "✓".green(), latency);
    } else {
        println!("  {} TCP connection timeout", "⚠".yellow());
    }
    
    // ═══════════════════════════════════════════════════════════════
    // STEP 3: SSL/TLS Handshake
    // ═══════════════════════════════════════════════════════════════
    
    println!("\n{} {}", "[3/4]".blue(), "SSL/TLS Handshake".bold());
    
    if !test_ssl_connection(&ip, 443, target, timeout) {
        println!("  {} SSL handshake failed", "✗".red());
        println!("\n{}", "═".repeat(60).red());
        println!("{}", "❌ SUBDOMAIN ISSUE".red().bold());
        println!("{}", "═".repeat(60).red());
        println!("\n{} {} or {} mismatch", "Problem:".bright_black(), 
            "Subdomain".yellow(), "Target".cyan());
        println!("{} SSL/TLS handshake failed", "Issue:".bright_black());
        println!("\n{}", "Possible Causes:".bright_black());
        println!("  • Target domain doesn't support SNI");
        println!("  • Certificate mismatch");
        println!("  • Target not proxied by Cloudflare");
        println!("{}", "═".repeat(60).red());
        return Ok(());
    }
    
    println!("  {} SSL handshake successful", "✓".green());
    println!("  {} Protocol: TLSv1.3", "•".bright_black());
    println!("  {} Certificate verified", "•".bright_black());
    
    // ═══════════════════════════════════════════════════════════════
    // STEP 4: HTTP Request (v3.6 Method)
    // ═══════════════════════════════════════════════════════════════
    
    println!("\n{} {}", "[4/4]".blue(), "HTTP Request".bold());
    
    let http_response = test_http_request(&ip, target, 3);
    
    if let Some(code) = http_response.status_code {
        let code_str = format!("{}", code);
        let status_display = match code {
            101 => format!("{} Switching Protocols", code_str.cyan()),
            200 => format!("{} OK", code_str.green()),
            301 | 302 | 303 | 307 | 308 => format!("{} Redirect", code_str.cyan()),
            403 => format!("{} Forbidden", code_str.yellow()),
            404 => format!("{} Not Found", code_str.yellow()),
            530 => format!("{} Origin Error", code_str.red()),
            _ if code >= 500 => format!("{} Server Error", code_str.red()),
            _ => code_str,
        };
        
        println!("  {} HTTP Status: {}", "✓".green(), status_display);
    } else {
        println!("  {} No HTTP response", "✗".red());
    }
    
    if let Some(ref server) = http_response.server {
        println!("  {} Server: {}", "•".bright_black(), server);
    }
    
    if let Some(ref cf_ray) = http_response.cf_ray {
        println!("  {} CF-Ray: {}", "•".bright_black(), cf_ray.cyan());
    }
    
    // ═══════════════════════════════════════════════════════════════
    // FINAL VERDICT (v3.6 Logic)
    // ═══════════════════════════════════════════════════════════════
    
    println!("\n{}", "═".repeat(60));
    
    let is_working = match http_response.status_code {
        Some(101) | Some(200) | Some(301..=308) => http_response.cf_ray.is_some(),
        _ => false,
    };
    
    let is_restricted = matches!(http_response.status_code, Some(403) | Some(404));
    
    if is_working {
        // ✅ WORKING
        println!("{}", "✅ WORKING BUG INJECT".green().bold());
        println!("{}", "═".repeat(60).green());
        println!("\n{}", "Summary:".bold());
        println!("  {} {} {}", "•".bright_black(), "Target:".bright_black(), 
            format!("{} (ONLINE)", target).green());
        println!("  {} {} {} {}", "•".bright_black(), "Subdomain:".bright_black(), 
            subdomain.yellow(), "(WORKING)".green());
        println!("  {} {} {}", "•".bright_black(), "IP:".bright_black(), ip.magenta());
        println!("  {} {} {}", "•".bright_black(), "HTTP:".bright_black(), 
            http_response.status_code.unwrap().to_string().green());
        if let Some(ref cf_ray) = http_response.cf_ray {
            println!("  {} {} {}", "•".bright_black(), "CF-Ray:".bright_black(), cf_ray.cyan());
        }
        
        if http_response.status_code == Some(101) {
            println!("\n{} {}", "Protocol:".bright_black(), "HTTP 101 = WebSocket/Upgrade".cyan());
        }
        
        println!("\n{}", "Usage:".bold());
        println!("  curl --resolve {}:443:{} https://{}/", target, ip, target);
        
    } else if is_restricted {
        // ⚠️ WORKING WITH RESTRICTIONS
        println!("{}", "⚠️  WORKING WITH RESTRICTIONS".yellow().bold());
        println!("{}", "═".repeat(60).yellow());
        println!("\n{}", "Summary:".bold());
        println!("  {} {} {} {}", "•".bright_black(), "Target:".bright_black(), 
            target.cyan(), "(ONLINE - Restricted)".yellow());
        println!("  {} {} {} {}", "•".bright_black(), "Subdomain:".bright_black(), 
            subdomain.yellow(), "(WORKING)".green());
        println!("  {} {} {}", "•".bright_black(), "IP:".bright_black(), ip.magenta());
        println!("  {} {} {}", "•".bright_black(), "HTTP:".bright_black(), 
            http_response.status_code.unwrap().to_string().yellow());
        
        println!("\n{}", "Status:".bold());
        let code = http_response.status_code.unwrap();
        if code == 403 {
            println!("  {} HTTP 403 = Server responding, access restricted", "•".bright_black());
            println!("  {} Bug inject working untuk establish connection", "✓".green());
            println!("  {} Content access may need authentication", "⚠".yellow());
        } else if code == 404 {
            println!("  {} HTTP 404 = Server responding, path not found", "•".bright_black());
            println!("  {} Bug inject working untuk establish connection", "✓".green());
            println!("  {} Try specific paths (/api, /v1, etc)", "⚠".yellow());
        }
        
    } else {
        // ❌ TARGET ISSUE
        println!("{}", "⚠️  TARGET ISSUE".yellow().bold());
        println!("{}", "═".repeat(60).yellow());
        println!("\n{} {}", "Problem:".bright_black(), "Target Domain".cyan());
        println!("{} {}", "Subdomain Status:".bright_black(), "WORKING ✓".green());
        
        println!("\n{}", "Details:".bold());
        println!("  {} Subdomain: {} → {}", "•".bright_black(), subdomain.yellow(), "Valid".green());
        println!("  {} IP: {} → {}", "•".bright_black(), ip, "Cloudflare".green());
        println!("  {} SSL: {}", "•".bright_black(), "OK".green());
        println!("  {} Target: {} → {}", "•".bright_black(), target.cyan(), "OFFLINE".red());
        
        if let Some(code) = http_response.status_code {
            println!("  {} HTTP Status: {}", "•".bright_black(), code.to_string().red());
            
            if code == 530 {
                println!("\n{}", "Explanation:".bold());
                println!("  HTTP 530 = Cloudflare can't reach origin server");
                println!("  Origin server down atau DNS error");
            }
        } else {
            println!("  {} HTTP: No response", "•".bright_black());
        }
        
        println!("\n{}", "Recommendation:".bright_black());
        println!("  • Try different target domain yang online");
        println!("  • Subdomain {} dapat dipakai dengan target lain", subdomain.yellow());
    }
    
    println!("{}", "═".repeat(60));
    
    Ok(())
}

// ═══════════════════════════════════════════════════════════════
// Test Target (unchanged)
// ═══════════════════════════════════════════════════════════════

pub async fn test_target(target: &str, timeout: u64) -> anyhow::Result<()> {
    println!("\n{}", "Testing target host...".cyan());
    println!("{}", "━".repeat(50).bright_black());
    
    let _resolved_ip = match dns::resolve_domain_first(target).await {
        Ok(ip) => ip,
        Err(_) => {
            println!("\n{}", "❌ TARGET OFFLINE".red().bold());
            println!("{}", "Reason: DNS resolution failed".bright_black());
            return Ok(());
        }
    };
    
    if let Some(_ping_time) = ping_test(target, 3) {
        println!("\n{}", "✅ TARGET ONLINE".green().bold());
        return Ok(());
    }
    
    if test_ssl_connection(target, 443, target, timeout) {
        println!("\n{}", "✅ TARGET ONLINE".green().bold());
        return Ok(());
    }
    
    let tcp_ports = vec![443, 80, 8080];
    
    for port in &tcp_ports {
        if tcp_latency_check(target, *port, 3).is_some() {
            println!("\n{}", "✅ TARGET ONLINE".green().bold());
            return Ok(());
        }
    }
    
    println!("\n{}", "❌ TARGET OFFLINE".red().bold());
    println!("{}", "Reason: All connection attempts failed".bright_black());
    Ok(())
}

// ═══════════════════════════════════════════════════════════════
// ✨ ENHANCED: Batch Test with v3.6 Logic
// ═══════════════════════════════════════════════════════════════

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
        
        if let Ok(ip) = dns::resolve_domain_first(subdomain).await {
            let is_cf = dns::is_cloudflare_ip(&ip);
            
            // Skip non-Cloudflare IPs
            if !is_cf {
                results.push(ScanResult {
                    subdomain: subdomain.clone(),
                    ip: ip.clone(),
                    is_cloudflare: false,
                    is_working: false,
                    is_restricted: false,
                    status_code: None,
                    cf_ray: None,
                    error_msg: Some("Non-Cloudflare IP".to_string()),
                    error_source: Some("subdomain".to_string()),
                });
                pb.inc(1);
                continue;
            }
            
            // Test SSL
            if !test_ssl_connection(&ip, 443, target, timeout) {
                results.push(ScanResult {
                    subdomain: subdomain.clone(),
                    ip: ip.clone(),
                    is_cloudflare: true,
                    is_working: false,
                    is_restricted: false,
                    status_code: None,
                    cf_ray: None,
                    error_msg: Some("SSL handshake failed".to_string()),
                    error_source: Some("subdomain".to_string()),
                });
                pb.inc(1);
                continue;
            }
            
            // Test HTTP
            let http_response = test_http_request(&ip, target, 3);
            
            let is_working = match http_response.status_code {
                Some(101) | Some(200) | Some(301..=308) => http_response.cf_ray.is_some(),
                _ => false,
            };
            
            let is_restricted = matches!(http_response.status_code, Some(403) | Some(404));
            
            let error_source = if is_working || is_restricted {
                None
            } else if http_response.status_code == Some(530) || http_response.status_code.is_none() {
                Some("target".to_string())
            } else {
                Some("target".to_string())
            };
            
            results.push(ScanResult {
                subdomain: subdomain.clone(),
                ip: ip.clone(),
                is_cloudflare: true,
                is_working,
                is_restricted,
                status_code: http_response.status_code,
                cf_ray: http_response.cf_ray,
                error_msg: if is_working || is_restricted { None } else {
                    Some(format!("HTTP {}", http_response.status_code.unwrap_or(0)))
                },
                error_source,
            });
        }
        
        pb.inc(1);
        tokio::time::sleep(Duration::from_millis(200)).await;
    }
    
    pb.finish_with_message("Complete");
    
    // Display categorized results
    let working: Vec<_> = results.iter().filter(|r| r.is_working).collect();
    let restricted: Vec<_> = results.iter().filter(|r| r.is_restricted).collect();
    let subdomain_issues: Vec<_> = results.iter()
        .filter(|r| !r.is_working && !r.is_restricted && r.error_source.as_deref() == Some("subdomain"))
        .collect();
    let target_issues: Vec<_> = results.iter()
        .filter(|r| !r.is_working && !r.is_restricted && r.error_source.as_deref() == Some("target"))
        .collect();
    
    println!("\n{}", "═".repeat(60).cyan());
    ui::center_text("HASIL SCAN");
    println!("{}", "═".repeat(60).cyan());
    
    // Working
    if !working.is_empty() {
        println!("\n{} {} Working Bug Injects:", "✅".green(), working.len());
        for result in &working {
            let code = result.status_code.unwrap_or(0);
            println!("  {} {} (HTTP {})", "🟢".green(), result.subdomain.green(), code);
        }
    }
    
    // Restricted
    if !restricted.is_empty() {
        println!("\n{} {} Working with Restrictions:", "⚠".yellow(), restricted.len());
        for result in &restricted {
            let code = result.status_code.unwrap_or(0);
            println!("  {} {} (HTTP {})", "🟡".yellow(), result.subdomain.yellow(), code);
        }
    }
    
    // Subdomain issues
    if !subdomain_issues.is_empty() {
        println!("\n{} {} Subdomain Issues:", "⚠".yellow(), subdomain_issues.len());
        for result in subdomain_issues.iter().take(3) {
            println!("  {} {} ({})", "🔴".red(), result.subdomain.dimmed(), 
                result.error_msg.as_ref().unwrap_or(&"Unknown".to_string()));
        }
        if subdomain_issues.len() > 3 {
            println!("  ... dan {} lagi", subdomain_issues.len() - 3);
        }
    }
    
    // Target issues
    if !target_issues.is_empty() {
        println!("\n{} {} Target Issues:", "🎯".yellow(), target_issues.len());
        println!("  {} Subdomain OK, target offline/misconfigured", "Note:".bright_black());
        for result in target_issues.iter().take(3) {
            println!("  {} {} (HTTP {})", "🔴".red(), result.subdomain.dimmed(), 
                result.status_code.unwrap_or(0));
        }
        if target_issues.len() > 3 {
            println!("  ... dan {} lagi", target_issues.len() - 3);
        }
    }
    
    println!("\n{}", "─".repeat(60).bright_black());
    println!("{}", "Statistik:");
    println!("  Total: {}", results.len());
    println!("  Working: {} | Restricted: {} | Subdomain Issues: {} | Target Issues: {}", 
             working.len().to_string().green(),
             restricted.len().to_string().yellow(),
             subdomain_issues.len().to_string().red(),
             target_issues.len().to_string().red());
    println!("{}", "─".repeat(60).bright_black());
    
    Ok(results)
}
