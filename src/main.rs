use anyhow::{Context, Result};
use colored::*;
use dialoguer::{Confirm, Input, Select};
use indicatif::{ProgressBar, ProgressStyle};
use ipnetwork::Ipv4Network;
use reqwest::Client;
use serde::{Deserialize, Serialize};
use std::collections::HashSet;
use std::fs;
use std::net::Ipv4Addr;
use std::path::PathBuf;
use std::str::FromStr;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::time::Duration;
use tokio::time::timeout;
use trust_dns_resolver::config::*;
use trust_dns_resolver::TokioAsyncResolver;

// Cloudflare IP ranges
const CF_RANGES: &[&str] = &[
    "104.16.0.0/13",
    "104.24.0.0/14",
    "172.64.0.0/13",
    "173.245.48.0/20",
    "162.158.0.0/15",
    "141.101.64.0/18",
];

// Embedded wordlist
const EMBEDDED_WORDLIST: &str = include_str!("wordlist.txt");

#[derive(Debug, Serialize, Deserialize, Clone)]
struct Config {
    target_host: String,
    default_subdomain: String,
    default_domain: String,
    timeout: u64,
    active_wordlist: String,
}

impl Default for Config {
    fn default() -> Self {
        Self {
            target_host: String::new(),
            default_subdomain: String::new(),
            default_domain: String::new(),
            timeout: 10,
            active_wordlist: "embedded".to_string(),
        }
    }
}

struct App {
    config: Config,
    config_path: PathBuf,
    wordlist_dir: PathBuf,
    resolver: TokioAsyncResolver,
    client: Client,
    cf_networks: Vec<Ipv4Network>,
    cancelled: Arc<AtomicBool>,
}

impl App {
    async fn new() -> Result<Self> {
        let config_dir = dirs::home_dir()
            .context("Failed to get home directory")?
            .join(".config")
            .join("injecttools");
        
        fs::create_dir_all(&config_dir)?;
        
        let config_path = config_dir.join("config.json");
        let wordlist_dir = config_dir.join("wordlists");
        
        fs::create_dir_all(&wordlist_dir)?;
        
        let resolver = TokioAsyncResolver::tokio(
            ResolverConfig::default(),
            ResolverOpts::default(),
        );
        
        let client = Client::builder()
            .timeout(Duration::from_secs(10))
            .danger_accept_invalid_certs(true)
            .build()?;
        
        let cf_networks = CF_RANGES
            .iter()
            .filter_map(|r| Ipv4Network::from_str(r).ok())
            .collect();
        
        let config = if config_path.exists() {
            let data = fs::read_to_string(&config_path)?;
            serde_json::from_str(&data).unwrap_or_default()
        } else {
            Config::default()
        };
        
        let cancelled = Arc::new(AtomicBool::new(false));
        let cancelled_clone = cancelled.clone();
        
        ctrlc::set_handler(move || {
            cancelled_clone.store(true, Ordering::SeqCst);
            println!("\n{}", "⚠️  Scan cancelled by user (Ctrl+C)".yellow().bold());
        })?;
        
        Ok(Self {
            config,
            config_path,
            wordlist_dir,
            resolver,
            client,
            cf_networks,
            cancelled,
        })
    }
    
    fn save_config(&self) -> Result<()> {
        let data = serde_json::to_string_pretty(&self.config)?;
        fs::write(&self.config_path, data)?;
        println!("{}", "✅ Configuration saved".green());
        Ok(())
    }
    
    fn print_header(&self, title: &str) {
        let width = 60;
        println!("{}", "═".repeat(width).cyan());
        let padding = (width - title.len()) / 2;
        println!("{}{}{}", " ".repeat(padding), title.white().bold(), " ".repeat(padding));
        println!("{}", "═".repeat(width).cyan());
    }
    
    fn extract_domain(&self, subdomain: &str) -> String {
        let parts: Vec<&str> = subdomain.trim_start_matches("http://")
            .trim_start_matches("https://")
            .split('.')
            .collect();
        
        if parts.len() >= 2 {
            format!("{}.{}", parts[parts.len() - 2], parts[parts.len() - 1])
        } else {
            subdomain.to_string()
        }
    }
    
    async fn first_time_setup(&mut self) -> Result<()> {
        self.print_header("SETUP AWAL");
        println!();
        println!("{}", "Selamat datang di InjectTools!".white());
        println!("{}", "Created by: t.me/hoshiyomi_id".cyan());
        println!();
        println!("{}", "Mari kita atur konfigurasi default kamu.".blue());
        println!();
        
        // Target Host
        println!("{}", "━".repeat(60).cyan());
        println!("{}", "1. Setup Target Host".white().bold());
        println!("{}", "━".repeat(60).cyan());
        println!();
        println!("{}", "Ini adalah domain tunnel/proxy kamu tempat bug inject akan connect.".blue());
        println!("{}", "Contoh: your-tunnel.com, proxy.example.com".yellow());
        println!();
        
        self.config.target_host = Input::new()
            .with_prompt("Masukkan target host")
            .interact_text()?;
        
        println!("{}", format!("✓ Target host diset: {}", self.config.target_host).green());
        println!();
        
        // Default Subdomain
        println!("{}", "━".repeat(60).cyan());
        println!("{}", "2. Setup Default Subdomain".white().bold());
        println!("{}", "━".repeat(60).cyan());
        println!();
        println!("{}", "Subdomain default untuk quick test.".blue());
        println!("{}", "Contoh: cdn.example.com, api.target.com".yellow());
        println!();
        
        self.config.default_subdomain = Input::new()
            .with_prompt("Masukkan default subdomain")
            .interact_text()?;
        
        self.config.default_domain = self.extract_domain(&self.config.default_subdomain);
        
        println!("{}", format!("✓ Default subdomain diset: {}", self.config.default_subdomain).green());
        println!("{}", format!("📌 Domain auto-detected: {}", self.config.default_domain).blue());
        println!();
        
        // Wordlist Setup
        println!("{}", "━".repeat(60).cyan());
        println!("{}", "3. Setup Wordlist".white().bold());
        println!("{}", "━".repeat(60).cyan());
        println!();
        
        self.auto_detect_wordlist();
        
        if self.config.active_wordlist == "embedded" {
            println!("{}", "Tidak ada wordlist terdeteksi.".yellow());
            println!();
            println!("{}", "Untuk hasil scan lebih baik, download wordlist komprehensif".blue());
            println!("{}", "dari SecLists (5K sampai 110K subdomain).".blue());
            println!();
            
            if Confirm::new()
                .with_prompt("Download wordlist sekarang?")
                .interact()?
            {
                self.download_wordlist_menu().await?;
                self.auto_detect_wordlist();
            }
        }
        
        self.save_config()?;
        println!();
        println!("{}", "Setup selesai! Konfigurasi kamu:".blue());
        self.print_config();
        
        Input::<String>::new()
            .with_prompt("Tekan Enter untuk lanjut")
            .allow_empty(true)
            .interact_text()?;
        
        Ok(())
    }
    
    fn auto_detect_wordlist(&mut self) {
        if let Ok(entries) = fs::read_dir(&self.wordlist_dir) {
            let mut largest: Option<(PathBuf, usize)> = None;
            
            for entry in entries.flatten() {
                if let Ok(metadata) = entry.metadata() {
                    if metadata.is_file() {
                        if let Some(ext) = entry.path().extension() {
                            if ext == "txt" {
                                let size = metadata.len() as usize;
                                if largest.as_ref().map_or(true, |(_, s)| size > *s) {
                                    largest = Some((entry.path(), size));
                                }
                            }
                        }
                    }
                }
            }
            
            if let Some((path, _)) = largest {
                self.config.active_wordlist = path.to_string_lossy().to_string();
            }
        }
    }
    
    fn is_cloudflare(&self, ip: Ipv4Addr) -> bool {
        self.cf_networks.iter().any(|net| net.contains(ip))
    }
    
    async fn resolve_domain(&self, domain: &str) -> Option<Ipv4Addr> {
        match self.resolver.ipv4_lookup(domain).await {
            Ok(response) => response.iter().next().map(|ip| ip.0),
            Err(_) => None,
        }
    }
    
    async fn test_inject(&self, ip: Ipv4Addr) -> bool {
        let url = format!("https://{}/", self.config.target_host);
        
        match timeout(
            Duration::from_secs(self.config.timeout),
            self.client
                .get(&url)
                .resolve(&self.config.target_host, (ip.to_string().as_str(), 443).into())
                .send()
        ).await {
            Ok(Ok(_)) => true,
            _ => false,
        }
    }
    
    async fn test_single_subdomain(&self) -> Result<()> {
        self.print_header("Test Single Subdomain");
        println!();
        
        let subdomain: String = Input::new()
            .with_prompt("Masukkan subdomain")
            .default(self.config.default_subdomain.clone())
            .interact_text()?;
        
        println!();
        println!("{} {}", "Target Host:".blue(), self.config.target_host.green());
        println!("{} {}", "Testing:".blue(), subdomain.yellow());
        println!();
        println!("{}", "🔍 Resolving DNS...".cyan());
        
        match self.resolve_domain(&subdomain).await {
            Some(ip) => {
                println!("{} {}", "   IP Address:".white(), ip.to_string().blue());
                
                if self.is_cloudflare(ip) {
                    println!("{} {}", "   Provider:".white(), "☁️  Cloudflare".cyan());
                } else {
                    println!("{} {}", "   Provider:".white(), "⚠️  Non-Cloudflare".yellow());
                }
                
                println!();
                println!("{}", "🧪 Testing bug inject...".cyan());
                
                if self.test_inject(ip).await {
                    println!();
                    println!("{}", "╔═══════════════════════════════════════════════════════╗".green());
                    println!("{}", "║           ✅ BUG INJECT WORKING!                      ║".green());
                    println!("{}", "╚═══════════════════════════════════════════════════════╝".green());
                    println!();
                    println!("{} {}", "   Subdomain:".white(), subdomain.green());
                    println!("{} {}", "   IP:".white(), ip.to_string().green());
                    println!("{} {}", "   Target:".white(), self.config.target_host.green());
                } else {
                    println!();
                    println!("{}", "╔═══════════════════════════════════════════════════════╗".red());
                    println!("{}", "║            ❌ BUG INJECT FAILED                       ║".red());
                    println!("{}", "╚═══════════════════════════════════════════════════════╝".red());
                    println!();
                    println!("{} {}", "   Subdomain:".white(), subdomain.red());
                    println!("{} {}", "   IP:".white(), ip.to_string().red());
                    println!("{} {}", "   Alasan:".white(), "Koneksi gagal atau diblokir".yellow());
                }
            }
            None => {
                println!("{}", format!("❌ IP tidak ditemukan untuk {}", subdomain).red().bold());
            }
        }
        
        println!();
        Input::<String>::new()
            .with_prompt("Tekan Enter untuk lanjut")
            .allow_empty(true)
            .interact_text()?;
        
        Ok(())
    }
    
    fn get_wordlist(&self) -> Result<Vec<String>> {
        let content = if self.config.active_wordlist == "embedded" {
            EMBEDDED_WORDLIST.to_string()
        } else {
            fs::read_to_string(&self.config.active_wordlist)?
        };
        
        Ok(content
            .lines()
            .filter(|l| !l.trim().is_empty())
            .map(|l| l.trim().to_string())
            .collect())
    }
    
    async fn full_scan(&mut self) -> Result<()> {
        self.print_header("Cloudflare Bug Scanner");
        println!();
        
        let target_domain: String = Input::new()
            .with_prompt("Masukkan target domain")
            .default(self.config.default_domain.clone())
            .interact_text()?;
        
        println!();
        println!("{} {}", "Domain:".blue(), target_domain.green());
        println!("{} {}", "Target:".blue(), self.config.target_host.green());
        println!("{} {}", "Filter:".blue(), "☁️  Cloudflare only".cyan());
        println!();
        
        println!("{}", "📚 Loading wordlist...".cyan());
        let wordlist = self.get_wordlist()?;
        let total = wordlist.len();
        println!("{}", format!("✅ Loaded {} patterns", total).green());
        println!();
        
        let pb = ProgressBar::new(total as u64);
        pb.set_style(
            ProgressStyle::default_bar()
                .template("{spinner:.green} [{elapsed_precise}] [{wide_bar:.cyan/blue}] {pos}/{len} ({eta})")
                .unwrap()
                .progress_chars("#>-"),
        );
        
        let mut working: Vec<(String, Ipv4Addr)> = Vec::new();
        let mut failed: Vec<(String, Ipv4Addr)> = Vec::new();
        let mut skipped = 0;
        
        self.cancelled.store(false, Ordering::SeqCst);
        
        for sub in wordlist {
            if self.cancelled.load(Ordering::SeqCst) {
                break;
            }
            
            let domain = format!("{}.{}", sub, target_domain);
            
            if let Some(ip) = self.resolve_domain(&domain).await {
                if !self.is_cloudflare(ip) {
                    skipped += 1;
                    pb.inc(1);
                    continue;
                }
                
                if self.test_inject(ip).await {
                    working.push((domain, ip));
                } else {
                    failed.push((domain, ip));
                }
            }
            
            pb.inc(1);
            tokio::time::sleep(Duration::from_millis(50)).await;
        }
        
        pb.finish_and_clear();
        
        println!();
        self.print_header("HASIL SCAN");
        println!();
        
        if self.cancelled.load(Ordering::SeqCst) {
            println!("{}", "Catatan: Scan dibatalkan (hasil parsial)".yellow().bold());
            println!();
        }
        
        if !working.is_empty() {
            println!("{}", format!("✅ Working Bugs ({}):", working.len()).green().bold());
            for (domain, ip) in &working {
                println!("  {} {} {}", "🟢".green(), domain.white(), format!("({})", ip).cyan());
            }
            println!();
        } else {
            println!("{}", "Tidak ada working bug ditemukan".yellow());
            println!();
        }
        
        if !failed.is_empty() {
            println!("{}", format!("❌ Failed Tests ({}):", failed.len()).red().bold());
            for (domain, ip) in &failed {
                println!("  {} {} {}", "🔴".red(), domain.white(), format!("({})", ip).cyan());
            }
            println!();
        }
        
        println!("{}", "─".repeat(60).cyan());
        println!("{}", "Statistik:".white().bold());
        println!("{} {}", "  Scanned:".blue(), format!("{}/{}", pb.position(), total).cyan());
        println!(
            "{} {} | {} {}",
            "  CF Found:".blue(),
            format!("{}", working.len() + failed.len()).green(),
            "Non-CF:".blue(),
            format!("{}", skipped).yellow()
        );
        println!();
        
        if !working.is_empty() || !failed.is_empty() {
            if Confirm::new().with_prompt("Export hasil?").interact()? {
                let filename = format!("bug-{}-{}.txt", target_domain, chrono::Local::now().format("%Y%m%d-%H%M%S"));
                let output_path = dirs::home_dir()
                    .context("Failed to get home dir")?
                    .join(&filename);
                
                let mut output = String::new();
                output.push_str("# InjectTools Scan Results\n");
                output.push_str("# Created by: t.me/hoshiyomi_id\n");
                output.push_str(&format!("# Domain: {}\n", target_domain));
                output.push_str(&format!("# Date: {}\n\n", chrono::Local::now()));
                
                if !working.is_empty() {
                    output.push_str(&format!("=== WORKING ({}) ===\n", working.len()));
                    for (domain, ip) in &working {
                        output.push_str(&format!("✅ {} {}\n", domain, ip));
                    }
                    output.push_str("\n");
                }
                
                if !failed.is_empty() {
                    output.push_str(&format!("=== FAILED ({}) ===\n", failed.len()));
                    for (domain, ip) in &failed {
                        output.push_str(&format!("❌ {} {}\n", domain, ip));
                    }
                }
                
                fs::write(&output_path, output)?;
                println!("{}", format!("✅ Tersimpan: {}", output_path.display()).green().bold());
            }
        }
        
        println!();
        Input::<String>::new()
            .with_prompt("Tekan Enter untuk kembali ke menu")
            .allow_empty(true)
            .interact_text()?;
        
        Ok(())
    }
    
    async fn download_wordlist_menu(&self) -> Result<()> {
        let options = vec![
            "Small - 5,000 subdomains (~90 KB)",
            "Medium - 20,000 subdomains (~350 KB)",
            "Large - 110,000 subdomains (~2 MB)",
            "Back",
        ];
        
        let selection = Select::new()
            .with_prompt("Download Wordlist (from SecLists)")
            .items(&options)
            .interact()?;
        
        match selection {
            0 => {
                self.download_wordlist(
                    "small",
                    "https://raw.githubusercontent.com/danielmiessler/SecLists/master/Discovery/DNS/subdomains-top1million-5000.txt",
                    "seclists-5k.txt",
                ).await?
            }
            1 => {
                self.download_wordlist(
                    "medium",
                    "https://raw.githubusercontent.com/danielmiessler/SecLists/master/Discovery/DNS/subdomains-top1million-20000.txt",
                    "seclists-20k.txt",
                ).await?
            }
            2 => {
                self.download_wordlist(
                    "large",
                    "https://raw.githubusercontent.com/danielmiessler/SecLists/master/Discovery/DNS/subdomains-top1million-110000.txt",
                    "seclists-110k.txt",
                ).await?
            }
            _ => {}
        }
        
        Ok(())
    }
    
    async fn download_wordlist(&self, size: &str, url: &str, filename: &str) -> Result<()> {
        println!();
        println!("{}", format!("📥 Downloading {} wordlist...", size).cyan());
        
        let filepath = self.wordlist_dir.join(filename);
        
        let response = self.client.get(url).send().await?;
        let content = response.text().await?;
        
        fs::write(&filepath, content)?;
        
        let line_count = fs::read_to_string(&filepath)?
            .lines()
            .count();
        
        println!("{}", format!("✅ Berhasil! Baris: {}", line_count).green().bold());
        println!();
        
        Ok(())
    }
    
    fn print_config(&self) {
        println!("  {} {}", "Target Host:".white(), self.config.target_host.green());
        println!("  {} {}", "Default Subdomain:".white(), self.config.default_subdomain.green());
        println!("  {} {}", "Default Domain:".white(), self.config.default_domain.green());
        
        if self.config.active_wordlist == "embedded" {
            println!("  {} {}", "Wordlist:".white(), "Embedded (terbatas)".yellow());
        } else {
            let filename = PathBuf::from(&self.config.active_wordlist)
                .file_name()
                .and_then(|n| n.to_str())
                .unwrap_or("unknown");
            println!("  {} {}", "Wordlist:".white(), filename.green());
        }
    }
    
    async fn settings_menu(&mut self) -> Result<()> {
        loop {
            self.print_header("SETTINGS");
            println!();
            println!("{}", "Konfigurasi Saat Ini:".white().bold());
            self.print_config();
            println!();
            
            let options = vec![
                "Change Target Host",
                "Change Default Domain",
                "Change Default Subdomain",
                "Change Timeout",
                "Reset to Embedded Wordlist",
                "Re-run First Time Setup",
                "Back",
            ];
            
            let selection = Select::new()
                .items(&options)
                .interact()?;
            
            match selection {
                0 => {
                    self.config.target_host = Input::new()
                        .with_prompt("Target host baru")
                        .interact_text()?;
                    self.save_config()?;
                }
                1 => {
                    self.config.default_domain = Input::new()
                        .with_prompt("Default domain baru")
                        .interact_text()?;
                    self.save_config()?;
                }
                2 => {
                    self.config.default_subdomain = Input::new()
                        .with_prompt("Default subdomain baru")
                        .interact_text()?;
                    self.config.default_domain = self.extract_domain(&self.config.default_subdomain);
                    self.save_config()?;
                    println!("{}", format!("📌 Domain auto-updated: {}", self.config.default_domain).blue());
                }
                3 => {
                    self.config.timeout = Input::new()
                        .with_prompt("Timeout (detik)")
                        .interact_text()?;
                    self.save_config()?;
                }
                4 => {
                    self.config.active_wordlist = "embedded".to_string();
                    self.save_config()?;
                }
                5 => {
                    self.first_time_setup().await?;
                }
                _ => break,
            }
        }
        
        Ok(())
    }
    
    async fn main_menu(&mut self) -> Result<()> {
        loop {
            self.print_header("InjectTools v1.1");
            println!();
            println!("{}", "Created by: t.me/hoshiyomi_id".cyan());
            println!();
            println!("{} {}", "Target:".blue(), self.config.target_host.green());
            
            if self.config.active_wordlist == "embedded" {
                println!("{} {}", "Wordlist:".blue(), "Embedded (terbatas)".yellow());
            } else {
                let filename = PathBuf::from(&self.config.active_wordlist)
                    .file_name()
                    .and_then(|n| n.to_str())
                    .unwrap_or("unknown");
                println!("{} {}", "Wordlist:".blue(), filename.green());
            }
            
            println!();
            println!("{}", "─".repeat(60).cyan());
            println!();
            
            let options = vec![
                "Test Single Subdomain",
                "Full Cloudflare Scan",
                "Download Wordlist (from SecLists)",
                "Settings",
                "Exit",
            ];
            
            let selection = Select::new()
                .with_prompt("Menu")
                .items(&options)
                .interact()?;
            
            match selection {
                0 => self.test_single_subdomain().await?,
                1 => self.full_scan().await?,
                2 => self.download_wordlist_menu().await?,
                3 => self.settings_menu().await?,
                4 => {
                    println!();
                    println!("{}", "Sampai jumpa!".green().bold());
                    println!("{}", "Created by: t.me/hoshiyomi_id".cyan());
                    println!();
                    break;
                }
                _ => {}
            }
        }
        
        Ok(())
    }
}

#[tokio::main]
async fn main() -> Result<()> {
    let mut app = App::new().await?;
    
    if app.config.target_host.is_empty() {
        app.first_time_setup().await?;
    }
    
    app.main_menu().await?;
    
    Ok(())
}
