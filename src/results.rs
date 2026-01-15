// Results viewer and export functionality

use anyhow::Result;
use chrono::Local;
use colored::*;
use dialoguer::{theme::ColorfulTheme, Input, Select};
use std::fs;
use std::path::PathBuf;

use crate::config::Config;
use crate::scanner::{ScanResults, TestResult};
use crate::ui;

pub fn view_results() -> Result<()> {
 loop {
 ui::clear_screen();
 ui::print_header("Hasil Scan Tersimpan");
 println!();

 let results_dir = Config::results_dir();

 if !results_dir.exists() || fs::read_dir(&results_dir)?.next().is_none() {
 println!("{}", "Belum ada hasil scan tersimpan.".yellow());
 println!();

 Input::<String>::new()
 .with_prompt("Tekan Enter untuk kembali")
 .allow_empty(true)
 .interact_text()?;
 return Ok(());
 }

 let mut files: Vec<PathBuf> = fs::read_dir(&results_dir)?
 .filter_map(|entry| entry.ok())
 .map(|entry| entry.path())
 .filter(|path| path.extension().and_then(|s| s.to_str()) == Some("txt"))
 .collect();

 files.sort_by(|a, b| {
 let a_modified = fs::metadata(a).and_then(|m| m.modified()).ok();
 let b_modified = fs::metadata(b).and_then(|m| m.modified()).ok();
 b_modified.cmp(&a_modified)
 });

 if files.is_empty() {
 println!("{}", "Gak ada file ditemukan.".yellow());
 println!();

 Input::<String>::new()
 .with_prompt("Tekan Enter untuk kembali")
 .allow_empty(true)
 .interact_text()?;
 return Ok(());
 }

 println!("{}", "Daftar hasil scan:".white().bold());
 println!();

 let mut options: Vec<String> = Vec::new();

 for file in &files {
 let filename = file.file_name().unwrap().to_string_lossy();
 let size = fs::metadata(file)
 .map(|m| format_size(m.len()))
 .unwrap_or_else(|_| "?".to_string());

 let modified = fs::metadata(file)
 .and_then(|m| m.modified())
 .ok()
 .map(|time| {
 let datetime: chrono::DateTime<Local> = time.into();
 datetime.format("%Y-%m-%d %H:%M:%S").to_string()
 })
 .unwrap_or_else(|| "Unknown".to_string());

 options.push(format!(
 "{} | Size: {} | Modified: {}",
 filename, size, modified
 ));
 }

 options.push("[Hapus Semua]".to_string());
 options.push("[Kembali]".to_string());

 let selection = Select::with_theme(&ColorfulTheme::default())
 .items(&options)
 .default(0)
 .interact()?;

 if selection == options.len() - 1 {
 // Kembali
 return Ok(());
 } else if selection == options.len() - 2 {
 // Hapus semua
 if dialoguer::Confirm::with_theme(&ColorfulTheme::default())
 .with_prompt("Hapus SEMUA file?")
 .default(false)
 .interact()?
 {
 for file in &files {
 let _ = fs::remove_file(file);
 }
 println!("{}", "✅ Semua file dihapus".green());
 std::thread::sleep(std::time::Duration::from_secs(2));
 }
 continue;
 }

 // File selected
 let selected_file = &files[selection];

 let actions = vec!["Lihat isi file", "Hapus file", "Kembali"];

 let action = Select::with_theme(&ColorfulTheme::default())
 .with_prompt("Pilih aksi")
 .items(&actions)
 .default(0)
 .interact()?;

 match action {
 0 => {
 // View file
 ui::clear_screen();
 ui::print_header("Isi File");
 println!();

 let content = fs::read_to_string(selected_file)?;
 println!("{}", content);
 println!();

 Input::<String>::new()
 .with_prompt("Tekan Enter untuk kembali")
 .allow_empty(true)
 .interact_text()?;
 }
 1 => {
 // Delete file
 if dialoguer::Confirm::with_theme(&ColorfulTheme::default())
 .with_prompt(format!(
 "Hapus file: {}?",
 selected_file.file_name().unwrap().to_string_lossy()
 ))
 .default(false)
 .interact()?
 {
 fs::remove_file(selected_file)?;
 println!("{}", "✅ File dihapus".green());
 std::thread::sleep(std::time::Duration::from_secs(1));
 }
 }
 2 => continue,
 _ => {}
 }
 }
}

fn format_size(bytes: u64) -> String {
 const KB: u64 = 1024;
 const MB: u64 = KB * 1024;

 if bytes >= MB {
 format!("{:.2} MB", bytes as f64 / MB as f64)
 } else if bytes >= KB {
 format!("{:.2} KB", bytes as f64 / KB as f64)
 } else {
 format!("{} B", bytes)
 }
}

pub fn display_batch_results(results: &ScanResults, total: usize) -> Result<()> {
 println!();
 println!();
 ui::print_header("HASIL BATCH TEST");
 println!();

 if results.cancelled {
 println!(
 "{}",
 "Catatan: Test dibatalkan (hasil parsial)".yellow().bold()
 );
 println!();
 }

 // Working bugs
 if !results.working.is_empty() {
 println!(
 "{} ({})",
 "✅ Working Bugs".green().bold(),
 results.working.len()
 );
 println!();

 for bug in &results.working {
 println!(" {} {}", "🟢".green(), bug.subdomain.white());
 println!(" {} IP: {}", "└─".cyan(), bug.ip);
 if let Some(ping) = bug.ping_ms {
 println!(" {} Ping: {}ms", "└─".cyan(), ping);
 }
 }
 println!();
 } else {
 println!("{}", "Gak ada working bug ditemukan".yellow());
 println!();
 }

 // Failed tests
 if !results.failed.is_empty() {
 println!(
 "{} ({})",
 "❌ Failed Tests".red().bold(),
 results.failed.len()
 );
 println!();

 for bug in &results.failed {
 println!(" {} {}", "🔴".red(), bug.subdomain.white());
 println!(" {} IP: {}", "└─".cyan(), bug.ip);
 if let Some(ping) = bug.ping_ms {
 println!(" {} Ping: {}ms", "└─".cyan(), ping);
 }
 }
 println!();
 }

 // Statistics
 println!("{}", "─".repeat(ui::term_width()).cyan());
 println!("{}", "Statistik:".white().bold());
 println!();
 println!(" {}: {}", "Total Tested".blue(), total.to_string().white());
 println!(
 " {}: {}",
 "No DNS/Failed".blue(),
 results.skipped.to_string().yellow()
 );
 println!();
 println!(
 " {}: {}",
 "Working".green(),
 results.working.len().to_string().green().bold()
 );
 println!(
 " {}: {}",
 "Failed".red(),
 results.failed.len().to_string().red()
 );
 println!();
 println!(
 " {}: {}s",
 "Waktu".blue(),
 results.elapsed_secs.to_string().cyan()
 );
 println!();

 Ok(())
}

pub fn display_full_scan_results(
 results: &ScanResults,
 domain: &str,
 total: usize,
) -> Result<()> {
 println!();
 println!();
 ui::print_header("HASIL SCAN");
 println!();

 if results.cancelled {
 println!(
 "{}",
 "Catatan: Scan dibatalkan (hasil parsial)".yellow().bold()
 );
 println!();
 }

 // Working bugs
 if !results.working.is_empty() {
 println!(
 "{} ({})",
 "✅ Working Bugs".green().bold(),
 results.working.len()
 );
 println!();

 for bug in &results.working {
 println!(" {} {}", "🟢".green(), bug.subdomain.white());
 println!(" {} IP: {}", "└─".cyan(), bug.ip);
 if let Some(ping) = bug.ping_ms {
 println!(" {} Ping: {}ms", "└─".cyan(), ping);
 }
 }
 println!();
 } else {
 println!("{}", "Gak ada working bug ditemukan".yellow());
 println!();
 }

 // Failed tests
 if !results.failed.is_empty() {
 println!(
 "{} ({})",
 "❌ Failed Tests".red().bold(),
 results.failed.len()
 );
 println!();

 for bug in &results.failed {
 println!(" {} {}", "🔴".red(), bug.subdomain.white());
 println!(" {} IP: {}", "└─".cyan(), bug.ip);
 if let Some(ping) = bug.ping_ms {
 println!(" {} Ping: {}ms", "└─".cyan(), ping);
 }
 }
 println!();
 }

 // Statistics
 println!("{}", "─".repeat(ui::term_width()).cyan());
 println!("{}", "Statistik Scan:".white().bold());
 println!();
 println!(
 " {}: {}",
 "Total Subdomains".blue(),
 total.to_string().white()
 );
 println!(
 " {}: {}",
 "Cloudflare Found".blue(),
 results.cf_tested.to_string().cyan()
 );
 println!(
 " {}: {}",
 "Non-CF/No DNS".blue(),
 results.skipped.to_string().yellow()
 );
 println!();
 println!(
 " {}: {}",
 "Working".green(),
 results.working.len().to_string().green().bold()
 );
 println!(
 " {}: {}",
 "Failed".red(),
 results.failed.len().to_string().red()
 );
 println!();
 println!(
 " {}: {} Cloudflare subdomains",
 "Total Tested".blue(),
 results.cf_tested.to_string().white()
 );
 println!(
 " {}: {}s",
 "Waktu".blue(),
 results.elapsed_secs.to_string().cyan()
 );
 println!();

 Ok(())
}

pub fn export_batch_results(results: &ScanResults) -> Result<()> {
 let timestamp = Local::now().format("%Y%m%d-%H%M%S");
 let filename = format!("batch-{}.txt", timestamp);
 let filepath = Config::results_dir().join(&filename);

 let mut content = String::new();
 content.push_str("# InjectTools Batch Test Results\n");
 content.push_str("# Created by: t.me/hoshiyomi_id\n");
 content.push_str(&format!("# Date: {}\n", Local::now().format("%Y-%m-%d %H:%M:%S")));
 content.push_str("# \n");
 content.push_str("# Statistics:\n");
 content.push_str(&format!("# - Total tested: {}\n", results.working.len() + results.failed.len()));
 content.push_str(&format!("# - No DNS/Failed: {}\n", results.skipped));
 content.push_str(&format!("# - Test time: {}s\n", results.elapsed_secs));
 if results.cancelled {
 content.push_str("# - Status: Cancelled (partial)\n");
 }
 content.push_str("\n");

 if !results.working.is_empty() {
 content.push_str(&format!("=== WORKING BUGS ({}) ===\n", results.working.len()));
 content.push_str("\n");
 for bug in &results.working {
 content.push_str(&format!("✅ {}\n", bug.subdomain));
 content.push_str(&format!(" IP: {}\n", bug.ip));
 if let Some(ping) = bug.ping_ms {
 content.push_str(&format!(" Ping: {}ms\n", ping));
 }
 content.push_str("\n");
 }
 content.push_str("\n");
 }

 if !results.failed.is_empty() {
 content.push_str(&format!("=== FAILED TESTS ({}) ===\n", results.failed.len()));
 content.push_str("\n");
 for bug in &results.failed {
 content.push_str(&format!("❌ {}\n", bug.subdomain));
 content.push_str(&format!(" IP: {}\n", bug.ip));
 if let Some(ping) = bug.ping_ms {
 content.push_str(&format!(" Ping: {}ms\n", ping));
 }
 content.push_str("\n");
 }
 }

 fs::write(&filepath, content)?;

 println!(
 "{} {}",
 "✅ Tersimpan:".green().bold(),
 filepath.display()
 );
 std::thread::sleep(std::time::Duration::from_secs(2));

 Ok(())
}

pub fn export_full_scan_results(results: &ScanResults, domain: &str) -> Result<()> {
 let timestamp = Local::now().format("%Y%m%d-%H%M%S");
 let filename = format!("fullscan-{}-{}.txt", domain, timestamp);
 let filepath = Config::results_dir().join(&filename);

 let mut content = String::new();
 content.push_str("# InjectTools Full Scan Results\n");
 content.push_str("# Created by: t.me/hoshiyomi_id\n");
 content.push_str(&format!("# Domain: {}\n", domain));
 content.push_str(&format!("# Date: {}\n", Local::now().format("%Y-%m-%d %H:%M:%S")));
 content.push_str("# \n");
 content.push_str("# Statistics:\n");
 content.push_str(&format!("# - Cloudflare found: {}\n", results.cf_tested));
 content.push_str(&format!("# - Non-CF/No DNS: {}\n", results.skipped));
 content.push_str(&format!("# - Scan time: {}s\n", results.elapsed_secs));
 if results.cancelled {
 content.push_str("# - Status: Cancelled (partial)\n");
 }
 content.push_str("\n");

 if !results.working.is_empty() {
 content.push_str(&format!("=== WORKING BUGS ({}) ===\n", results.working.len()));
 content.push_str("\n");
 for bug in &results.working {
 content.push_str(&format!("✅ {}\n", bug.subdomain));
 content.push_str(&format!(" IP: {}\n", bug.ip));
 if let Some(ping) = bug.ping_ms {
 content.push_str(&format!(" Ping: {}ms\n", ping));
 }
 content.push_str("\n");
 }
 content.push_str("\n");
 }

 if !results.failed.is_empty() {
 content.push_str(&format!("=== FAILED TESTS ({}) ===\n", results.failed.len()));
 content.push_str("\n");
 for bug in &results.failed {
 content.push_str(&format!("❌ {}\n", bug.subdomain));
 content.push_str(&format!(" IP: {}\n", bug.ip));
 if let Some(ping) = bug.ping_ms {
 content.push_str(&format!(" Ping: {}ms\n", ping));
 }
 content.push_str("\n");
 }
 }

 fs::write(&filepath, content)?;

 println!(
 "{} {}",
 "✅ Tersimpan:".green().bold(),
 filepath.display()
 );
 std::thread::sleep(std::time::Duration::from_secs(2));

 Ok(())
}
