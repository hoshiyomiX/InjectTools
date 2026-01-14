use crate::config::Config;
use crate::ui;

use anyhow::Result;
use colored::*;
use std::fs;
use std::io::{self, Write};
use std::path::{Path, PathBuf};

const EMBEDDED_WORDLIST: &str = include_str!("../wordlists/embedded.txt");

pub fn wordlist_dir() -> PathBuf {
    dirs::home_dir()
        .unwrap_or_else(|| PathBuf::from("."))
        .join("bug-wordlists")
}

pub fn auto_detect_wordlist() -> Option<PathBuf> {
    let dir = wordlist_dir();
    if !dir.exists() {
        return None;
    }
    
    let mut largest: Option<(PathBuf, usize)> = None;
    
    if let Ok(entries) = fs::read_dir(&dir) {
        for entry in entries.flatten() {
            let path = entry.path();
            if path.extension().and_then(|s| s.to_str()) == Some("txt") {
                if let Ok(count) = count_lines(&path) {
                    if largest.is_none() || count > largest.as_ref().unwrap().1 {
                        largest = Some((path, count));
                    }
                }
            }
        }
    }
    
    largest.map(|(path, _)| path)
}

pub fn count_lines(path: &Path) -> Result<usize> {
    let content = fs::read_to_string(path)?;
    Ok(content.lines().filter(|l| !l.trim().is_empty()).count())
}

pub fn load_wordlist(active: &Option<String>) -> Result<String> {
    match active {
        Some(path) => Ok(fs::read_to_string(path)?),
        None => Ok(EMBEDDED_WORDLIST.to_string()),
    }
}

pub fn wordlist_menu(config: &mut Config) -> Result<()> {
    loop {
        ui::clear_screen();
        ui::print_header("Wordlist Manager");
        println!();
        println!("{}", "Wordlist Tersedia untuk Download:".white().bold());
        println!();
        println!("  {}. Small - 5,000 subdomains {}", "1".white(), "(~90 KB)".cyan());
        println!("  {}. Medium - 20,000 subdomains {}", "2".white(), "(~350 KB)".cyan());
        println!("  {}. Large - 110,000 subdomains {}", "3".white(), "(~2 MB)".cyan());
        println!("  {}. View Downloaded Wordlists", "4".white());
        println!("  {}. Delete Wordlists", "5".white());
        println!("  {}. Reset to Embedded", "6".white());
        println!("  {}. Back", "7".white());
        println!();
        
        print!("Pilih [1-7]: ");
        io::stdout().flush()?;
        
        let mut choice = String::new();
        io::stdin().read_line(&mut choice)?;
        
        match choice.trim() {
            "1" => {
                download_wordlist(
                    "small",
                    "https://raw.githubusercontent.com/danielmiessler/SecLists/master/Discovery/DNS/subdomains-top1million-5000.txt",
                    "seclists-5k.txt",
                    config,
                )?;
            }
            "2" => {
                download_wordlist(
                    "medium",
                    "https://raw.githubusercontent.com/danielmiessler/SecLists/master/Discovery/DNS/subdomains-top1million-20000.txt",
                    "seclists-20k.txt",
                    config,
                )?;
            }
            "3" => {
                download_wordlist(
                    "large",
                    "https://raw.githubusercontent.com/danielmiessler/SecLists/master/Discovery/DNS/subdomains-top1million-110000.txt",
                    "seclists-110k.txt",
                    config,
                )?;
            }
            "4" => view_wordlists(config)?,
            "5" => delete_wordlists(config)?,
            "6" => {
                config.active_wordlist = None;
                config.save()?;
                println!("{}", "✅ Reset ke embedded".green());
                std::thread::sleep(std::time::Duration::from_secs(1));
            }
            "7" => return Ok(()),
            _ => {}
        }
    }
}

fn download_wordlist(size: &str, url: &str, filename: &str, config: &mut Config) -> Result<()> {
    let dir = wordlist_dir();
    fs::create_dir_all(&dir)?;
    
    let filepath = dir.join(filename);
    
    println!();
    println!("{}", format!("📥 Downloading {} wordlist...", size).cyan());
    println!();
    
    if filepath.exists() {
        print!("{}", "⚠️  File sudah ada! Timpa? (y/n): ".yellow());
        io::stdout().flush()?;
        let mut overwrite = String::new();
        io::stdin().read_line(&mut overwrite)?;
        if !overwrite.trim().to_lowercase().starts_with('y') {
            return Ok(());
        }
    }
    
    // Download using reqwest blocking
    let response = reqwest::blocking::get(url)?;
    let content = response.text()?;
    fs::write(&filepath, &content)?;
    
    let line_count = content.lines().filter(|l| !l.trim().is_empty()).count();
    let file_size = fs::metadata(&filepath)?.len();
    let size_kb = file_size / 1024;
    
    println!();
    println!(
        "{}",
        format!(
            "✅ Berhasil! Baris: {} | Ukuran: {} KB",
            line_count, size_kb
        )
        .green()
        .bold()
    );
    println!();
    
    print!("Set sebagai aktif? (y/n): ");
    io::stdout().flush()?;
    let mut set_active = String::new();
    io::stdin().read_line(&mut set_active)?;
    
    if set_active.trim().to_lowercase().starts_with('y') {
        config.active_wordlist = Some(filepath.to_string_lossy().to_string());
        config.save()?;
        println!("{}", format!("✅ Aktif: {}", filename).green());
    }
    
    println!();
    print!("Tekan Enter...");
    io::stdout().flush()?;
    let mut _dummy = String::new();
    io::stdin().read_line(&mut _dummy)?;
    
    Ok(())
}

fn view_wordlists(config: &mut Config) -> Result<()> {
    ui::clear_screen();
    ui::print_header("Downloaded Wordlists");
    println!();
    
    let dir = wordlist_dir();
    if !dir.exists() {
        println!("{}", "Belum ada wordlist yang didownload".yellow());
        println!();
        print!("Tekan Enter...");
        io::stdout().flush()?;
        let mut _dummy = String::new();
        io::stdin().read_line(&mut _dummy)?;
        return Ok(());
    }
    
    let mut wordlists: Vec<PathBuf> = Vec::new();
    
    if let Ok(entries) = fs::read_dir(&dir) {
        for entry in entries.flatten() {
            let path = entry.path();
            if path.extension().and_then(|s| s.to_str()) == Some("txt") {
                wordlists.push(path);
            }
        }
    }
    
    if wordlists.is_empty() {
        println!("{}", "Belum ada wordlist yang didownload".yellow());
    } else {
        let active_path = config.active_wordlist.as_ref().map(|s| Path::new(s));
        
        for (idx, path) in wordlists.iter().enumerate() {
            let filename = path.file_name().and_then(|n| n.to_str()).unwrap_or("unknown");
            let lines = count_lines(path).unwrap_or(0);
            let size = fs::metadata(path)
                .map(|m| m.len() / 1024)
                .unwrap_or(0);
            
            let is_active = active_path == Some(path.as_path());
            
            if is_active {
                println!(
                    "  {}. ★ {} {}",
                    (idx + 1).to_string().green().bold(),
                    filename.green().bold(),
                    format!("({} lines, {} KB)", lines, size).cyan()
                );
            } else {
                println!(
                    "  {}. {} {}",
                    (idx + 1).to_string().white(),
                    filename,
                    format!("({} lines, {} KB)", lines, size).cyan()
                );
            }
        }
        
        println!();
        print!("Pilih untuk set aktif (0=batal): ");
        io::stdout().flush()?;
        
        let mut selection = String::new();
        io::stdin().read_line(&mut selection)?;
        
        if let Ok(num) = selection.trim().parse::<usize>() {
            if num > 0 && num <= wordlists.len() {
                let selected = &wordlists[num - 1];
                config.active_wordlist = Some(selected.to_string_lossy().to_string());
                config.save()?;
                println!(
                    "{}",
                    format!(
                        "✅ Aktif: {}",
                        selected.file_name().and_then(|n| n.to_str()).unwrap_or("unknown")
                    )
                    .green()
                );
                std::thread::sleep(std::time::Duration::from_secs(2));
            }
        }
    }
    
    println!();
    print!("Tekan Enter...");
    io::stdout().flush()?;
    let mut _dummy = String::new();
    io::stdin().read_line(&mut _dummy)?;
    
    Ok(())
}

fn delete_wordlists(config: &mut Config) -> Result<()> {
    ui::clear_screen();
    ui::print_header("Delete Wordlists");
    println!();
    
    let dir = wordlist_dir();
    if !dir.exists() {
        println!("{}", "Tidak ada wordlist untuk dihapus".yellow());
        std::thread::sleep(std::time::Duration::from_secs(2));
        return Ok(());
    }
    
    let mut wordlists: Vec<PathBuf> = Vec::new();
    
    if let Ok(entries) = fs::read_dir(&dir) {
        for entry in entries.flatten() {
            let path = entry.path();
            if path.extension().and_then(|s| s.to_str()) == Some("txt") {
                wordlists.push(path);
            }
        }
    }
    
    if wordlists.is_empty() {
        println!("{}", "Tidak ada wordlist untuk dihapus".yellow());
        std::thread::sleep(std::time::Duration::from_secs(2));
        return Ok(());
    }
    
    for (idx, path) in wordlists.iter().enumerate() {
        let filename = path.file_name().and_then(|n| n.to_str()).unwrap_or("unknown");
        let size = fs::metadata(path)
            .map(|m| m.len() / 1024)
            .unwrap_or(0);
        println!(
            "  {}. {} {}",
            (idx + 1).to_string().white(),
            filename,
            format!("({} KB)", size).cyan()
        );
    }
    
    println!();
    println!("  {}. Delete All", "A".red().bold());
    println!("  {}. Cancel", "0".white());
    println!();
    
    print!("Pilih: ");
    io::stdout().flush()?;
    
    let mut selection = String::new();
    io::stdin().read_line(&mut selection)?;
    let selection = selection.trim();
    
    if selection.to_lowercase() == "a" {
        print!("{}", "Hapus SEMUA? (y/n): ".red().bold());
        io::stdout().flush()?;
        let mut confirm = String::new();
        io::stdin().read_line(&mut confirm)?;
        
        if confirm.trim().to_lowercase().starts_with('y') {
            for path in &wordlists {
                fs::remove_file(path).ok();
            }
            config.active_wordlist = None;
            config.save()?;
            println!("{}", "✅ Semua dihapus".green());
            std::thread::sleep(std::time::Duration::from_secs(2));
        }
    } else if let Ok(num) = selection.parse::<usize>() {
        if num > 0 && num <= wordlists.len() {
            let selected = &wordlists[num - 1];
            fs::remove_file(selected)?;
            
            // Reset active if deleted
            if let Some(active) = &config.active_wordlist {
                if Path::new(active) == selected {
                    config.active_wordlist = None;
                    config.save()?;
                }
            }
            
            println!("{}", "✅ Dihapus".green());
            std::thread::sleep(std::time::Duration::from_secs(2));
        }
    }
    
    Ok(())
}