use trust_dns_resolver::TokioAsyncResolver;
use trust_dns_resolver::config::*;
use ipnetwork::Ipv4Network;
use std::net::Ipv4Addr;
use std::str::FromStr;
use std::process::Command;
use std::time::Duration;

// Cloudflare IP ranges
const CLOUDFLARE_RANGES: &[&str] = &[
    "173.245.48.0/20",
    "103.21.244.0/22",
    "103.22.200.0/22",
    "103.31.4.0/22",
    "141.101.64.0/18",
    "108.162.192.0/18",
    "190.93.240.0/20",
    "188.114.96.0/20",
    "197.234.240.0/22",
    "198.41.128.0/17",
    "162.158.0.0/15",
    "104.16.0.0/13",
    "104.24.0.0/14",
    "172.64.0.0/13",
    "131.0.72.0/22",
];

pub fn is_cloudflare_ip(ip: &str) -> bool {
    let parsed_ip = match Ipv4Addr::from_str(ip) {
        Ok(addr) => addr,
        Err(_) => return false,
    };

    for range_str in CLOUDFLARE_RANGES {
        if let Ok(range) = Ipv4Network::from_str(range_str) {
            if range.contains(parsed_ip) {
                return true;
            }
        }
    }

    false
}

// ═══════════════════════════════════════════════════════════════
// ✨ NEW: Check Cloudflare via HTTP Headers (CF-Ray detection)
// ═══════════════════════════════════════════════════════════════

/// Verify if subdomain is behind Cloudflare by checking CF-Ray header
/// This handles BYOIP (Bring Your Own IP) scenarios where IPs may not be
/// in official Cloudflare ranges but still proxied through Cloudflare
pub async fn check_cf_via_http(subdomain: &str, timeout_secs: u64) -> bool {
    let output = Command::new("curl")
        .arg("-I")                          // HEAD request
        .arg("-s")                          // Silent
        .arg("-L")                          // Follow redirects
        .arg("--max-time")
        .arg(timeout_secs.to_string())
        .arg("-k")                          // Allow insecure
        .arg(format!("https://{}", subdomain))
        .output();
    
    if let Ok(result) = output {
        if let Ok(stdout) = String::from_utf8(result.stdout) {
            // Check for CF-Ray header (primary indicator)
            if stdout.to_lowercase().contains("cf-ray:") {
                return true;
            }
            
            // Secondary check: Cloudflare Server header
            if stdout.to_lowercase().contains("server: cloudflare") {
                return true;
            }
            
            // Tertiary check: CF-Cache-Status (Cloudflare caching)
            if stdout.to_lowercase().contains("cf-cache-status:") {
                return true;
            }
        }
    }
    
    false
}

// ═══════════════════════════════════════════════════════════════
// ✨ NEW: Enhanced Cloudflare Detection (Hybrid Method)
// ═══════════════════════════════════════════════════════════════

/// Enhanced Cloudflare detection combining:
/// 1. IP range check (fast, works for official CF IPs)
/// 2. HTTP header check (slower, handles BYOIP/custom ranges)
/// 
/// This hybrid approach ensures accurate detection even when:
/// - Custom IP ranges are used (BYOIP)
/// - Cloudflare Enterprise with custom IPs
/// - New Cloudflare IP ranges not yet in our list
pub async fn is_cloudflare_enhanced(subdomain: &str, ip: &str) -> bool {
    // Fast path: Check official IP ranges first
    if is_cloudflare_ip(ip) {
        return true;
    }
    
    // Slow path: Verify via HTTP headers
    // This catches BYOIP and custom Cloudflare setups
    check_cf_via_http(subdomain, 5).await
}

pub async fn resolve_domain(domain: &str) -> anyhow::Result<Vec<String>> {
    let resolver = TokioAsyncResolver::tokio(
        ResolverConfig::default(),
        ResolverOpts::default(),
    );

    let response = resolver.lookup_ip(domain).await?;
    let ips: Vec<String> = response.iter().map(|ip| ip.to_string()).collect();
    
    Ok(ips)
}

pub async fn resolve_domain_first(domain: &str) -> anyhow::Result<String> {
    let ips = resolve_domain(domain).await?;
    ips.first()
        .cloned()
        .ok_or_else(|| anyhow::anyhow!("No IP found for domain"))
}