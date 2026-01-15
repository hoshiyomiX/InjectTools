use reqwest::Client;
use serde::Deserialize;
use std::collections::HashSet;
use std::time::Duration;

#[derive(Debug, Deserialize)]
struct CrtShEntry {
    name_value: String,
}

pub async fn fetch_subdomains(domain: &str) -> anyhow::Result<Vec<String>> {
    // Try with retry mechanism (3 attempts)
    let mut last_error = None;
    
    for attempt in 1..=3 {
        match fetch_with_timeout(domain, attempt).await {
            Ok(subs) => return Ok(subs),
            Err(e) => {
                eprintln!("⚠️  Attempt {}/3 failed: {}", attempt, e);
                last_error = Some(e);
                
                if attempt < 3 {
                    eprintln!("   Retrying in {} seconds...", attempt);
                    tokio::time::sleep(Duration::from_secs(attempt as u64)).await;
                }
            }
        }
    }
    
    Err(last_error.unwrap_or_else(|| anyhow::anyhow!("Unknown error")))
}

async fn fetch_with_timeout(domain: &str, attempt: u32) -> anyhow::Result<Vec<String>> {
    let url = format!("https://crt.sh/?q=%.{}&output=json", domain);
    
    // Increase timeout on each retry
    let timeout_secs = 30 + (attempt - 1) * 15; // 30s, 45s, 60s
    
    let client = Client::builder()
        .connect_timeout(Duration::from_secs(10)) // Connection timeout
        .timeout(Duration::from_secs(timeout_secs)) // Total request timeout
        .danger_accept_invalid_certs(false) // Enforce TLS validation
        .build()?;
    
    let response = client
        .get(&url)
        .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) InjectTools/2.3")
        .header("Accept", "application/json")
        .send()
        .await
        .map_err(|e| {
            if e.is_timeout() {
                anyhow::anyhow!("Request timeout setelah {}s", timeout_secs)
            } else if e.is_connect() {
                anyhow::anyhow!("Gagal connect ke crt.sh (network issue)")
            } else {
                anyhow::anyhow!("Network error: {}", e)
            }
        })?;
    
    if !response.status().is_success() {
        return Err(anyhow::anyhow!(
            "crt.sh returned HTTP {}: {}",
            response.status().as_u16(),
            response.status().canonical_reason().unwrap_or("Unknown")
        ));
    }
    
    // Parse JSON
    let entries: Vec<CrtShEntry> = response.json().await
        .map_err(|e| anyhow::anyhow!("Failed to parse JSON response: {}", e))?;
    
    if entries.is_empty() {
        return Err(anyhow::anyhow!("No certificates found untuk domain {}", domain));
    }
    
    // Extract unique subdomains
    let mut subdomains = HashSet::new();
    
    for entry in entries {
        // Handle wildcard certificates and multiple domains
        for name in entry.name_value.split('\n') {
            let name = name.trim();
            if name.is_empty() {
                continue;
            }
            
            // Remove wildcard prefix
            let cleaned = name.trim_start_matches("*.");
            
            // Validate subdomain
            if cleaned.ends_with(domain) 
                && !cleaned.contains(' ') 
                && cleaned.contains('.') {
                subdomains.insert(cleaned.to_string());
            }
        }
    }
    
    if subdomains.is_empty() {
        return Err(anyhow::anyhow!("No valid subdomains extracted dari {} certificates", entries.len()));
    }
    
    let mut result: Vec<String> = subdomains.into_iter().collect();
    result.sort();
    
    Ok(result)
}