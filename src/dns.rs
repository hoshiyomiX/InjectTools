use anyhow::Result;
use std::net::IpAddr;
use trust_dns_resolver::config::*;
use trust_dns_resolver::TokioAsyncResolver;

pub struct DnsResolver {
    resolver: TokioAsyncResolver,
}

impl DnsResolver {
    pub fn new() -> Result<Self> {
        let resolver = TokioAsyncResolver::tokio(
            ResolverConfig::cloudflare(),
            ResolverOpts::default(),
        )?;
        Ok(Self { resolver })
    }

    pub async fn resolve(&self, domain: &str) -> Result<Option<IpAddr>> {
        match self.resolver.lookup_ip(domain).await {
            Ok(lookup) => {
                if let Some(ip) = lookup.iter().next() {
                    return Ok(Some(ip));
                }
                Ok(None)
            }
            Err(_) => Ok(None),
        }
    }
}

pub fn is_cloudflare(ip: &IpAddr) -> bool {
    if let IpAddr::V4(ipv4) = ip {
        let octets = ipv4.octets();
        
        // Cloudflare IP ranges
        // 104.16.0.0/13 -> 104.16-23.*
        if octets[0] == 104 && (16..=23).contains(&octets[1]) {
            return true;
        }
        
        // 172.64.0.0/13 -> 172.64-71.*
        if octets[0] == 172 && (64..=71).contains(&octets[1]) {
            return true;
        }
        
        // 173.245.48.0/20
        if octets[0] == 173 && octets[1] == 245 {
            return true;
        }
        
        // 162.158.0.0/15
        if octets[0] == 162 && (158..=159).contains(&octets[1]) {
            return true;
        }
        
        // 141.101.64.0/18
        if octets[0] == 141 && octets[1] == 101 {
            return true;
        }
    }
    
    false
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::str::FromStr;

    #[test]
    fn test_cloudflare_detection() {
        assert!(is_cloudflare(&IpAddr::from_str("104.16.1.1").unwrap()));
        assert!(is_cloudflare(&IpAddr::from_str("172.64.1.1").unwrap()));
        assert!(is_cloudflare(&IpAddr::from_str("173.245.48.1").unwrap()));
        assert!(!is_cloudflare(&IpAddr::from_str("8.8.8.8").unwrap()));
        assert!(!is_cloudflare(&IpAddr::from_str("1.1.1.1").unwrap()));
    }
}