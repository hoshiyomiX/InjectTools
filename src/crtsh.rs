use reqwest::Client;
use serde::Deserialize;
use std::collections::HashSet;

#[derive(Debug, Deserialize)]
struct CrtShEntry {
    name_value: String,
}

pub async fn fetch_subdomains(domain: &str) -> anyhow::Result<Vec<String>> {
    let url = format!("https://crt.sh/?q=%.{}&output=json", domain);
    
    let client = Client::builder()
        .timeout(std::time::Duration::from_secs(30))
        .build()?;
    
    let response = client
        .get(&url)
        .header("User-Agent", "InjectTools/2.3")
        .send()
        .await?;
    
    if !response.status().is_success() {
        return Err(anyhow::anyhow!("crt.sh returned status: {}", response.status()));
    }
    
    let entries: Vec<CrtShEntry> = response.json().await?;
    
    // Extract unique subdomains
    let mut subdomains = HashSet::new();
    
    for entry in entries {
        // Handle wildcard certificates and multiple domains
        for name in entry.name_value.split('\n') {
            let cleaned = name.trim().replace("*.", "");
            if cleaned.ends_with(domain) && !cleaned.contains(' ') {
                subdomains.insert(cleaned);
            }
        }
    }
    
    let mut result: Vec<String> = subdomains.into_iter().collect();
    result.sort();
    
    Ok(result)
}