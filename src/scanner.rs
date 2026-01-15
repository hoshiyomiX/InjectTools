use colored::Colorize;
use indicatif::{ProgressBar, ProgressStyle};
use reqwest::Client;
use std::fs;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::time::Duration;

use crate::dns;
use crate::ui;

#[derive(Debug, Clone)]
pub struct ScanResult {
    pub subdomain: String,
    pub ip: String,
    pub is_cloudflare: bool,
    pub is_working: bool,
    pub status_code: Option<u16>,
}

pub async fn test_target(target: &str, timeout: u64) -> anyhow::Result<()> {
    println!("\n{}", "Testing target host...".cyan());
    println!("{}", "━".repeat(50).bright_black());
    
    let client = Client::builder()
        .timeout(Duration::from_secs(timeout))
        .danger_accept_invalid_certs(true)
        .build()?;

    let url = format!("http://{}", target);
    println!("\n{} {}", "URL:".bright_black(), url);
    
    match client.get(&url).send().await {
        Ok(response) => {
            let status = response.status().as_u16();
            println!("{} Status: {}", "✓".green(), status.to_string().green());
            println!("{} Target host is reachable", "✓".green());
        }
        Err(e) => {
            println!("{} {}", "✗".red(), format!("Error: {}", e).red());
            println!("{}", "⚠️  Target might be unreachable or timeout".yellow());
        }
    }
    
    Ok(())
}

pub async fn test_single(target: &str, subdomain: &str, timeout: u64) -> anyhow::Result<()> {
    println!("\n{}", "Testing subdomain...".cyan());
    println!("{}", "━".repeat(50).bright_black());
    println!("\n{} {}", "Subdomain:".bright_black(), subdomain);
    println!("{} {}", "Target:".bright_black(), target);
    
    // DNS resolution
    print!("\n{} Resolving DNS...", "📡".cyan());
    match dns::resolve_domain_first(subdomain).await {
        Ok(ip) => {
            println!(" {} {}", "✓".green(), ip.green());
            
            let is_cf = dns::is_cloudflare_ip(&ip);
            if is_cf {
                println!("{} {} {}", "☁️".cyan(), "Cloudflare IP detected".cyan(), ip.bright_black());
            } else {
                println!("{} {} {}", "🌐".yellow(), "Non-Cloudflare IP".yellow(), ip.bright_black());
            }
            
            // HTTP test
            print!("\n{} Testing connection...", "🔌".cyan());
            let client = Client::builder()
                .timeout(Duration::from_secs(timeout))
                .danger_accept_invalid_certs(true)
                .build()?;

            let url = format!("http://{}", target);
            let request = client
                .get(&url)
                .header("Host", subdomain)
                .build()?;

            match client.execute(request).await {
                Ok(response) => {
                    let status = response.status().as_u16();
                    println!(" {} Status: {}", "✓".green(), status.to_string().green());
                    
                    if is_cf {
                        println!("\n{}", "═".repeat(50).green());
                        println!("{}", "✅ WORKING BUG!".green().bold());
                        println!("{} {}", "Subdomain:".bright_black(), subdomain.green());
                        println!("{} {}", "IP:".bright_black(), ip.green());
                        println!("{} {}", "Status:".bright_black(), status.to_string().green());
                        println!("{}", "═".repeat(50).green());
                    } else {
                        println!("\n{}", "⚠️  Not a Cloudflare IP".yellow());
                    }
                }
                Err(e) => {
                    println!(" {} {}", "✗".red(), format!("Error: {}", e).red());
                }
            }
        }
        Err(e) => {
            println!(" {} {}", "✗".red(), format!("DNS resolution failed: {}", e).red());
        }
    }
    
    Ok(())
}

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

    let client = Client::builder()
        .timeout(Duration::from_secs(timeout))
        .danger_accept_invalid_certs(true)
        .build()?;

    for subdomain in subdomains {
        if !running.load(Ordering::SeqCst) {
            pb.finish_with_message("Cancelled");
            break;
        }

        pb.set_message(format!("Testing: {}", subdomain));
        
        // DNS resolution
        if let Ok(ip) = dns::resolve_domain_first(subdomain).await {
            let is_cf = dns::is_cloudflare_ip(&ip);
            
            // HTTP test
            let url = format!("http://{}", target);
            let request = client
                .get(&url)
                .header("Host", subdomain)
                .build();

            if let Ok(req) = request {
                if let Ok(response) = client.execute(req).await {
                    let status = response.status().as_u16();
                    
                    results.push(ScanResult {
                        subdomain: subdomain.clone(),
                        ip: ip.clone(),
                        is_cloudflare: is_cf,
                        is_working: is_cf && status < 500,
                        status_code: Some(status),
                    });
                }
            }
        }
        
        pb.inc(1);
    }
    
    pb.finish_with_message("Complete");
    
    // Display results
    let working: Vec<_> = results.iter().filter(|r| r.is_working).collect();
    let non_cf: Vec<_> = results.iter().filter(|r| !r.is_cloudflare && r.status_code.is_some()).collect();
    
    println!("\n{}", "═".repeat(60).cyan());
    ui::center_text("HASIL SCAN");
    println!("{}", "═".repeat(60).cyan());
    
    if working.is_empty() {
        println!("\n{}", "❌ Tidak ada working bug ditemukan".yellow());
    } else {
        println!("\n{} Working Bugs ({}):", "✅".green(), working.len());
        for result in &working {
            println!("  {} {} ({})", "🟢".green(), result.subdomain.green(), result.ip.bright_black());
        }
    }
    
    if !non_cf.is_empty() {
        println!("\n{} Non-CF Responses ({}):", "📝".yellow(), non_cf.len());
        for result in non_cf.iter().take(5) {
            println!("  {} {} ({})", "🟡".yellow(), result.subdomain, result.ip.bright_black());
        }
        if non_cf.len() > 5 {
            println!("  ... and {} more", non_cf.len() - 5);
        }
    }
    
    println!("\n{}", "─".repeat(60).bright_black());
    println!("{}" , "Statistik:");
    println!("  Scanned: {}/{} ({}%)", results.len(), total, (results.len() * 100 / total.max(1)));
    println!("  CF Found: {} | Non-CF: {}", working.len(), non_cf.len());
    println!("{}", "─".repeat(60).bright_black());
    
    Ok(results)
}

pub fn load_batch_file(path: &str) -> anyhow::Result<Vec<String>> {
    let content = fs::read_to_string(path)?;
    let subdomains: Vec<String> = content
        .lines()
        .map(|line| line.trim().to_string())
        .filter(|line| !line.is_empty() && !line.starts_with('#'))
        .collect();
    
    Ok(subdomains)
}

pub async fn full_scan(
    target: &str,
    domain: &str,
    timeout: u64,
    running: Arc<AtomicBool>,
) -> anyhow::Result<()> {
    ui::print_header("FULL DOMAIN SCAN");
    println!("\n{} {}", "Domain:".bright_black(), domain.cyan());
    println!("{} {}\n", "Target:".bright_black(), target.cyan());
    
    // Common subdomains to test
    let common_subs = vec![
        format!("www.{}", domain),
        format!("api.{}", domain),
        format!("cdn.{}", domain),
        format!("static.{}", domain),
        format!("mail.{}", domain),
        format!("ftp.{}", domain),
        format!("blog.{}", domain),
        format!("shop.{}", domain),
        format!("admin.{}", domain),
        format!("dev.{}", domain),
        format!("staging.{}", domain),
        format!("test.{}", domain),
        format!("m.{}", domain),
        format!("mobile.{}", domain),
        format!("app.{}", domain),
    ];
    
    let results = batch_test(target, &common_subs, timeout, running).await?;
    
    if !results.is_empty() {
        crate::results::export_results(&results, domain)?;
    }
    
    Ok(())
}