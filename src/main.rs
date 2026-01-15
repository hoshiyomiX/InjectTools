mod config;
mod scanner;
mod dns;
mod ui;
mod crtsh;
mod results;

use clap::Parser;
use colored::Colorize;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;

#[derive(Parser, Debug)]
#[command(name = "InjectTools")]
#[command(author = "hoshiyomi_id <t.me/hoshiyomi_id>")]
#[command(version = "2.3.0")]
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

    /// Fetch subdomains from crt.sh
    #[arg(long)]
    crtsh: bool,

    /// Timeout in seconds
    #[arg(long, default_value = "10")]
    timeout: u64,

    /// Non-interactive mode
    #[arg(long)]
    non_interactive: bool,

    /// View exported results
    #[arg(long)]
    view_results: bool,
}

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    let args = Args::parse();

    // Setup signal handler
    let running = Arc::new(AtomicBool::new(true));
    let r = running.clone();
    
    ctrlc::set_handler(move || {
        eprintln!("\n\n{}", "⚠️  Scan dibatalkan oleh user (Ctrl+C)".yellow());
        eprintln!("{}", "Menyimpan hasil scan..." .cyan());
        r.store(false, Ordering::SeqCst);
    })?;

    // View results mode
    if args.view_results {
        results::view_results()?;
        return Ok(());
    }

    // Load or create config
    let mut config = config::Config::load_or_create()?;

    // Non-interactive mode
    if args.non_interactive {
        if let Some(target) = args.target {
            config.target_host = target;
            config.save()?;

            if args.crtsh {
                if let Some(domain) = args.domain {
                    // Fetch from crt.sh and test
                    ui::print_header("CRTSH SUBDOMAIN DISCOVERY");
                    let subdomains = crtsh::fetch_subdomains(&domain).await?;
                    println!("\n{} subdomains dari crt.sh\n", subdomains.len());
                    
                    let results = scanner::batch_test(
                        &config.target_host,
                        &subdomains,
                        args.timeout,
                        running.clone(),
                    ).await?;
                    
                    results::export_results(&results, &domain)?;
                } else {
                    eprintln!("{}", "Error: --domain required untuk --crtsh".red());
                    std::process::exit(1);
                }
            } else if let Some(subdomain) = args.subdomain {
                // Single test
                scanner::test_single(&config.target_host, &subdomain, args.timeout).await?;
            } else {
                eprintln!("{}", "Error: Gunakan --subdomain atau --crtsh --domain".red());
                std::process::exit(1);
            }
            
            return Ok(());
        } else {
            eprintln!("{}", "Error: --target required untuk non-interactive mode".red());
            std::process::exit(1);
        }
    }

    // Interactive mode
    loop {
        ui::clear_screen();
        ui::print_header("INJECTTOOLS v2.3");
        
        println!("\n{}", "MAIN MENU".bold());
        println!("{}" , "━".repeat(50).cyan());
        println!("\n1. {} Test Target Host", "🎯".cyan());
        println!("2. {} Test Single Subdomain", "🔍".cyan());
        println!("3. {} Fetch & Test dari crt.sh", "🌐".cyan());
        println!("4. {} View Exported Results", "📊".cyan());
        println!("5. {} Settings", "⚙️".cyan());
        println!("6. {} Exit", "🚪".red());
        println!("\n{}", "━".repeat(50).cyan());
        
        if !config.target_host.is_empty() {
            println!("\n{} {}", "Target:".bright_black(), config.target_host.green());
        }
        
        print!("\n{} ", "Pilih:".bold());
        let choice = ui::read_line();

        match choice.trim() {
            "1" => {
                ui::print_header("TEST TARGET HOST");
                print!("\nMasukkan target host: ");
                let target = ui::read_line();
                if !target.is_empty() {
                    scanner::test_target(&target, args.timeout).await?;
                    config.target_host = target;
                    config.save()?;
                }
                ui::pause();
            }
            "2" => {
                if config.target_host.is_empty() {
                    println!("\n{}", "⚠️  Set target host dulu di Settings!".yellow());
                    ui::pause();
                    continue;
                }
                
                ui::print_header("TEST SINGLE SUBDOMAIN");
                print!("\nMasukkan subdomain: ");
                let subdomain = ui::read_line();
                if !subdomain.is_empty() {
                    scanner::test_single(&config.target_host, &subdomain, args.timeout).await?;
                }
                ui::pause();
            }
            "3" => {
                if config.target_host.is_empty() {
                    println!("\n{}", "⚠️  Set target host dulu di Settings!".yellow());
                    ui::pause();
                    continue;
                }
                
                ui::print_header("CRTSH SUBDOMAIN DISCOVERY");
                print!("\nMasukkan domain (contoh: cloudflare.com): ");
                let domain = ui::read_line();
                if !domain.is_empty() {
                    println!("\n{}", "📡 Fetching subdomains dari crt.sh...".cyan());
                    match crtsh::fetch_subdomains(&domain).await {
                        Ok(subdomains) => {
                            println!("{} {} subdomains ditemukan\n", "✓".green(), subdomains.len());
                            
                            if subdomains.is_empty() {
                                println!("{}", "Tidak ada subdomain ditemukan".yellow());
                            } else {
                                println!("{}", "Mulai testing...".cyan());
                                let results = scanner::batch_test(
                                    &config.target_host,
                                    &subdomains,
                                    args.timeout,
                                    running.clone(),
                                ).await?;
                                
                                results::export_results(&results, &domain)?;
                            }
                        }
                        Err(e) => {
                            println!("{} {}", "✗".red(), format!("Gagal fetch dari crt.sh: {}", e).red());
                        }
                    }
                }
                ui::pause();
            }
            "4" => {
                results::view_results()?;
                ui::pause();
            }
            "5" => {
                settings_menu(&mut config, args.timeout)?;
            }
            "6" => {
                println!("\n{}", "👋 Terima kasih telah menggunakan InjectTools!".green());
                break;
            }
            _ => {
                println!("\n{}", "❌ Pilihan tidak valid".red());
                ui::pause();
            }
        }
    }

    Ok(())
}

fn settings_menu(config: &mut config::Config, timeout: u64) -> anyhow::Result<()> {
    loop {
        ui::clear_screen();
        ui::print_header("SETTINGS");
        
        println!("\n{}", "CURRENT SETTINGS".bold());
        println!("{}" , "━".repeat(50).cyan());
        println!("\n{} {}", "Target Host:".bright_black(), 
                 if config.target_host.is_empty() { "(not set)".red() } else { config.target_host.green() });
        println!("{} {} seconds", "Timeout:".bright_black(), timeout.to_string().green());
        println!("\n{}" , "━".repeat(50).cyan());
        
        println!("\n1. Change Target Host");
        println!("2. Back to Main Menu");
        
        print!("\n{} ", "Pilih:".bold());
        let choice = ui::read_line();

        match choice.trim() {
            "1" => {
                print!("\nMasukkan target host baru: ");
                let target = ui::read_line();
                if !target.is_empty() {
                    config.target_host = target;
                    config.save()?;
                    println!("{}", "✓ Target host updated".green());
                    std::thread::sleep(std::time::Duration::from_secs(1));
                }
            }
            "2" => break,
            _ => {
                println!("\n{}", "❌ Pilihan tidak valid".red());
                ui::pause();
            }
        }
    }
    
    Ok(())
}