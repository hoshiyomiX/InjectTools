mod config;
mod scanner;
mod dns;
mod wordlist;
mod ui;

use anyhow::Result;
use clap::Parser;
use colored::*;
use std::io::{self, Write};

#[derive(Parser, Debug)]
#[command(name = "InjectTools")]
#[command(author = "t.me/hoshiyomi_id")]
#[command(version = "1.1.0")]
#[command(about = "Bug Inject Scanner for Cloudflare Subdomains", long_about = None)]
struct Args {
    /// Target host (tunnel/proxy domain)
    #[arg(short, long)]
    target: Option<String>,

    /// Domain to scan
    #[arg(short, long)]
    domain: Option<String>,

    /// Test single subdomain
    #[arg(short, long)]
    subdomain: Option<String>,

    /// Wordlist file path
    #[arg(short, long)]
    wordlist: Option<String>,

    /// Timeout in seconds
    #[arg(long, default_value = "10")]
    timeout: u64,

    /// Skip interactive mode
    #[arg(long)]
    non_interactive: bool,
}

#[tokio::main]
async fn main() -> Result<()> {
    let args = Args::parse();

    // Load or create config
    let mut config = config::Config::load().unwrap_or_else(|_| {
        config::Config::default()
    });

    // Override dengan args jika ada
    if let Some(target) = args.target {
        config.target_host = target;
    }
    if let Some(domain) = args.domain {
        config.default_domain = domain;
    }
    if let Some(subdomain) = args.subdomain {
        config.default_subdomain = subdomain.clone();
        // Test single subdomain mode
        return test_single_subdomain(&config, &subdomain).await;
    }
    config.timeout = args.timeout;

    if args.non_interactive {
        // Direct scan mode
        if config.target_host.is_empty() || config.default_domain.is_empty() {
            eprintln!("{}", "Error: Target host and domain required in non-interactive mode".red().bold());
            std::process::exit(1);
        }
        return scanner::full_scan(&config).await;
    }

    // Interactive mode - check if first time setup needed
    if config.target_host.is_empty() {
        config = first_time_setup()?;
        config.save()?;
    }

    // Main menu loop
    main_menu(config).await
}

fn first_time_setup() -> Result<config::Config> {
    ui::clear_screen();
    ui::print_header("SETUP AWAL");
    println!();
    println!("{}", "Selamat datang di InjectTools!".white());
    println!("{}", "Created by: t.me/hoshiyomi_id".cyan());
    println!();
    println!("{}", "Mari kita atur konfigurasi default kamu.".blue());
    println!();

    // Target Host
    ui::print_separator();
    println!("{}", "1. Setup Target Host".white().bold());
    ui::print_separator();
    println!();
    println!("{}", "Ini adalah domain tunnel/proxy kamu tempat bug inject akan connect.".blue());
    println!("{}", "Contoh: your-tunnel.com, proxy.example.com".yellow());
    println!();
    
    let target_host = loop {
        print!("{}", "Masukkan target host: ".white());
        io::stdout().flush()?;
        let mut input = String::new();
        io::stdin().read_line(&mut input)?;
        let input = input.trim();
        if !input.is_empty() {
            break input.to_string();
        }
        println!("{}", "Target host tidak boleh kosong!".red());
    };
    
    println!("{}", format!("✓ Target host diset: {}", target_host).green());
    println!();
    std::thread::sleep(std::time::Duration::from_secs(1));

    // Default Subdomain
    ui::print_separator();
    println!("{}", "2. Setup Default Subdomain".white().bold());
    ui::print_separator();
    println!();
    println!("{}", "Subdomain default untuk quick test.".blue());
    println!("{}", "Contoh: cdn.example.com, api.target.com".yellow());
    println!();
    
    let default_subdomain = loop {
        print!("{}", "Masukkan default subdomain: ".white());
        io::stdout().flush()?;
        let mut input = String::new();
        io::stdin().read_line(&mut input)?;
        let input = input.trim();
        if !input.is_empty() {
            break input.to_string();
        }
        println!("{}", "Default subdomain tidak boleh kosong!".red());
    };
    
    println!("{}", format!("✓ Default subdomain diset: {}", default_subdomain).green());
    println!();

    // Auto-extract domain
    let default_domain = config::extract_domain(&default_subdomain);
    println!("{}", format!("📌 Domain auto-detected: {}", default_domain).blue().bold());
    println!("{}", "   (extracted from subdomain)".yellow());
    println!();
    std::thread::sleep(std::time::Duration::from_secs(2));

    // Wordlist setup
    ui::print_separator();
    println!("{}", "3. Setup Wordlist".white().bold());
    ui::print_separator();
    println!();
    
    let active_wordlist = wordlist::auto_detect_wordlist();
    match &active_wordlist {
        Some(path) => {
            let count = wordlist::count_lines(path)?;
            println!("{}", "✅ Wordlist terdeteksi!".green().bold());
            println!("   {}: {}", "File".white(), path.display().to_string().green());
            println!("   {}: {} patterns", "Lines".white(), count.to_string().cyan());
            println!();
        }
        None => {
            println!("{}", "Tidak ada wordlist terdeteksi.".yellow());
            println!();
            println!("{}", "Untuk hasil scan lebih baik, download wordlist komprehensif".blue());
            println!("{}", "dari SecLists (5K sampai 110K subdomain).".blue());
            println!();
            println!("{}", "Saat ini: Embedded wordlist (coverage terbatas)".yellow());
            println!();
        }
    }

    // Save config
    ui::print_separator();
    println!("{}", "Setup Selesai!".white().bold());
    ui::print_separator();
    println!();
    
    let config = config::Config {
        target_host,
        default_subdomain,
        default_domain,
        timeout: 10,
        active_wordlist: active_wordlist.map(|p| p.to_string_lossy().to_string()),
    };
    
    println!("{}", "Konfigurasi kamu:".blue());
    println!("  {}: {}", "Target Host".white(), config.target_host.green());
    println!("  {}: {}", "Default Subdomain".white(), config.default_subdomain.green());
    println!("  {}: {}", "Default Domain".white(), config.default_domain.green());
    println!();
    
    config.save()?;
    println!("{}", "✅ Konfigurasi disimpan".green());
    println!();
    println!("{}", "Kamu bisa ubah setting ini kapan saja dari Menu Settings.".blue());
    println!();
    
    print!("Tekan Enter untuk lanjut...");
    io::stdout().flush()?;
    let mut _dummy = String::new();
    io::stdin().read_line(&mut _dummy)?;
    
    Ok(config)
}

async fn main_menu(mut config: config::Config) -> Result<()> {
    loop {
        ui::clear_screen();
        ui::print_header("InjectTools v1.1");
        println!();
        println!("{}", "Created by: t.me/hoshiyomi_id".cyan());
        println!();
        println!("{}: {}", "Target".blue(), config.target_host.green());
        
        match &config.active_wordlist {
            Some(path) => {
                let name = std::path::Path::new(path)
                    .file_name()
                    .and_then(|n| n.to_str())
                    .unwrap_or("unknown");
                let count = wordlist::count_lines(std::path::Path::new(path)).unwrap_or(0);
                println!("{}: {} ({} lines)", 
                    "Wordlist".blue(), 
                    name.green(), 
                    count.to_string().cyan()
                );
            }
            None => {
                println!("{}: {}", "Wordlist".blue(), "Embedded (terbatas)".yellow());
            }
        }
        
        println!();
        ui::print_separator();
        println!();
        println!("{}", "Menu:".white().bold());
        println!();
        println!("  {}. Test Single Subdomain", "1".white());
        println!("  {}. Full Cloudflare Scan", "2".white());
        println!("  {}. Wordlist Manager", "3".white());
        println!("  {}. Settings", "4".white());
        println!("  {}. Exit", "5".white());
        println!();
        
        print!("Pilih [1-5]: ");
        io::stdout().flush()?;
        
        let mut choice = String::new();
        io::stdin().read_line(&mut choice)?;
        
        match choice.trim() {
            "1" => {
                test_single_subdomain(&config, &config.default_subdomain.clone()).await?;
            }
            "2" => {
                scanner::full_scan(&config).await?;
            }
            "3" => {
                wordlist::wordlist_menu(&mut config)?;
            }
            "4" => {
                settings_menu(&mut config)?;
            }
            "5" => {
                ui::clear_screen();
                println!("{}", "Sampai jumpa!".green().bold());
                println!("{}", "Created by: t.me/hoshiyomi_id".cyan());
                println!();
                break;
            }
            _ => {
                println!("{}", "Pilihan tidak valid!".red().bold());
                std::thread::sleep(std::time::Duration::from_secs(1));
            }
        }
    }
    Ok(())
}

async fn test_single_subdomain(config: &config::Config, subdomain: &str) -> Result<()> {
    ui::clear_screen();
    ui::print_header("Test Single Subdomain");
    println!();
    
    println!("{}: {}", "Target Host".blue(), config.target_host.green());
    println!("{}: {}", "Testing".blue(), subdomain.yellow());
    println!();
    
    println!("{}", "🔍 Resolving DNS...".cyan());
    
    let result = scanner::test_subdomain(config, subdomain).await?;
    
    println!();
    if result.works {
        println!("{}", "╔═══════════════════════════════════════════════════════╗".green());
        println!("{}", "║           ✅ BUG INJECT WORKING!                      ║".green());
        println!("{}", "╚═══════════════════════════════════════════════════════╝".green());
        println!();
        println!("   {}: {}", "Subdomain".white(), result.subdomain.green());
        println!("   {}: {}", "IP".white(), result.ip.green());
        println!("   {}: {}", "Target".white(), config.target_host.green());
    } else {
        println!("{}", "╔═══════════════════════════════════════════════════════╗".red());
        println!("{}", "║            ❌ BUG INJECT FAILED                       ║".red());
        println!("{}", "╚═══════════════════════════════════════════════════════╝".red());
        println!();
        println!("   {}: {}", "Subdomain".white(), result.subdomain.red());
        println!("   {}: {}", "IP".white(), result.ip.red());
        println!("   {}: {}", "Alasan".white(), result.reason.yellow());
    }
    
    println!();
    print!("Tekan Enter untuk lanjut...");
    io::stdout().flush()?;
    let mut _dummy = String::new();
    io::stdin().read_line(&mut _dummy)?;
    
    Ok(())
}

fn settings_menu(config: &mut config::Config) -> Result<()> {
    loop {
        ui::clear_screen();
        ui::print_header("SETTINGS");
        println!();
        println!("{}", "Konfigurasi Saat Ini:".white().bold());
        println!("  {}: {}", "Target Host".blue(), config.target_host.green());
        println!("  {}: {}", "Default Domain".blue(), config.default_domain.green());
        println!("  {}: {}", "Default Subdomain".blue(), config.default_subdomain.green());
        println!("  {}: {}s", "Timeout".blue(), config.timeout.to_string().cyan());
        println!();
        println!("  {}. Change Target Host", "1".white());
        println!("  {}. Change Default Domain", "2".white());
        println!("  {}. Change Default Subdomain", "3".white());
        println!("  {}. Change Timeout", "4".white());
        println!("  {}. Re-run First Time Setup", "5".white());
        println!("  {}. Back", "6".white());
        println!();
        
        print!("Pilih [1-6]: ");
        io::stdout().flush()?;
        
        let mut choice = String::new();
        io::stdin().read_line(&mut choice)?;
        
        match choice.trim() {
            "1" => {
                print!("Target host baru: ");
                io::stdout().flush()?;
                let mut input = String::new();
                io::stdin().read_line(&mut input)?;
                let input = input.trim();
                if !input.is_empty() {
                    config.target_host = input.to_string();
                    config.save()?;
                }
            }
            "2" => {
                print!("Default domain baru: ");
                io::stdout().flush()?;
                let mut input = String::new();
                io::stdin().read_line(&mut input)?;
                let input = input.trim();
                if !input.is_empty() {
                    config.default_domain = input.to_string();
                    config.save()?;
                }
            }
            "3" => {
                print!("Default subdomain baru: ");
                io::stdout().flush()?;
                let mut input = String::new();
                io::stdin().read_line(&mut input)?;
                let input = input.trim();
                if !input.is_empty() {
                    config.default_subdomain = input.to_string();
                    config.default_domain = config::extract_domain(input);
                    config.save()?;
                }
            }
            "4" => {
                print!("Timeout (detik): ");
                io::stdout().flush()?;
                let mut input = String::new();
                io::stdin().read_line(&mut input)?;
                if let Ok(timeout) = input.trim().parse() {
                    config.timeout = timeout;
                    config.save()?;
                }
            }
            "5" => {
                *config = first_time_setup()?;
                return Ok(());
            }
            "6" => return Ok(()),
            _ => {}
        }
    }
}