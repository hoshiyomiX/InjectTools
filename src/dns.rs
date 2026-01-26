use anyhow::{Context, Result};
use hickory_resolver::TokioAsyncResolver;
use hickory_resolver::config::*;
use std::net::IpAddr;
use std::time::Duration;

pub struct DnsResolver {
    resolver: TokioAsyncResolver,
}

impl DnsResolver {
    pub fn new() -> Result<Self> {
        let resolver = TokioAsyncResolver::tokio(
            ResolverConfig::default(),
            ResolverOpts::default(),
        )
        .context("Failed to create DNS resolver")?;

        Ok(Self { resolver })
    }

    pub async fn resolve(&self, domain: &str) -> Result<Vec<IpAddr>> {
        let response = self
            .resolver
            .lookup_ip(domain)
            .await
            .context(format!("Failed to resolve domain: {}", domain))?;

        let ips: Vec<IpAddr> = response.iter().collect();
        
        if ips.is_empty() {
            anyhow::bail!("No IP addresses found for domain: {}", domain);
        }

        Ok(ips)
    }

    pub async fn resolve_first(&self, domain: &str) -> Result<IpAddr> {
        let ips = self.resolve(domain).await?;
        ips.into_iter()
            .next()
            .context(format!("No IP address resolved for: {}", domain))
    }

    pub async fn resolve_timeout(&self, domain: &str, timeout: Duration) -> Result<Vec<IpAddr>> {
        tokio::time::timeout(timeout, self.resolve(domain))
            .await
            .context("DNS resolution timed out")?
    }

    pub async fn reverse_lookup(&self, ip: IpAddr) -> Result<String> {
        let response = self
            .resolver
            .reverse_lookup(ip)
            .await
            .context(format!("Failed to reverse lookup IP: {}", ip))?;

        response
            .iter()
            .next()
            .map(|name| name.to_string())
            .context(format!("No PTR record found for IP: {}", ip))
    }
}

impl Default for DnsResolver {
    fn default() -> Self {
        Self::new().expect("Failed to create default DNS resolver")
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_resolve_google() {
        let resolver = DnsResolver::new().unwrap();
        let ips = resolver.resolve("google.com").await.unwrap();
        assert!(!ips.is_empty());
    }

    #[tokio::test]
    async fn test_resolve_first() {
        let resolver = DnsResolver::new().unwrap();
        let ip = resolver.resolve_first("cloudflare.com").await.unwrap();
        println!("Cloudflare IP: {}", ip);
    }

    #[tokio::test]
    async fn test_resolve_timeout() {
        let resolver = DnsResolver::new().unwrap();
        let ips = resolver
            .resolve_timeout("example.com", Duration::from_secs(5))
            .await
            .unwrap();
        assert!(!ips.is_empty());
    }
}
