use anyhow::Result;
use clap::Parser;
use colored::*;
use dialoguer::{theme::ColorfulTheme, Input, Select, Confirm};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::time::Duration;

mod config;
mod scanner;
mod dns;
mod ui;
mod crtsh;
mod results;

use config::Config;
use scanner::Scanner;

#[derive(Parser, Debug)]
#[command(name = "InjectTools")]
#[command(version = "2.3.1")]
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

    /// Batch test file
    #[arg(short, long)]
    batch_file: Option<String>,

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

    // Setup Ctrl+C handler
    let cancelled = Arc::new(AtomicBool::new(false));
    let cancelled_clone = Arc::clone(&cancelled);
    ctrlc::set_handler(move || {
        cancelled_clone.store(true, Ordering::SeqCst);
        eprintln!("\n{}\n", "⚠️ Scan dibatalkan oleh user (Ctrl+C)".yellow().bold());
        std::process::exit(0);
    })?;

    // Check dependencies
    check_dependencies();

    // Load or create config
    let mut config = match Config::load() {
        Ok(c) => c,
        Err(_) => {
            if args.non_interactive {
                eprintln!("{}", "Error: Config tidak ditemukan. Jalankan interactive mode dulu.".red());
                std::process::exit(1);
            }
            first_time_setup()?
        }
    };

    // Override config with CLI args
    if let Some(target) = args.target {
        config.target_host = target;
    }
    config.timeout = args.timeout;

    // CLI mode
    if args.non_interactive {
        if let Some(subdomain) = args.subdomain {
            test_single_subdomain(&config, &subdomain).await?;
            return Ok(());
        }

        if let Some(batch_file) = args.batch_file {
            batch_test_from_file(&config, &batch_file, cancelled).await?;
            return Ok(());
        }

        if let Some(domain) = args.domain {
            full_scan(&config, &domain, cancelled).await?;
            return Ok(());
        }

        eprintln!("{}", "Error: Specify --subdomain, --batch-file, or --domain".red());
        std::process::exit(1);
    }

    // Interactive mode
    main_menu(&mut config, cancelled).await?;

    Ok(())
}

fn check_dependencies() {
    // Check if curl/reqwest works (we're using Rust so this is implicit)
    // Just a placeholder for future dependency checks
}

fn first_time_setup() -> Result<Config> {
    ui::clear_screen();
    ui::print_header("SETUP AWAL");

    println!();
    println!("{}", "Selamat datang di InjectTools!".white());
    println!("{}", "Created by: t.me/hoshiyomi_id".cyan());
    println!();
    println!("{}", "Yuk setup config dulu.".blue());
    println!();

    println!("{}", "━".repeat(ui::term_width()).cyan());
    println!("{}", "Setup Target Host".white().bold());
    println!("{}", "━".repeat(ui::term_width()).cyan());
    println!();
    println!("{}", "Ini domain tunnel/proxy kamu yang akan dipakai bug inject.".blue());
    println!("{}", "Contoh: your-tunnel.com, proxy.example.com".yellow());
    println!();

    let target_host: String = Input::with_theme(&ColorfulTheme::default())
        .with_prompt("Masukkan target host")
        .interact_text()?;

    println!();
    println!("{}", "━".repeat(ui::term_width()).cyan());
    println!("{}", "Setup Selesai!".white().bold());
    println!("{}", "━".repeat(ui::term_width()).cyan());
    println!();

    let config = Config {
        target_host: target_host.clone(),
        timeout: 10,
    };

    config.save()?;

    println!("{}", "Config kamu:".blue());
    println!("  {}: {}", "Target Host".white(), target_host.green());
    println!();
    println!("{}: {}", "Data disimpan di".blue(), Config::config_dir().display().to_string().cyan());
    println!();
    println!("{}", "Config bisa diubah kapan saja dari Menu 6 (Settings).".blue());
    println!();

    Input::<String>::new()
        .with_prompt("Tekan Enter untuk lanjut")
        .allow_empty(true)
        .interact_text()?;

    Ok(config)
}

async fn main_menu(config: &mut Config, cancelled: Arc<AtomicBool>) -> Result<()> {
    loop {
        ui::clear_screen();
        ui::print_header("InjectTools v2.3.1");

        println!();
        println!("{}", "Created by: t.me/hoshiyomi_id".cyan());
        println!();
        println!("{}: {}", "Target Host".blue(), config.target_host.green());
        println!();
        println!("{}", "─".repeat(ui::term_width()).cyan());
        println!();
        println!("{}", "Menu:".white().bold());
        println!();

        let options = vec![
            "Single Subdomain Test",
            "Batch Subdomain Test",
            "Full Scan (crt.sh)",
            "View Scan Results",
            "Test Target Host",
            "Settings",
            "Exit",
        ];

        let selection = Select::with_theme(&ColorfulTheme::default())
            .items(&options)
            .default(0)
            .interact()?;

        match selection {
            0 => test_single_menu(config).await?,
            1 => batch_test_menu(config, Arc::clone(&cancelled)).await?,
            2 => full_scan_menu(config, Arc::clone(&cancelled)).await?,
            3 => results::view_results()?,
            4 => test_target_host_menu(config).await?,
            5 => settings_menu(config)?,
            6 => {
                ui::clear_screen();
                println!("{}", "Sampai jumpa!".green().bold());
                println!("{}", "Created by: t.me/hoshiyomi_id".cyan());
                println!();
                std::process::exit(0);
            }
            _ => {}
        }
    }
}

// ... rest of the code stays same ...
// (keeping only the function signatures for brevity, actual implementation unchanged)

async fn test_single_menu(config: &Config) -> Result<()> { /* unchanged */ }
async fn test_single_subdomain(config: &Config, subdomain: &str) -> Result<()> { /* unchanged */ }
async fn batch_test_menu(config: &Config, cancelled: Arc<AtomicBool>) -> Result<()> { /* unchanged */ }
async fn batch_test_manual(config: &Config, cancelled: Arc<AtomicBool>) -> Result<()> { /* unchanged */ }
async fn batch_test_from_file_menu(config: &Config, cancelled: Arc<AtomicBool>) -> Result<()> { /* unchanged */ }
async fn batch_test_from_file(config: &Config, file_path: &str, cancelled: Arc<AtomicBool>) -> Result<()> { /* unchanged */ }
async fn batch_test_execute(config: &Config, subdomains: Vec<String>, cancelled: Arc<AtomicBool>) -> Result<()> { /* unchanged */ }
async fn full_scan_menu(config: &Config, cancelled: Arc<AtomicBool>) -> Result<()> { /* unchanged */ }
async fn full_scan(config: &Config, domain: &str, cancelled: Arc<AtomicBool>) -> Result<()> { /* unchanged */ }
async fn test_target_host_menu(config: &Config) -> Result<()> { /* unchanged */ }
fn settings_menu(config: &mut Config) -> Result<()> { /* unchanged */ }
