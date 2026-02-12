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
#[command(version = "3.6.1")]
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

    /// Enable verbose logging for troubleshooting
    #[arg(short, long)]
    verbose: bool,
}

// Cache untuk target status
struct TargetStatus {
    is_online: bool,
    last_check: Instant,
}

// Track scanned domains for re-scan logic
#[derive(Debug, Clone)]
struct ScanHistory {
    last_scanned_domain: String,
    last_scan_time: Instant,
}

impl ScanHistory {
    fn new() -> Self {
        Self {
            last_scanned_domain: String::new(),
            last_scan_time: Instant::now() - Duration::from_secs(86400), // 24 hours ago
        }
    }
    
    fn should_rescan(&self, domain: &str) -> bool {
        !self.last_scanned_domain.is_empty() 
            && self.last_scanned_domain == domain 
            && self.last_scan_time.elapsed() < Duration::from_secs(3600) // Within 1 hour
    }
    
    fn update_scan(&mut self, domain: &str) {
        self.last_scanned_domain = domain.to_string();
        self.last_scan_time = Instant::now();
    }
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
                        args.verbose,
                    ).await?;
                    
                    results::export_results(&results, &domain)?;
                } else {
                    eprintln!("{}", "Error: --domain required untuk --crtsh".red());
                    std::process::exit(1);
                }
            } else if let Some(subdomain) = args.subdomain {
                // Single test
                scanner::test_single(&config.target_host, &subdomain, args.timeout, args.verbose).await?;
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
    
    // Scan history for re-scan logic
    let scan_history = Arc::new(Mutex::new(ScanHistory::new()));

    // Interactive mode
    loop {
        ui::clear_screen();
        ui::print_header("INJECTTOOLS v3.6.1");
        
        let mut status_icon = "⚪"; // Default neutral
        
        // Display target status dengan auto-check
        if !config.target_host.is_empty() {
            let mut status = target_status.lock().unwrap();
            
            // Refresh status jika sudah > 30 detik
            if status.should_refresh() {
                print!("\n🔄 Checking target status...");
                std::io::Write::flush(&mut std::io::stdout()).ok();
                
                // Quick check target (async)
                let target_clone = config.target_host.clone();
                let is_online = tokio::task::block_in_place(|| {
                    tokio::runtime::Handle::current().block_on(async {
                        check_target_quick(&target_clone, 5).await
                    })
                });
                
                status.is_online = is_online;
                status.last_check = Instant::now();
                
                // Clear checking message
                print!("\r\x1B[K");
            }
            
            status_icon = if status.is_online { "🟢" } else { "🔴" };
        }
        
        // New Header Layout
        // Host: example.com [✎] 🟢
        println!("");
        println!("  {} {} {} {}", 
            "Host:".bold(), 
            if config.target_host.is_empty() { "Not Set".red() } else { config.target_host.cyan().bold() },
            "[E]dit".yellow().dimmed(),
            status_icon
        );
        if args.verbose {
            println!("  {}", "VERBOSE MODE ON".yellow().bold());
        }
        println!("");
        
        // Tiles Menu Layout
        println!("  ┌──────────────────────┐   ┌──────────────────────┐");
        println!("  │ 1. Single Subdomain  │   │ 2. Scan & Test Subdomain  │");
        println!("  └──────────────────────┘   └──────────────────────┘");
        println!("  ┌──────────────────────┐   ┌──────────────────────┐");
        println!("  │ 3. Recent Results    │   │ x. Exit App          │");
        println!("  └──────────────────────┘   └──────────────────────┘");
        
        print!("\n{} ", "Menu >".bold());
        let choice = ui::read_line();

        match choice.trim() {
            "1" => {
                if config.target_host.is_empty() {
                    println!("\n{}", "⚠️  Set target host dulu (tekan 'e')!".yellow());
                    ui::pause();
                    continue;
                }
                
                ui::print_header("TEST SINGLE SUBDOMAIN");
                let subdomain = ui::prompt_with_clear("\nMasukkan subdomain");
                if !subdomain.is_empty() {
                    scanner::test_single(&config.target_host, &subdomain, args.timeout, args.verbose).await?;
                }
                ui::pause();
            }
            "2" => {
                if config.target_host.is_empty() {
                    println!("\n{}", "⚠️  Set target host dulu (tekan 'e')!".yellow());
                    ui::pause();
                    continue;
                }
                
                ui::print_header("CRTSH SUBDOMAIN DISCOVERY");
                let domain = ui::prompt_with_clear("\nMasukkan domain (contoh: cloudflare.com)");
                if !domain.is_empty() {
                    // Check if should show "Re-scan" instead of "Scan"
                    let should_rescan = {
                        let history = scan_history.lock().unwrap();
                        history.should_rescan(&domain)
                    };
                    
                    if should_rescan {
                        println!("\n{} {}", "🔄 Re-scanning domain:".cyan().bold(), domain.cyan());
                    } else {
                        println!("\n{}", "📡 Fetching subdomains dari crt.sh...".cyan());
                    }
                    
                    match crtsh::fetch_subdomains(&domain).await {
                        Ok(subdomains) => {
                            println!("{} {} subdomains ditemukan\n", "✓".green(), subdomains.len());
                            
                            // Display summary of fetched results
                            println!("{}", "─".repeat(50).cyan());
                            println!("{}", "CRT.SH FETCH SUMMARY".bold());
                            println!("{}", "─".repeat(50).cyan());
                            println!("{}: {}", "Domain".bright_black(), domain.cyan());
                            println!("{}: {}", "Total Subdomains".bright_black(), subdomains.len().to_string().green());
                            if !subdomains.is_empty() {
                                println!("{}: {}", "Sample Subdomains".bright_black(), subdomains.iter().take(5).cloned().collect::<Vec<_>>().join(", ").yellow());
                                if subdomains.len() > 5 {
                                    println!("{}: ... and {} more", "Additional".bright_black(), (subdomains.len() - 5).to_string().yellow());
                                }
                            }
                            println!("{}", "─".repeat(50).cyan());
                            
                            if subdomains.is_empty() {
                                println!("{}", "Tidak ada subdomain ditemukan".yellow());
                            } else {
                                println!("{}", "Mulai testing...".cyan());
                                let results = scanner::batch_test(
                                    &config.target_host,
                                    &subdomains,
                                    args.timeout,
                                    running.clone(),
                                    args.verbose,
                                ).await?;
                                
results::export_results(&results, &domain)?;
                                
                                // Update scan history after successful scan
                                let mut history = scan_history.lock().unwrap();
                                history.update_scan(&domain);
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
            "e" | "E" => {
                // Moved from Menu 4 to 'e'
                ui::print_header("CHANGE TARGET HOST");
                
                if !config.target_host.is_empty() {
                    println!("\n{} {}", "Current target:".bright_black(), config.target_host.cyan());
                }
                
                let target = ui::prompt_with_clear("\nMasukkan target host baru");
                
                if !target.is_empty() {
                    // Test target connection
                    println!("\n{}", "🔍 Testing target connection...".cyan());
                    scanner::test_target(&target, args.timeout, args.verbose).await?;
                    
                    // Save if test successful
                    config.target_host = target;
                    config.save()?;
                    
                    println!("\n{}", "✓ Target host updated".green());
                    
                    // Force refresh status
                    let mut status = target_status.lock().unwrap();
                    status.last_check = Instant::now() - Duration::from_secs(60);
                    
                    std::thread::sleep(std::time::Duration::from_secs(2));
                } else {
                    println!("\n{}", "⚠️  Target host tidak boleh kosong".yellow());
                    std::thread::sleep(std::time::Duration::from_secs(1));
                }
            }
            "x" | "X" | "exit" => {
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

// Quick check target status menggunakan curl --resolve
// curl -s --max-time $TIMEOUT --resolve $TARGET:443:$IP https://$TARGET/ -o /dev/null
async fn check_target_quick(target: &str, timeout: u64) -> bool {
    // Step 1: Resolve target domain untuk dapat IP
    let ip = match dns::resolve_domain_first(target).await {
        Ok(ip) => ip,
        Err(_) => return false,
    };
    
    // Step 2: Test HTTPS connection dengan curl --resolve
    // curl -s --max-time 5 --resolve target:443:ip https://target/ -o /dev/null
    let resolve_arg = format!("{}:443:{}", target, ip);
    let url = format!("https://{}/", target);
    
    let output = tokio::process::Command::new("curl")
        .arg("-s")                          // Silent mode
        .arg("--max-time")
        .arg(timeout.to_string())           // Timeout
        .arg("--resolve")
        .arg(&resolve_arg)                  // Resolve target:443 to IP
        .arg("-k")                          // Allow insecure SSL
        .arg(&url)                          // URL to test
        .arg("-o")
        .arg("/dev/null")                   // Discard output
        .output()
        .await;
    
    if let Ok(result) = output {
        // Exit code 0 = success (connection successful)
        if result.status.success() {
            return true;
        }
        
        // Exit codes yang dianggap "target reachable":
        // - 0: Success
        // - 22: HTTP error (tapi connection berhasil)
        if let Some(code) = result.status.code() {
            if code == 0 || code == 22 {
                return true;
            }
        }
    }
    
    // Fallback: Try HTTP port 80
    let resolve_arg_80 = format!("{}:80:{}", target, ip);
    let url_80 = format!("http://{}/", target);
    
    let output_80 = tokio::process::Command::new("curl")
        .arg("-s")
        .arg("--max-time")
        .arg("3")
        .arg("--resolve")
        .arg(&resolve_arg_80)
        .arg(&url_80)
        .arg("-o")
        .arg("/dev/null")
        .output()
        .await;
    
    if let Ok(result) = output_80 {
        if result.status.success() {
            return true;
        }
        
        if let Some(code) = result.status.code() {
            if code == 0 || code == 22 {
                return true;
            }
        }
    }
    
    false
}