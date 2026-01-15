// crt.sh API integration for subdomain enumeration

use anyhow::{Result, bail};
use colored::*;
use serde::Deserialize;
use std::collections::HashSet;

#[derive(Deserialize, Debug)]
struct CrtShEntry {
 name_value: String,
}

pub async fn fetch_subdomains(domain: &str) -> Result<Vec<String>> {
 println!("{} {}", "🌐 Scanning subdomains for:".cyan(), domain.white());
 println!();

 let url = format!("https://crt.sh/?q=%.{}&output=json", domain);

 // Fetch data from crt.sh
 let client = reqwest::Client::builder()
 .timeout(std::time::Duration::from_secs(60))
 .build()?;

 let response = client.get(&url).send().await?;

 if !response.status().is_success() {
 bail!("HTTP request failed with status: {}", response.status());
 }

 let body = response.text().await?;

 if body.is_empty() || body == "[]" {
 bail!("Gak ada data ditemukan untuk domain ini");
 }

 println!("{}", "✅ Data berhasil diambil".green());
 println!();
 println!("{}", "📝 Parsing data...".cyan());

 // Parse JSON
 let entries: Vec<CrtShEntry> = match serde_json::from_str(&body) {
 Ok(e) => e,
 Err(e) => bail!("Failed to parse JSON: {}", e),
 };

 // Extract unique subdomains
 let mut subdomains = HashSet::new();

 for entry in entries {
 // Split by newlines (crt.sh can return multiple domains per entry)
 for line in entry.name_value.lines() {
 let subdomain = line.trim().to_lowercase();

 // Skip wildcards
 if subdomain.starts_with('*') {
 continue;
 }

 // Only include actual subdomains of target domain
 if subdomain.ends_with(&format!(".{}", domain)) || subdomain == domain {
 subdomains.insert(subdomain);
 }
 }
 }

 let mut result: Vec<String> = subdomains.into_iter().collect();
 result.sort();

 let count = result.len();

 if count == 0 {
 bail!("Gak ada subdomain ditemukan");
 }

 println!(
 "{} {} unique subdomains",
 "✅ Ditemukan".green(),
 count.to_string().white().bold()
 );

 Ok(result)
}
