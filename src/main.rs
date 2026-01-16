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
use std::time::{Duration, Instant};
use std::sync::Mutex;

#[derive(Parser, Debug)]
#[command(name = "InjectTools")]
#[command(author = "hoshiyomi_id <t.me/hoshiyomi_id>")]
#[command(version = "2.4.0")]
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

// Cache untuk target status
struct TargetStatus {
    is_online: bool,
    last_check: Instant,
}

impl TargetStatus {
    fn new() -> Self {
        Self {
            is_online: false,
            last_check: Instant::now() - Duration::from_secs(60), // Force first check
        }
    }
    
    fn should_refresh(&self) -> bool {
        self.last_check.elapsed() > Duration::from_secs(30)
    }
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
                eprintln!("{}", "Error: --subdomain atau --crtsh required".red());
                std::process::exit(1);
            }
            
            return Ok(());
        } else {
            eprintln!("{}", "Error: --target required untuk non-interactive mode".red());
            eprintln!("\n{}", "Usage:".cyan());
            eprintln!("  injecttools -t host.com -s subdomain.com --non-interactive");
            eprintln!("  injecttools -t host.com --crtsh -d domain.com --non-interactive");
            std::process::exit(1);
        }
    }

    // Target status cache
    let target_status = Arc::new(Mutex::new(TargetStatus::new()));

    // Interactive mode
    loop {
        ui::clear_screen();
        ui::print_header("INJECTTOOLS v2.4.0");
        
        // Display target status dengan auto-ping
        if !config.target_host.is_empty() {
            let mut status = target_status.lock().unwrap();
            
            // Refresh status jika sudah > 30 detik
            if status.should_refresh() {
                print!("\n{} Checking target status...", "🔄".cyan());
                std::io::Write::flush(&mut std::io::stdout()).ok();
                
                // Quick check target (async)
                let target_clone = config.target_host.clone();
                let is_online = tokio::task::block_in_place(|| {
                    tokio::runtime::Handle::current().block_on(async {
                        check_target_quick(&target_clone).await
                    })
                });
                
                status.is_online = is_online;
                status.last_check = Instant::now();
                
                // Clear checking message
                print!("\r\x1B[K");
            }
            
            let status_text = if status.is_online {
                format!("🟢 ONLINE")
            } else {
                format!("🔴 OFFLINE")
            };
            
            let status_color = if status.is_online {
                status_text.green()
            } else {
                status_text.red()
            };
            
            println!("\n{}", "─".repeat(50).bright_black());
            println!("{} {}", "Target:".bright_black(), config.target_host.cyan().bold());
            println!("{} {}", "Status:".bright_black(), status_color);
            println!("{}", "─".repeat(50).bright_black());
        }
        
        println!("\n{}", "MAIN MENU".bold());
        println!("{}" , "━".repeat(50).cyan());
        println!("\n1. {} Test Single Subdomain", "🔍".cyan());
        println!("2. {} Fetch & Test dari crt.sh", "🌐".cyan());
        println!("3. {} View Exported Results", "📊".cyan());
        println!("4. {} Settings", "⚙️".cyan());
        println!("5. {} Exit", "🚪".red());
        println!("\n{}", "━".repeat(50).cyan());
        
        print!("\n{} ", "Pilih:".bold());
        let choice = ui::read_line();

        match choice.trim() {
            "1" => {
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
            "2" => {
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
            "3" => {
                results::view_results()?;
                ui::pause();
            }
            "4" => {
                settings_menu(&mut config, args.timeout, target_status.clone()).await?;
            }
            "5" => {
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

async fn settings_menu(
    config: &mut config::Config,
    timeout: u64,
    target_status: Arc<Mutex<TargetStatus>>,
) -> anyhow::Result<()> {
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
        println!("2. Test Target Connection");
        println!("3. Back to Main Menu");
        
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
                    
                    // Force refresh status
                    let mut status = target_status.lock().unwrap();
                    status.last_check = Instant::now() - Duration::from_secs(60);
                    
                    std::thread::sleep(std::time::Duration::from_secs(1));
                }
            }
            "2" => {
                if config.target_host.is_empty() {
                    println!("\n{}", "⚠️  Set target host dulu!".yellow());
                    ui::pause();
                    continue;
                }
                
                println!("\n{}", "Testing target connection...".cyan());
                scanner::test_target(&config.target_host, timeout).await?;
                
                // Force refresh status
                let mut status = target_status.lock().unwrap();
                status.last_check = Instant::now() - Duration::from_secs(60);
                
                ui::pause();
            }
            "3" => break,
            _ => {
                println!("\n{}", "❌ Pilihan tidak valid".red());
                ui::pause();
            }
        }
    }
    
    Ok(())
}

// Quick check target status (ping atau TCP check)
async fn check_target_quick(target: &str) -> bool {
    // Try DNS resolution first
    if dns::resolve_domain_first(target).await.is_err() {
        return false;
    }
    
    // Try ping (1 second timeout)
    let output = tokio::process::Command::new("ping")
        .arg("-c")
        .arg("1")
        .arg("-W")
        .arg("1")
        .arg(target)
        .output()
        .await;
    
    if let Ok(result) = output {
        if result.status.success() {
            return true;
        }
    }
    
    // Fallback: TCP port check (443 or 80)
    use std::net::{TcpStream, ToSocketAddrs};
    use std::time::Duration;
    
    for port in &[443, 80] {
        let addr = format!("{}:{}", target, port);
        if let Ok(mut addrs) = addr.to_socket_addrs() {
            if let Some(socket_addr) = addrs.next() {
                if TcpStream::connect_timeout(&socket_addr, Duration::from_secs(2)).is_ok() {
                    return true;
                }
            }
        }
    }
    
    false
}
