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
 println!(" {}: {}", "Target Host".white(), target_host.green());
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
 ui::print_header("InjectTools v2.3");

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

async fn test_single_menu(config: &Config) -> Result<()> {
 ui::clear_screen();
 ui::print_header("Test Single Subdomain");
 println!();

 let subdomain: String = Input::with_theme(&ColorfulTheme::default())
 .with_prompt("Masukkan subdomain (contoh: cdn.nimo.tv)")
 .interact_text()?;

 if subdomain.is_empty() {
 println!("{}", "Subdomain gak boleh kosong!".red());
 std::thread::sleep(Duration::from_secs(2));
 return Ok(());
 }

 test_single_subdomain(config, &subdomain).await?;

 Input::<String>::new()
 .with_prompt("Tekan Enter untuk lanjut")
 .allow_empty(true)
 .interact_text()?;

 Ok(())
}

async fn test_single_subdomain(config: &Config, subdomain: &str) -> Result<()> {
 println!();
 println!("{}: {}", "Target Host".blue(), config.target_host.green());
 println!("{}: {}", "Testing".blue(), subdomain.yellow());
 println!();

 let scanner = Scanner::new(config.target_host.clone(), config.timeout);

 println!("{}", "🔍 Resolving DNS...".cyan());

 let ip = match scanner.resolve_domain(subdomain).await {
 Some(ip) => ip,
 None => {
 println!("{}", format!("❌ IP gak ditemukan untuk {}", subdomain).red().bold());
 return Ok(());
 }
 };

 println!(" {}: {}", "IP Address".white(), ip.to_string().blue());

 if dns::is_cloudflare(&ip) {
 println!(" {}: {}", "Provider".white(), "☁️ Cloudflare".cyan());
 } else {
 println!(" {}: {}", "Provider".white(), "⚠️ Non-Cloudflare".yellow());
 }

 println!();

 // Ping test
 println!("{}", "📡 Ping test...".cyan());
 if let Some(ping_ms) = scanner.ping_test(subdomain).await {
 println!(" {}: {}ms", "Response time".white(), ping_ms.to_string().green());
 } else {
 println!(" {}: {}", "Response time".white(), "Timeout".red());
 }

 println!();

 // Bug inject test
 println!("{}", "🧪 Testing bug inject...".cyan());

 let result = scanner.test_inject(&ip, subdomain).await;

 println!();

 if result {
 println!("{}", "╔═══════════════════════════════════════════════════════╗".green());
 println!("{}", "║ ✅ BUG INJECT WORKING! ║".green());
 println!("{}", "╚═══════════════════════════════════════════════════════╝".green());
 println!();
 println!(" {}: {}", "Subdomain".white(), subdomain.green());
 println!(" {}: {}", "IP".white(), ip.to_string().green());
 println!(" {}: {}", "Target".white(), config.target_host.green());
 } else {
 println!("{}", "╔═══════════════════════════════════════════════════════╗".red());
 println!("{}", "║ ❌ BUG INJECT FAILED ║".red());
 println!("{}", "╚═══════════════════════════════════════════════════════╝".red());
 println!();
 println!(" {}: {}", "Subdomain".white(), subdomain.red());
 println!(" {}: {}", "IP".white(), ip.to_string().red());
 println!(" {}: {}", "Alasan".white(), "Koneksi gagal atau diblokir".yellow());
 }

 Ok(())
}

async fn batch_test_menu(config: &Config, cancelled: Arc<AtomicBool>) -> Result<()> {
 ui::clear_screen();
 ui::print_header("Batch Subdomain Test");
 println!();

 let options = vec![
 "Input manual (satu per satu)",
 "Load dari file",
 "Kembali",
 ];

 let selection = Select::with_theme(&ColorfulTheme::default())
 .with_prompt("Pilih mode input")
 .items(&options)
 .default(0)
 .interact()?;

 match selection {
 0 => batch_test_manual(config, cancelled).await?,
 1 => batch_test_from_file_menu(config, cancelled).await?,
 2 => return Ok(()),
 _ => {}
 }

 Ok(())
}

async fn batch_test_manual(config: &Config, cancelled: Arc<AtomicBool>) -> Result<()> {
 ui::clear_screen();
 ui::print_header("Batch Test - Manual Input");
 println!();
 println!("{}", "Masukkan subdomain (satu per baris).".blue());
 println!("{} untuk selesai.", "Ketik END".white().bold());
 println!();

 let mut subdomains = Vec::new();

 loop {
 let line: String = Input::new()
 .with_prompt(">")
 .allow_empty(true)
 .interact_text()?;

 if line.to_uppercase() == "END" || line.is_empty() && !subdomains.is_empty() {
 break;
 }

 if !line.is_empty() {
 subdomains.push(line);
 }
 }

 if subdomains.is_empty() {
 println!("{}", "Gak ada subdomain yang diinput!".red());
 std::thread::sleep(Duration::from_secs(2));
 return Ok(());
 }

 batch_test_execute(config, subdomains, cancelled).await?;

 Ok(())
}

async fn batch_test_from_file_menu(config: &Config, cancelled: Arc<AtomicBool>) -> Result<()> {
 ui::clear_screen();
 ui::print_header("Batch Test - Load File");
 println!();

 let file_path: String = Input::with_theme(&ColorfulTheme::default())
 .with_prompt("Masukkan path file (contoh: /sdcard/subdomains.txt)")
 .interact_text()?;

 batch_test_from_file(config, &file_path, cancelled).await?;

 Ok(())
}

async fn batch_test_from_file(
 config: &Config,
 file_path: &str,
 cancelled: Arc<AtomicBool>,
) -> Result<()> {
 use std::fs;
 use std::io::{BufRead, BufReader};

 let file = fs::File::open(file_path)?;
 let reader = BufReader::new(file);

 let subdomains: Vec<String> = reader
 .lines()
 .filter_map(|line| line.ok())
 .filter(|line| !line.trim().is_empty())
 .collect();

 batch_test_execute(config, subdomains, cancelled).await?;

 Ok(())
}

async fn batch_test_execute(
 config: &Config,
 subdomains: Vec<String>,
 cancelled: Arc<AtomicBool>,
) -> Result<()> {
 ui::clear_screen();
 ui::print_header("Batch Testing");
 println!();

 let total = subdomains.len();
 println!("{} {}", "📚 Loaded".green(), format!("{} subdomains", total).white().bold());

 ui::show_test_warning();

 Input::<String>::new()
 .with_prompt("Tekan Enter untuk mulai testing")
 .allow_empty(true)
 .interact_text()?;

 println!();
 println!(
 "{} Tekan {} untuk stop",
 "🧪 Testing...".cyan(),
 "[Ctrl+C]".white().bold()
 );
 println!();

 let scanner = Scanner::new(config.target_host.clone(), config.timeout);
 let results = scanner
 .batch_test(subdomains, cancelled)
 .await?;

 // Show results
 results::display_batch_results(&results, total)?;

 // Export option
 if !results.working.is_empty() || !results.failed.is_empty() {
 if Confirm::with_theme(&ColorfulTheme::default())
 .with_prompt("Export hasil?")
 .default(false)
 .interact()?
 {
 results::export_batch_results(&results)?;
 }
 }

 Input::<String>::new()
 .with_prompt("Tekan Enter untuk kembali ke menu")
 .allow_empty(true)
 .interact_text()?;

 Ok(())
}

async fn full_scan_menu(config: &Config, cancelled: Arc<AtomicBool>) -> Result<()> {
 ui::clear_screen();
 ui::print_header("Full Scan");
 println!();

 let domain: String = Input::with_theme(&ColorfulTheme::default())
 .with_prompt("Masukkan target domain (contoh: nimo.tv, cloudflare.com)")
 .interact_text()?;

 if domain.is_empty() {
 println!("{}", "Domain gak boleh kosong!".red());
 std::thread::sleep(Duration::from_secs(2));
 return Ok(());
 }

 full_scan(config, &domain, cancelled).await?;

 Ok(())
}

async fn full_scan(
 config: &Config,
 domain: &str,
 cancelled: Arc<AtomicBool>,
) -> Result<()> {
 ui::clear_screen();
 ui::print_header("Scanning Subdomains");
 println!();

 // Fetch from crt.sh
 let subdomains = match crtsh::fetch_subdomains(domain).await {
 Ok(subs) => subs,
 Err(e) => {
 println!("{}", format!("❌ Scan gagal: {}", e).red().bold());
 println!();
 println!("{}", "Kemungkinan:".yellow());
 println!(" {} Gak ada koneksi internet", "•".white());
 println!(" {} Service scan sedang down", "•".white());
 println!(" {} Request timeout", "•".white());
 println!();

 Input::<String>::new()
 .with_prompt("Tekan Enter untuk kembali")
 .allow_empty(true)
 .interact_text()?;
 return Ok(());
 }
 };

 println!();
 println!("{}", "━".repeat(ui::term_width()).cyan());
 println!();
 println!("{}", "✅ Subdomain list tersimpan".green().bold());
 println!();
 println!(
 "{}: {}",
 "Total subdomains".cyan(),
 subdomains.len().to_string().white().bold()
 );
 println!();

 Input::<String>::new()
 .with_prompt("Tekan Enter untuk lanjut ke testing")
 .allow_empty(true)
 .interact_text()?;

 // Test phase
 ui::clear_screen();
 ui::print_header("Testing Subdomains");
 println!();
 println!("{}: {}", "Domain".blue(), domain.green());
 println!("{}: {}", "Target".blue(), config.target_host.green());
 println!("{}: {}", "Filter".blue(), "☁️ Cloudflare only".cyan());
 println!();

 let total = subdomains.len();
 println!("{} {}", "📚 Loaded".green(), format!("{} subdomains", total).white().bold());

 ui::show_test_warning();

 Input::<String>::new()
 .with_prompt("Tekan Enter untuk mulai testing")
 .allow_empty(true)
 .interact_text()?;

 println!();
 println!(
 "{} Tekan {} untuk stop",
 "🧪 Testing...".cyan(),
 "[Ctrl+C]".white().bold()
 );
 println!();

 let scanner = Scanner::new(config.target_host.clone(), config.timeout);
 let results = scanner
 .full_scan(subdomains, cancelled)
 .await?;

 // Show results
 results::display_full_scan_results(&results, domain, total)?;

 // Export option
 if !results.working.is_empty() || !results.failed.is_empty() {
 if Confirm::with_theme(&ColorfulTheme::default())
 .with_prompt("Export hasil?")
 .default(false)
 .interact()?
 {
 results::export_full_scan_results(&results, domain)?;
 }
 }

 Input::<String>::new()
 .with_prompt("Tekan Enter untuk kembali ke menu")
 .allow_empty(true)
 .interact_text()?;

 Ok(())
}

async fn test_target_host_menu(config: &Config) -> Result<()> {
 ui::clear_screen();
 ui::print_header("Test Target Host");
 println!();
 println!("{}: {}", "Target Host".blue(), config.target_host.green());
 println!();
 println!("{}", "🔍 Checking status...".cyan());
 println!();

 let scanner = Scanner::new(config.target_host.clone(), config.timeout);

 // DNS Resolution
 println!("{}", "1. DNS Resolution:".white());
 let ip = match scanner.resolve_domain(&config.target_host).await {
 Some(ip) => {
 println!(" {}", "✓ Sukses".green());
 println!(" {}: {}", "IP".cyan(), ip.to_string());
 ip
 }
 None => {
 println!(" {}", "❌ Gagal - IP gak ditemukan".red());
 println!();
 println!("{}", "Target host DOWN atau gak bisa diakses!".red().bold());
 println!();

 Input::<String>::new()
 .with_prompt("Tekan Enter untuk kembali")
 .allow_empty(true)
 .interact_text()?;
 return Ok(());
 }
 };

 println!();

 // Ping Test
 println!("{}", "2. Ping Test:".white());
 if let Some(ping_ms) = scanner.ping_test(&config.target_host).await {
 println!(" {}", "✓ Sukses".green());
 println!(" {}: {}ms", "Response time".cyan(), ping_ms);
 println!();
 println!("{}", "✅ Target host UP dan berjalan normal!".green().bold());
 } else {
 println!(" {}", "❌ Gagal - Host unreachable".red());
 println!();
 println!("{}", "Target host DOWN atau gak bisa diakses!".red().bold());
 }

 println!();

 Input::<String>::new()
 .with_prompt("Tekan Enter untuk kembali")
 .allow_empty(true)
 .interact_text()?;

 Ok(())
}

fn settings_menu(config: &mut Config) -> Result<()> {
 loop {
 ui::clear_screen();
 ui::print_header("SETTINGS");
 println!();
 println!("{}", "Config saat ini:".white().bold());
 println!(" {}: {}", "Target Host".blue(), config.target_host.green());
 println!(" {}: {}s", "Timeout".blue(), config.timeout.to_string().cyan());
 println!(
 " {}: {}",
 "Data Directory".blue(),
 Config::base_dir().display().to_string().cyan()
 );
 println!();

 let options = vec![
 "Ubah Target Host",
 "Ubah Timeout",
 "Setup Ulang",
 "Kembali",
 ];

 let selection = Select::with_theme(&ColorfulTheme::default())
 .items(&options)
 .default(0)
 .interact()?;

 match selection {
 0 => {
 let new_host: String = Input::with_theme(&ColorfulTheme::default())
 .with_prompt("Target host baru")
 .interact_text()?;

 if !new_host.is_empty() {
 config.target_host = new_host;
 config.save()?;
 }
 }
 1 => {
 let new_timeout: String = Input::with_theme(&ColorfulTheme::default())
 .with_prompt("Timeout (detik)")
 .interact_text()?;

 if let Ok(timeout) = new_timeout.parse::<u64>() {
 config.timeout = timeout;
 config.save()?;
 }
 }
 2 => {
 *config = first_time_setup()?;
 }
 3 => return Ok(()),
 _ => {}
 }
 }
}
