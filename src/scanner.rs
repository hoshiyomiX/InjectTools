use colored::Colorize;
use indicatif::{ProgressBar, ProgressStyle};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::time::{Duration, Instant};
use std::process::Command;
use tokio::net::TcpStream; // Using tokio for async latency check

use crate::dns;
use crate::ui;

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
// HELPER FUNCTIONS (Logic V14)
// =============================================================================

fn is_private_ip(ip: &str) -> bool {
    if ip.starts_with("10.") || ip.starts_with("192.168.") || ip.starts_with("127.") {
        return true;
    }
    if ip.starts_with("172.") {
        let parts: Vec<&str> = ip.split('.').collect();
        if let Ok(second_octet) = parts[1].parse::<u8>() {
            if second_octet >= 16 && second_octet <= 31 {
                return true;
            }
        }
    }
    if ip.starts_with("100.") {
         let parts: Vec<&str> = ip.split('.').collect();
         if let Ok(second_octet) = parts[1].parse::<u8>() {
             if second_octet >= 64 && second_octet <= 127 {
                 return true; // CGNAT
             }
         }
    }
    false
}

fn is_fake_dns_ip(ip: &str) -> bool {
    ip.starts_with("198.18.") || ip.starts_with("198.19.")
}

/// Check target health to distinguish between "Subdomain Mismatch" and "Target Dead"
/// Returns (Status, Note)
/// Status: ONLINE | OFFLINE | UNKNOWN
async fn check_target_health(target: &str) -> (String, String) {
    // 1. Resolve Target
    let t_ip = match dns::resolve_domain_first(target).await {
        Ok(ip) => ip,
        Err(_) => return ("UNKNOWN".to_string(), "DNS_FAIL".to_string()),
    };

    // 2. Try Direct SSL (Target IP + Target SNI)
    let ssl_cmd = Command::new("timeout")
        .arg("5")
        .arg("openssl")
        .arg("s_client")
        .arg("-connect")
        .arg(format!("{}:443", t_ip))
        .arg("-servername")
        .arg(target)
        .arg("-brief")
        .output();

    if let Ok(output) = ssl_cmd {
        let out_str = String::from_utf8_lossy(&output.stdout).to_string() + &String::from_utf8_lossy(&output.stderr).to_string();
        if out_str.contains("ESTABLISHED") || out_str.contains("Verification: OK") {
            return ("ONLINE".to_string(), "SSL_OK".to_string());
        }
    }

    // 3. Fallback: HTTP Port 80
    // Curl -I http://target/
    let curl_cmd = Command::new("curl")
        .arg("-s")
        .arg("-o")
        .arg("/dev/null")
        .arg("-w")
        .arg("%{http_code}")
        .arg("--connect-timeout")
        .arg("3")
        .arg(format!("http://{}/", target))
        .output();

    if let Ok(output) = curl_cmd {
        let code = String::from_utf8_lossy(&output.stdout).trim().to_string();
        if code != "000" && !code.is_empty() {
            return ("ONLINE".to_string(), format!("HTTP_{}", code));
        }
    }

    ("OFFLINE".to_string(), "NO_SSL_NO_HTTP".to_string())
}

// =============================================================================
// MAIN VALIDATION LOGIC (Menu 1: Test Single)
// =============================================================================

pub async fn test_single(target: &str, subdomain: &str, _timeout: u64) -> anyhow::Result<()> {
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
        let (ts, _) = check_target_health(target).await;
        print_report(target, &ts, subdomain, &sub_ip, "NOT_WORKING", "ENV_VPN_FAKE_DNS");
        return Ok(());
    }
    if is_private_ip(&sub_ip) {
        let (ts, _) = check_target_health(target).await;
        print_report(target, &ts, subdomain, &sub_ip, "NOT_WORKING", "ENV_PRIVATE_IP");
        return Ok(());
    }
    // Strict Cloudflare Check (Menu 1 uses strict V14 logic)
    if !dns::is_cloudflare_ip(&sub_ip) {
        let (ts, _) = check_target_health(target).await;
        print_report(target, &ts, subdomain, &sub_ip, "NOT_WORKING", "SUBDOMAIN_NOT_CLOUDFLARE");
        return Ok(());
    }

    // 3. Latency Check (<5ms detection for VPN Interception)
    let start = Instant::now();
    let tcp_check = tokio::time::timeout(Duration::from_secs(3), TcpStream::connect((sub_ip.as_str(), 443))).await;
    let latency = start.elapsed().as_millis();

    match tcp_check {
        Ok(Ok(_)) => {
            if latency < 5 {
                let (ts, _) = check_target_health(target).await;
                print_report(target, &ts, subdomain, &sub_ip, "NOT_WORKING", "ENV_VPN_DETECTED(LATENCY <5ms)");
                return Ok(());
            }
        },
        _ => {
            let (ts, _) = check_target_health(target).await;
            print_report(target, &ts, subdomain, &sub_ip, "NOT_WORKING", "SUBDOMAIN_TCP_443_BLOCKED");
            return Ok(());
        }
    }

    // 4. SSL Handshake (Subdomain IP + Target SNI)
    // We use command wrapper because openssl crate complexity is high for this specific check
    let ssl_cmd = Command::new("timeout")
        .arg("5")
        .arg("openssl")
        .arg("s_client")
        .arg("-connect")
        .arg(format!("{}:443", sub_ip))
        .arg("-servername")
        .arg(target)
        .arg("-brief")
        .output();

    let mut ssl_success = false;
    let mut ssl_output = String::new();

    if let Ok(output) = ssl_cmd {
        ssl_output = String::from_utf8_lossy(&output.stdout).to_string() + &String::from_utf8_lossy(&output.stderr).to_string();
        if ssl_output.contains("ESTABLISHED") || ssl_output.contains("Verification: OK") {
            ssl_success = true;
        }
    }

    if !ssl_success {
        // Handshake Failed -> Check Target Health
        let (t_status, t_note) = check_target_health(target).await;
        
        let bug_note = if t_status == "OFFLINE" {
            format!("TARGET_OFFLINE({})", t_note)
        } else {
            // Target is ONLINE, so it's a mismatch
            if ssl_output.contains("errno=104") || ssl_output.contains("Connection reset") {
                "BUG_NOT_COMPATIBLE(TLS_RESET)".to_string()
            } else if ssl_output.contains("unexpected eof") {
                "BUG_NOT_COMPATIBLE(EOF_PROXY?)".to_string()
            } else if ssl_output.contains("handshake failure") {
                "BUG_NOT_COMPATIBLE(HANDSHAKE_FAIL)".to_string()
            } else {
                "BUG_NOT_COMPATIBLE(SSL_FAIL)".to_string()
            }
        };

        print_report(target, &t_status, subdomain, &sub_ip, "NOT_WORKING", &bug_note);
        return Ok(());
    }

    // 5. HTTP Check (End-to-End)
    // curl -I -s --http1.1 --resolve target:443:ip https://target/
    let curl_cmd = Command::new("curl")
        .arg("-s")
        .arg("-I")
        .arg("--http1.1")
        .arg("--resolve")
        .arg(format!("{}:443:{}", target, sub_ip))
        .arg(format!("https://{}/", target))
        .arg("--max-time")
        .arg("5")
        .arg("-k")
        .output();

    let mut http_code = "000".to_string();
    let mut has_cf_ray = false;

    if let Ok(output) = curl_cmd {
        let out_str = String::from_utf8_lossy(&output.stdout).to_string();
        
        // Extract HTTP Code
        if let Some(line) = out_str.lines().next() {
            let parts: Vec<&str> = line.split_whitespace().collect();
            if parts.len() >= 2 {
                http_code = parts[1].to_string();
            }
        }

        // Check CF-Ray
        if out_str.to_lowercase().contains("cf-ray:") {
            has_cf_ray = true;
        }
    }

    // Determine Final Status
    let mut target_status = "ONLINE";
    if http_code.starts_with("52") || http_code == "530" {
        target_status = "OFFLINE";
    }

    if has_cf_ray && http_code != "000" {
         print_report(target, target_status, subdomain, &sub_ip, "WORKING", &format!("OK_HTTP_{}_CF", http_code));
    } else {
         let note = if http_code == "000" { "HTTP_NO_RESPONSE".to_string() } else { format!("HTTP_{}_NO_CF", http_code) };
         print_report(target, target_status, subdomain, &sub_ip, "NOT_WORKING", &note);
    }
    
    Ok(())
}

// Helper to print standard report (V14 Style)
fn print_report(target: &str, t_status: &str, subdomain: &str, ip: &str, b_status: &str, note: &str) {
    println!("");
    println!("{}", "==================== RESULT ====================".blue());
    
    // Target Status Color
    let t_color = match t_status {
        "ONLINE" => "green",
        "OFFLINE" => "red",
        _ => "yellow",
    };
    println!("TARGET: {} | {}", target, t_status.color(t_color));

    // Bug Status Color
    let b_color = if b_status == "WORKING" { "green" } else { "red" };
    println!("BUG   : {} -> {} | {}", subdomain, ip, b_status.color(b_color));
    
    println!("NOTE  : {}", note);
    println!("{}", "================================================".blue());
    println!("");
}

// ═══════════════════════════════════════════════════════════════
// BATCH TEST (Preserved but compatible)
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
        
        pb.set_message(format!("Scanning: {}", subdomain));

        // Use standard logic logic, but we must return ScanResult struct.
        // For simplicity in batch mode, we stick to the core check without full verbose reports.
        
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

        if let Ok(ip) = dns::resolve_domain_first(subdomain).await {
            scan_res.ip = ip.clone();
            
            // Basic checks
            if !is_fake_dns_ip(&ip) && !is_private_ip(&ip) && dns::is_cloudflare_ip(&ip) {
                scan_res.is_cloudflare = true;

                // SSL Check (Simplified wrapper)
                let ssl_cmd = Command::new("timeout").arg("3").arg("openssl").arg("s_client").arg("-connect").arg(format!("{}:443", ip)).arg("-servername").arg(target).arg("-brief").output();
                let ssl_ok = match ssl_cmd {
                    Ok(o) => String::from_utf8_lossy(&o.stdout).contains("ESTABLISHED"),
                    Err(_) => false,
                };

                if ssl_ok {
                    // HTTP Check
                     let curl_cmd = Command::new("curl").arg("-s").arg("-I").arg("--http1.1").arg("--resolve").arg(format!("{}:443:{}", target, ip)).arg(format!("https://{}/", target)).arg("--max-time").arg("3").arg("-k").output();
                     if let Ok(output) = curl_cmd {
                         let out = String::from_utf8_lossy(&output.stdout);
                         if out.to_lowercase().contains("cf-ray:") {
                             scan_res.cf_ray = Some("Yes".to_string());
                             if let Some(line) = out.lines().next() {
                                 if let Some(code) = line.split_whitespace().nth(1) {
                                     scan_res.status_code = code.parse().ok();
                                     if let Some(c) = scan_res.status_code {
                                         if c != 0 { scan_res.is_working = true; }
                                     }
                                 }
                             }
                         }
                     }
                } else {
                    scan_res.error_msg = Some("SSL Handshake Failed".to_string());
                }
            } else {
                 scan_res.error_msg = Some("Not Valid Cloudflare/VPN Detected".to_string());
            }
        } else {
            scan_res.error_msg = Some("DNS Failed".to_string());
        }
        
        results.push(scan_res);
        pb.inc(1);
    }
    
    pb.finish_with_message("Batch scan complete");
    
    // Summary
    let working = results.iter().filter(|r| r.is_working).count();
    println!("\nBatch Summary: {} working out of {}", working, total);

    Ok(results)
}
