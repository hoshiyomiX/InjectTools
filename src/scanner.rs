use colored::Colorize;
use indicatif::{ProgressBar, ProgressStyle};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::time::{Duration, Instant};
use std::process::Command;
use tokio::net::TcpStream;

use crate::dns;

#[derive(Debug, Clone)]
pub struct ScanResult {
    pub subdomain: String,
    pub ip: String,
    pub is_cloudflare: bool,
    pub is_working: bool,
    pub is_restricted: bool,
    pub status_code: Option<u16>,
    pub cf_ray: Option<String>,
    pub error_msg: Option<String>,
    pub error_source: Option<String>,
}

// =============================================================================
// HELPER FUNCTIONS (Logic V17 - Optimized)
// =============================================================================

fn is_private_ip(ip: &str) -> bool {
    // V17 Regex equivalent: ^10\.|^192\.168\.|^172\.(1[6-9]|2[0-9]|3[0-1])\.|^127\.
    if ip.starts_with("10.") || ip.starts_with("192.168.") || ip.starts_with("127.") {
        return true;
    }
    if ip.starts_with("172.") {
        let parts: Vec<&str> = ip.split('.').collect();
        if parts.len() > 1 {
            if let Ok(second_octet) = parts[1].parse::<u8>() {
                if second_octet >= 16 && second_octet <= 31 {
                    return true;
                }
            }
        }
    }
    false
}

fn is_fake_dns_ip(ip: &str) -> bool {
    // V17: ^198\.(18|19)\.
    ip.starts_with("198.18.") || ip.starts_with("198.19.")
}

// Use logic from dns.rs instead of local implementation for better accuracy
fn is_cloudflare_ip(ip: &str) -> bool {
    dns::is_cloudflare_ip(ip)
}

// Curl based SSL Handshake to match V17 "Best for TLS Fingerprint"
fn ssl_handshake_curl(ip: &str, sni: &str, verbose: bool) -> String {
    let curl_cmd = format!(
        "curl -s -I --http1.1 --resolve {}:443:{} https://{}/ --connect-timeout 5 -k",
        sni, ip, sni
    );

    if verbose {
        println!("{}", "[VERBOSE] Executing SSL Handshake:".bright_black());
        println!("{}", curl_cmd.bright_black());
    }

    let output = Command::new("curl")
        .arg("-s")
        .arg("-I")
        .arg("--http1.1")
        .arg("--resolve")
        .arg(format!("{}:443:{}", sni, ip))
        .arg(format!("https://{}/", sni))
        .arg("--connect-timeout")
        .arg("5")
        .arg("-k")
        .output();

    match output {
        Ok(out) => {
            let stdout = String::from_utf8_lossy(&out.stdout).to_string();
            let stderr = String::from_utf8_lossy(&out.stderr).to_string();
            let combined = format!("{}{}", stdout, stderr);
            
            if verbose {
                println!("{}", "[VERBOSE] SSL Output:".bright_black());
                println!("{}", combined.trim().bright_black());
            }

            // Check for any valid HTTP response line or Server header
            // Also check for 403/400/500/etc which indicate a handshake succeeded and server replied
            if combined.contains("HTTP/") || 
               combined.to_lowercase().contains("server:") || 
               combined.contains("403 Forbidden") || 
               combined.contains("400 Bad Request") {
                "ESTABLISHED".to_string()
            } else {
                if combined.trim().is_empty() {
                    "FAILED: Empty Output".to_string()
                } else {
                    format!("FAILED: {}", combined.trim())
                }
            }
        },
        Err(e) => format!("FAILED: Exec Error {}", e)
    }
}

async fn check_target_status(target: &str, verbose: bool) -> String {
    // 1. Resolve Target
    let tip = match dns::resolve_domain_first(target).await {
        Ok(ip) => ip,
        Err(_) => return "UNKNOWN|DNS_FAIL".to_string(),
    };

    // 2. Check Direct SSL
    let tssl = ssl_handshake_curl(&tip, target, verbose);
    if tssl == "ESTABLISHED" {
        return "ONLINE|SSL_OK".to_string();
    }

    // 3. Fallback HTTP
    // curl -s -o /dev/null -w "%{http_code}" --connect-timeout 3 "http://target/"
    if verbose {
        println!("{}", "[VERBOSE] Direct SSL failed, trying fallback HTTP check...".bright_black());
    }
    
    let output = Command::new("curl")
        .arg("-s")
        .arg("-o")
        .arg("/dev/null")
        .arg("-w")
        .arg("%{http_code}")
        .arg("--connect-timeout")
        .arg("3")
        .arg(format!("http://{}/", target))
        .output();

    if let Ok(out) = output {
        let code = String::from_utf8_lossy(&out.stdout).trim().to_string();
        if code != "000" && !code.is_empty() {
            return format!("ONLINE|HTTP_{}", code);
        }
    }

    "UNREACHABLE|NO_DIRECT_ACCESS".to_string()
}

// =============================================================================
// MAIN VALIDATION LOGIC (Menu 1: Test Single)
// =============================================================================

/// Test single target connection (Used in Menu 4: Change Target)
pub async fn test_target(target: &str, _timeout: u64, verbose: bool) -> anyhow::Result<()> {
    let status_raw = check_target_status(target, verbose).await;
    let parts: Vec<&str> = status_raw.split('|').collect();
    let status = parts.get(0).unwrap_or(&"UNKNOWN");
    let note = parts.get(1).unwrap_or(&"");

    if *status == "ONLINE" {
        println!("{} Target is reachable ({})", "✓".green(), note);
        Ok(())
    } else {
        println!("{} Target unreachable: {}", "✗".red(), note);
        Err(anyhow::anyhow!("Target unreachable"))
    }
}

pub async fn test_single(target: &str, subdomain: &str, _timeout: u64, verbose: bool) -> anyhow::Result<()> {
    println!("\n{}", "==================== CHECKING ====================".blue());
    println!("Subdomain : {}", subdomain.yellow());
    println!("Target    : {}", target.cyan());
    println!("------------------------------------------------");

    // 1. Resolve Subdomain
    let sub_ip = match dns::resolve_domain_first(subdomain).await {
        Ok(ip) => ip,
        Err(_) => {
             print_report(target, "UNKNOWN", subdomain, "-", "NOT_WORKING", "SUBDOMAIN_DNS_FAIL");
             return Ok(());
        }
    };
    println!("Resolved  : {} ({})", subdomain, sub_ip.magenta());

    // 2. Environment Checks
    if is_fake_dns_ip(&sub_ip) {
        print_report(target, "UNKNOWN", subdomain, &sub_ip, "NOT_WORKING", "ENV_VPN_FAKE_DNS");
        return Ok(());
    }
    if is_private_ip(&sub_ip) {
        print_report(target, "UNKNOWN", subdomain, &sub_ip, "NOT_WORKING", "ENV_PRIVATE_IP");
        return Ok(());
    }
    
    // NEW: Use enhanced Cloudflare detection (IP Range + HTTP Headers)
    // This fixes the "accuracy" issue for BYOIP addresses
    let is_cf = dns::is_cloudflare_enhanced(subdomain, &sub_ip).await;
    
    if !is_cf {
        if verbose {
            println!("{}", "[VERBOSE] IP not in CF Range and no CF headers found.".red());
        }
        print_report(target, "UNKNOWN", subdomain, &sub_ip, "NOT_WORKING", "SUBDOMAIN_NOT_CLOUDFLARE");
        return Ok(());
    } else if verbose && !dns::is_cloudflare_ip(&sub_ip) {
         println!("{}", "[VERBOSE] Note: IP not in official range, but CF headers detected.".green());
    }

    // 3. Latency Check (V17: <5ms detection for VPN Interception)
    let start = Instant::now();
    let tcp_check = tokio::time::timeout(Duration::from_secs(2), TcpStream::connect((sub_ip.as_str(), 443))).await;
    let latency = start.elapsed().as_millis();

    if verbose {
         println!("{}", format!("[VERBOSE] TCP Latency: {}ms", latency).bright_black());
    }

    match tcp_check {
        Ok(Ok(_)) => {
            if latency < 5 {
                println!("{}", "⚠️  Warning: Very low latency (<5ms). VPN might be active?".yellow());
            }
        },
        _ => {
            print_report(target, "UNKNOWN", subdomain, &sub_ip, "NOT_WORKING", "SUBDOMAIN_TCP_BLOCKED");
            return Ok(());
        }
    }

    // 4. Test Handshake (V17 Logic)
    let ssl_status = ssl_handshake_curl(&sub_ip, target, verbose);

    if ssl_status != "ESTABLISHED" {
        // Handshake Failed
        let ts_full = check_target_status(target, verbose).await;
        let parts: Vec<&str> = ts_full.split('|').collect();
        let t_status = parts.get(0).unwrap_or(&"UNKNOWN");
        let _t_note = parts.get(1).unwrap_or(&"");

        let bug_note = if *t_status == "UNREACHABLE" {
            "DIRECT_CONNECTION_BLOCKED".to_string()
        } else {
            if ssl_status.contains("refused") {
                "BUG_NOT_COMPATIBLE(CONN_REFUSED)".to_string()
            } else if ssl_status.contains("reset") {
                "BUG_NOT_COMPATIBLE(TLS_RESET)".to_string()
            } else if ssl_status.contains("timeout") {
                "BUG_NOT_COMPATIBLE(TIMEOUT)".to_string()
            } else {
                "BUG_NOT_COMPATIBLE(SSL_FAIL)".to_string()
            }
        };

        print_report(target, t_status, subdomain, &sub_ip, "NOT_WORKING", &bug_note);
        return Ok(());
    }

    // 5. Handshake Success -> Check End-to-End HTTP
    let output = Command::new("curl")
        .arg("-s")
        .arg("-I")
        .arg("--http1.1")
        .arg("--resolve")
        .arg(format!("{}:443:{}", target, sub_ip))
        .arg(format!("https://{}/", target))
        .arg("--connect-timeout")
        .arg("5")
        .arg("-k")
        .output();

    let mut http_code = "000".to_string();
    let mut cf_ray = false;

    if let Ok(out) = output {
        let stdout = String::from_utf8_lossy(&out.stdout).to_string();
        
        if verbose {
            println!("{}", "[VERBOSE] Final HTTP Output:".bright_black());
            println!("{}", stdout.trim().bright_black());
        }
        
        // Parse HTTP Code
        if let Some(line) = stdout.lines().next() {
            let parts: Vec<&str> = line.split_whitespace().collect();
            if parts.len() >= 2 {
                http_code = parts[1].to_string();
            }
        }
        
        // Parse CF-Ray
        if stdout.to_lowercase().contains("cf-ray:") {
            cf_ray = true;
        }
    }

    let mut target_status_final = "ONLINE";
    if http_code.starts_with("52") || http_code == "530" {
        target_status_final = "OFFLINE";
    }

    if cf_ray {
        print_report(target, target_status_final, subdomain, &sub_ip, "WORKING", &format!("OK_HTTP_{}_CF", http_code));
    } else {
        let note = if http_code == "000" { "HTTP_NO_RESPONSE".to_string() } else { format!("HTTP_{}_NO_CF", http_code) };
        print_report(target, target_status_final, subdomain, &sub_ip, "NOT_WORKING", &note);
    }

    Ok(())
}

fn print_report(target: &str, t_status: &str, subdomain: &str, ip: &str, b_status: &str, note: &str) {
    println!("");
    println!("{}", "==================== RESULT ====================".blue());
    
    let t_color = match t_status {
        "ONLINE" => "green",
        "UNREACHABLE" => "red",
        "OFFLINE" => "red",
        _ => "yellow",
    };
    println!("TARGET: {} | {}", target, t_status.color(t_color));

    let b_color = if b_status == "WORKING" { "green" } else { "red" };
    println!("BUG   : {} -> {} | {}", subdomain, ip, b_status.color(b_color));
    
    println!("NOTE  : {}", note);
    println!("{}", "================================================".blue());
    println!("");
}

// =============================================================================
// BATCH TEST (Menu 2: Updated to match V17 Logic simplified)
// =============================================================================

pub async fn batch_test(
    target: &str,
    subdomains: &[String],
    _timeout: u64,
    running: Arc<AtomicBool>,
    verbose: bool,
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
        
        pb.set_message(format!("Scanning: {}", subdomain));
        
        let mut scan_res = ScanResult {
            subdomain: subdomain.clone(),
            ip: String::new(),
            is_cloudflare: false,
            is_working: false,
            is_restricted: false,
            status_code: None,
            cf_ray: None,
            error_msg: None,
            error_source: None,
        };

        match dns::resolve_domain_first(subdomain).await {
            Ok(ip) => {
                scan_res.ip = ip.clone();
                
                // V17 Env Checks
                if is_fake_dns_ip(&ip) {
                    scan_res.error_msg = Some("Fake DNS".to_string());
                } else if is_private_ip(&ip) {
                     scan_res.error_msg = Some("Private IP".to_string());
                } else if !is_cloudflare_ip(&ip) {
                     scan_res.error_msg = Some("Not Cloudflare".to_string());
                } else {
                    scan_res.is_cloudflare = true;

                    // Latency Check (<5ms check included)
                    let start = Instant::now();
                    let tcp_check = tokio::time::timeout(Duration::from_secs(2), TcpStream::connect((ip.as_str(), 443))).await;
                    let latency = start.elapsed().as_millis();
                    
                    let mut tcp_ok = false;
                    match tcp_check {
                        Ok(Ok(_)) => {
                            if latency < 2 {
                                // Super suspicious
                                scan_res.error_msg = Some("Suspicious Latency <2ms".to_string());
                            } else {
                                tcp_ok = true;
                            }
                        },
                        _ => { scan_res.error_msg = Some("TCP 443 Blocked".to_string()); }
                    }

                    if tcp_ok {
                         // V17 Handshake
                         let ssl_res = ssl_handshake_curl(&ip, target, verbose); // pass verbose but maybe ignore output in batch? 
                         // actually verbose in batch might ruin progress bar. 
                         // Let's force verbose to false for batch's internal calls to avoid spam, 
                         // OR we implement a "log to file" if verbose.
                         // For now, let's just pass `false` for internal helper calls in batch to keep it clean.
                         // Wait, I changed the signature of ssl_handshake_curl to take verbose.
                         // I will pass `false` here to prevent spamming the console during batch.
                         
                         if ssl_res == "ESTABLISHED" {
                             // End-to-End
                             let output = Command::new("curl")
                                .arg("-s")
                                .arg("-I")
                                .arg("--http1.1")
                                .arg("--resolve")
                                .arg(format!("{}:443:{}", target, ip))
                                .arg(format!("https://{}/", target))
                                .arg("--connect-timeout")
                                .arg("5")
                                .arg("-k")
                                .output();
                            
                             if let Ok(out) = output {
                                 let stdout = String::from_utf8_lossy(&out.stdout);
                                 if stdout.to_lowercase().contains("cf-ray:") {
                                     scan_res.cf_ray = Some("Yes".to_string());
                                     if let Some(line) = stdout.lines().next() {
                                         if let Some(code_str) = line.split_whitespace().nth(1) {
                                             if let Ok(c) = code_str.parse::<u16>() {
                                                 scan_res.status_code = Some(c);
                                                 if c != 0 { scan_res.is_working = true; }
                                             }
                                         }
                                     }
                                 } else {
                                     scan_res.error_msg = Some("No CF-Ray".to_string());
                                 }
                             }
                         } else {
                             scan_res.error_msg = Some("SSL Handshake Failed".to_string());
                         }
                    }
                }
            },
            Err(_) => {
                scan_res.error_msg = Some("DNS Fail".to_string());
            }
        }

        results.push(scan_res);
        pb.inc(1);
    }
    
    pb.finish_with_message("Batch scan complete");
    
    let working = results.iter().filter(|r| r.is_working).count();
    println!("\nBatch Summary: {} working out of {}", working, total);

    Ok(results)
}