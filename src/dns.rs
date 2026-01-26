use anyhow::{Context, Result};
use hickory_resolver::TokioAsyncResolver;
use std::net::IpAddr;

pub async fn resolve_domain(domain: &str) -> Result<Vec<IpAddr>> {
    // Create resolver using from_tokio() - hickory-resolver 0.24+ API
    let resolver = TokioAsyncResolver::from_system_conf()
        .await
        .context("Failed to create DNS resolver")?;

    let response = resolver
        .lookup_ip(domain)
        .await
        .context(format!("Failed to resolve domain: {}", domain))?;

    let ips: Vec<IpAddr> = response.iter().collect();

    if ips.is_empty() {
        anyhow::bail!("No IP addresses found for domain: {}", domain);
    }

    Ok(ips)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_resolve_cloudflare() {
        let ips = resolve_domain("cloudflare.com").await.unwrap();
        assert!(!ips.is_empty());
    }

    #[tokio::test]
    async fn test_resolve_nonexistent() {
        let result = resolve_domain("this-domain-does-not-exist-12345.com").await;
        assert!(result.is_err());
    }
}
