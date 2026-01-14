use crate::config::Config;
use crate::dns::{self, DnsResolver};
use crate::ui;
use crate::wordlist;

use anyhow::Result;
use colored::*;
use indicatif::{ProgressBar, ProgressStyle};
use std::io::{self, Write};
use std::net::IpAddr;
use std::sync::Arc;
use std::time::Duration;

pub struct TestResult {
    pub subdomain: String,
    pub ip: String,
    pub works: bool,
    pub reason: String,
}

pub async fn test_subdomain(config: &Config, subdomain: &str) -> Result<TestResult> {
    let resolver = DnsResolver::new()?;
    
    // Resolve DNS
    let ip = match resolver.resolve(subdomain).await? {
        Some(ip) => ip,
        None => {
            return Ok(TestResult {
                subdomain: subdomain.to_string(),
                ip: "N/A".to_string(),
                works: false,
                reason: "DNS resolution failed".to_string(),
            });
        }
    };
    
    println!("   {}: {}", "IP Address".white(), ip.to_string().blue());
    
    // Check if Cloudflare
    let is_cf = dns::is_cloudflare(&ip);
    if is_cf {
        println!("   {}: {}", "Provider".white(), "☁️  Cloudflare".cyan());
    } else {
        println!("   {}: {}", "Provider".white(), "⚠️  Non-Cloudflare".yellow());
    }
    
    println!();
    println!("{}", "🧪 Testing bug inject...".cyan());
    
    // Test connection
    let works = test_connection(&config.target_host, &ip, config.timeout).await?;
    
    Ok(TestResult {
        subdomain: subdomain.to_string(),
        ip: ip.to_string(),
        works,
        reason: if works {
            "Connected successfully".to_string()
        } else {
            "Connection failed or blocked".to_string()
        },
    })
}

async fn test_connection(target_host: &str, ip: &IpAddr, timeout_secs: u64) -> Result<bool> {
    let url = format!("https://{}/", target_host);
    
    // Create custom DNS resolver that maps target_host to the specific IP
    let client = reqwest::Client::builder()
        .timeout(Duration::from_secs(timeout_secs))
        .danger_accept_invalid_certs(true)
        .resolve(target_host, format!("{}:443", ip).parse()?)
        .build()?;
    
    match client.get(&url).send().await {
        Ok(response) => Ok(response.status().is_success() || response.status().is_redirection()),
        Err(_) => Ok(false),
    }
}

pub async fn full_scan(config: &Config) -> Result<()> {
    ui::clear_screen();
    ui::print_header("Cloudflare Bug Scanner");
    println!();
    
    // Get domain to scan
    print!(
        "{}",
        format!(
            "Masukkan target domain [default: {}]: ",
            config.default_domain
        )
        .white()
    );
    io::stdout().flush()?;
    
    let mut input = String::new();
    io::stdin().read_line(&mut input)?;
    let target_domain = if input.trim().is_empty() {
        config.default_domain.clone()
    } else {
        input.trim().to_string()
    };
    
    println!();
    println!("{}: {}", "Domain".blue(), target_domain.green());
    println!("{}: {}", "Target".blue(), config.target_host.green());
    println!(
        "{}: {}",
        "Filter".blue(),
        "☁️  Cloudflare only".cyan()
    );
    
    // Load wordlist
    let wordlist_data = wordlist::load_wordlist(&config.active_wordlist)?;
    let words: Vec<&str> = wordlist_data.lines().filter(|l| !l.trim().is_empty()).collect();
    let total = words.len();
    
    match &config.active_wordlist {
        Some(path) => {
            let name = std::path::Path::new(path)
                .file_name()
                .and_then(|n| n.to_str())
                .unwrap_or("unknown");
            println!("{}: {}", "Wordlist".blue(), name.green());
        }
        None => {
            println!(
                "{}: {}",
                "Wordlist".blue(),
                "Embedded (terbatas)".yellow()
            );
        }
    }
    
    println!();
    println!("{}", format!("✅ Loaded {} patterns", total).green());
    println!();
    
    // Progress bar
    let pb = ProgressBar::new(total as u64);
    pb.set_style(
        ProgressStyle::default_bar()
            .template("{spinner:.cyan} [{bar:40.cyan/blue}] {pos}/{len} ({percent}%) {msg}")
            .unwrap()
            .progress_chars("#>-"),
    );
    
    let resolver = Arc::new(DnsResolver::new()?);
    let mut work_bugs = Vec::new();
    let mut fail_bugs = Vec::new();
    let mut skipped = 0;
    
    let start = std::time::Instant::now();
    
    // Scan
    for (idx, sub) in words.iter().enumerate() {
        let domain = format!("{}.{}", sub, target_domain);
        pb.set_position((idx + 1) as u64);
        pb.set_message(format!("Testing: {}", domain));
        
        // Resolve
        let ip = match resolver.resolve(&domain).await? {
            Some(ip) => ip,
            None => {
                continue;
            }
        };
        
        // Check Cloudflare
        if !dns::is_cloudflare(&ip) {
            skipped += 1;
            continue;
        }
        
        // Test
        let works = test_connection(&config.target_host, &ip, config.timeout).await?;
        
        if works {
            work_bugs.push((domain.clone(), ip.to_string()));
        } else {
            fail_bugs.push((domain.clone(), ip.to_string()));
        }
        
        tokio::time::sleep(Duration::from_millis(50)).await;
    }
    
    pb.finish_and_clear();
    
    let elapsed = start.elapsed().as_secs();
    
    // Results
    println!();
    ui::print_header("HASIL SCAN");
    println!();
    
    if !work_bugs.is_empty() {
        println!(
            "{}",
            format!("✅ Working Bugs ({}):", work_bugs.len())
                .green()
                .bold()
        );
        for (domain, ip) in &work_bugs {
            println!("  {} {} {}", "🟢".green(), domain.white(), format!("({})", ip).cyan());
        }
        println!();
    } else {
        println!("{}", "Tidak ada working bug ditemukan".yellow());
        println!();
    }
    
    if !fail_bugs.is_empty() {
        println!(
            "{}",
            format!("❌ Failed Tests ({}):", fail_bugs.len()).red().bold()
        );
        for (domain, ip) in &fail_bugs {
            println!("  {} {} {}", "🔴".red(), domain.white(), format!("({})", ip).cyan());
        }
        println!();
    }
    
    ui::print_separator();
    println!("{}", "Statistik:".white().bold());
    println!(
        "  {}: {}/{} ({}%)",
        "Scanned".blue(),
        total,
        total,
        100
    );
    println!(
        "  {}: {} | {}: {}",
        "CF Found".blue(),
        (work_bugs.len() + fail_bugs.len()).to_string().green(),
        "Non-CF".blue(),
        skipped.to_string().yellow()
    );
    println!("  {}: {}s", "Waktu".blue(), elapsed.to_string().cyan());
    println!();
    
    // Export option
    if !work_bugs.is_empty() || !fail_bugs.is_empty() {
        print!("Export hasil? (y/n): ");
        io::stdout().flush()?;
        
        let mut export_choice = String::new();
        io::stdin().read_line(&mut export_choice)?;
        
        if export_choice.trim().to_lowercase() == "y" {
            export_results(&target_domain, &work_bugs, &fail_bugs, elapsed)?;
        }
    }
    
    println!();
    print!("Tekan Enter untuk kembali ke menu...");
    io::stdout().flush()?;
    let mut _dummy = String::new();
    io::stdin().read_line(&mut _dummy)?;
    
    Ok(())
}

fn export_results(
    domain: &str,
    work_bugs: &[(String, String)],
    fail_bugs: &[(String, String)],
    elapsed: u64,
) -> Result<()> {
    let home = dirs::home_dir().unwrap_or_else(|| std::path::PathBuf::from("."));
    let timestamp = chrono::Local::now().format("%Y%m%d-%H%M%S");
    let filename = format!("bug-{}-{}.txt", domain, timestamp);
    let filepath = home.join(&filename);
    
    let mut content = String::new();
    content.push_str("# InjectTools Scan Results\n");
    content.push_str("# Created by: t.me/hoshiyomi_id\n");
    content.push_str(&format!("# Domain: {}\n", domain));
    content.push_str(&format!("# Date: {}\n", chrono::Local::now()));
    content.push_str(&format!("# Scan time: {}s\n", elapsed));
    content.push_str("\n");
    
    if !work_bugs.is_empty() {
        content.push_str(&format!("=== WORKING ({}) ===\n", work_bugs.len()));
        for (domain, ip) in work_bugs {
            content.push_str(&format!("✅ {} {}\n", domain, ip));
        }
        content.push_str("\n");
    }
    
    if !fail_bugs.is_empty() {
        content.push_str(&format!("=== FAILED ({}) ===\n", fail_bugs.len()));
        for (domain, ip) in fail_bugs {
            content.push_str(&format!("❌ {} {}\n", domain, ip));
        }
    }
    
    std::fs::write(&filepath, content)?;
    
    println!();
    println!(
        "{}",
        format!("✅ Tersimpan: {}", filepath.display())
            .green()
            .bold()
    );
    std::thread::sleep(Duration::from_secs(2));
    
    Ok(())
}