use crate::config::Config;
use crate::scanner::ScanResult;
use chrono::Local;
use colored::Colorize;
use std::fs;

pub fn export_results(results: &[ScanResult], domain: &str) -> anyhow::Result<()> {
    let results_dir = Config::results_dir();
    fs::create_dir_all(&results_dir)?;
    
    let timestamp = Local::now().format("%Y%m%d_%H%M%S");
    let filename = format!("scan_{}_{}.txt", domain.replace(".", "_"), timestamp);
    let filepath = results_dir.join(&filename);
    
    let mut content = String::new();
    content.push_str(&format!("InjectTools v2.3 - Scan Results\n"));
    content.push_str(&format!("Domain: {}\n", domain));
    content.push_str(&format!("Timestamp: {}\n", Local::now().format("%Y-%m-%d %H:%M:%S")));
    content.push_str(&format!("\n{}\n\n", "=".repeat(60)));
    
    let working: Vec<_> = results.iter().filter(|r| r.is_working).collect();
    let non_cf: Vec<_> = results.iter().filter(|r| !r.is_cloudflare && r.status_code.is_some()).collect();
    
    content.push_str(&format!("WORKING BUGS ({}):\n", working.len()));
    content.push_str(&format!("{}\n\n", "-".repeat(60)));
    
    if working.is_empty() {
        content.push_str("No working bugs found\n");
    } else {
        for result in &working {
            content.push_str(&format!(
                "✓ {} | {} | Status: {}\n",
                result.subdomain,
                result.ip,
                result.status_code.unwrap_or(0)
            ));
        }
    }
    
    content.push_str(&format!("\n\nNON-CLOUDFLARE RESPONSES ({}):\n", non_cf.len()));
    content.push_str(&format!("{}\n\n", "-".repeat(60)));
    
    for result in &non_cf {
        content.push_str(&format!(
            "• {} | {} | Status: {}\n",
            result.subdomain,
            result.ip,
            result.status_code.unwrap_or(0)
        ));
    }
    
    content.push_str(&format!("\n\nSTATISTICS:\n"));
    content.push_str(&format!("{}\n", "-".repeat(60)));
    content.push_str(&format!("Total Scanned: {}\n", results.len()));
    content.push_str(&format!("Working Bugs: {}\n", working.len()));
    content.push_str(&format!("Non-CF: {}\n", non_cf.len()));
    
    fs::write(&filepath, content)?;
    
    println!("\n{}", "═".repeat(60).green());
    println!("{}", "📁 RESULTS EXPORTED".green().bold());
    println!("{}", "═".repeat(60).green());
    println!("\n{} {}", "File:".bright_black(), filename.green());
    println!("{} {}", "Path:".bright_black(), filepath.display().to_string().bright_black());
    println!("{} {} bugs\n", "Working:".bright_black(), working.len().to_string().green());
    
    Ok(())
}

pub fn view_results() -> anyhow::Result<()> {
    let results_dir = Config::results_dir();
    
    if !results_dir.exists() {
        println!("\n{}", "📂 No results directory found".yellow());
        return Ok(());
    }
    
    let entries = fs::read_dir(&results_dir)?;
    let mut files: Vec<_> = entries
        .filter_map(|e| e.ok())
        .filter(|e| {
            e.path()
                .extension()
                .and_then(|s| s.to_str())
                .map(|s| s == "txt")
                .unwrap_or(false)
        })
        .collect();
    
    if files.is_empty() {
        println!("\n{}", "📂 No scan results found".yellow());
        return Ok(());
    }
    
    files.sort_by_key(|e| {
        e.metadata()
            .and_then(|m| m.modified())
            .unwrap_or(std::time::SystemTime::UNIX_EPOCH)
    });
    files.reverse();
    
    crate::ui::print_header("EXPORTED RESULTS");
    println!("\n{} {} files\n", "Total:".bright_black(), files.len().to_string().yellow());
    
    for (idx, entry) in files.iter().enumerate().take(10) {
        let filename = entry.file_name();
        let metadata = entry.metadata()?;
        let modified = metadata.modified()?;
        let datetime: chrono::DateTime<chrono::Local> = modified.into();
        
        println!(
            "{}. {} {}",
            (idx + 1).to_string().cyan(),
            filename.to_string_lossy().green(),
            format!("({})", datetime.format("%Y-%m-%d %H:%M")).bright_black()
        );
    }
    
    if files.len() > 10 {
        println!("\n... and {} more files", files.len() - 10);
    }
    
    println!("\n{} {}", "Directory:".bright_black(), results_dir.display().to_string().bright_black());
    
    // Offer to view latest
    println!("\n{}", "View latest result? (y/n)".bold());
    let choice = crate::ui::read_line();
    
    if choice.trim().eq_ignore_ascii_case("y") {
        let latest = &files[0];
        let content = fs::read_to_string(latest.path())?;
        
        println!("\n{}", "═".repeat(60).cyan());
        println!("{}", content);
        println!("{}", "═".repeat(60).cyan());
    }
    
    Ok(())
}