use trust_dns_resolver::TokioAsyncResolver;
use trust_dns_resolver::config::*;
use ipnetwork::Ipv4Network;
use std::net::Ipv4Addr;
use std::str::FromStr;

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