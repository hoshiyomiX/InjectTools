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
    content.push_str(&format!("InjectTools v3.6 - Scan Results\n"));
    content.push_str(&format!("Domain: {}\n", domain));
    content.push_str(&format!("Timestamp: {}\n", Local::now().format("%Y-%m-%d %H:%M:%S")));
    content.push_str(&format!("\n{}\n\n", "=".repeat(70)));
    
    // Categorize results
    let working: Vec<_> = results.iter().filter(|r| r.is_working).collect();
    let restricted: Vec<_> = results.iter().filter(|r| r.is_restricted).collect();
    let subdomain_issues: Vec<_> = results.iter()
        .filter(|r| !r.is_working && !r.is_restricted && r.error_source.as_deref() == Some("subdomain"))
        .collect();
    let target_issues: Vec<_> = results.iter()
        .filter(|r| !r.is_working && !r.is_restricted && r.error_source.as_deref() == Some("target"))
        .collect();
    
    // ═══════════════════════════════════════════════════════════════
    // 1. WORKING BUGS
    // ═══════════════════════════════════════════════════════════════
    
    content.push_str(&format!("✅ WORKING BUG INJECTS ({}):\n", working.len()));
    content.push_str(&format!("{}\n\n", "-".repeat(70)));
    
    if working.is_empty() {
        content.push_str("No fully working bugs found\n");
    } else {
        for result in &working {
            content.push_str(&format!(
                "✓ {}\n  IP: {} | HTTP: {}",
                result.subdomain,
                result.ip,
                result.status_code.unwrap_or(0)
            ));
            
            if let Some(ref cf_ray) = result.cf_ray {
                content.push_str(&format!(" | CF-Ray: {}", cf_ray));
            }
            
            content.push_str("\n\n");
        }
    }
    
    // ═══════════════════════════════════════════════════════════════
    // 2. RESTRICTED ACCESS (HTTP 403/404)
    // ═══════════════════════════════════════════════════════════════
    
    content.push_str(&format!("\n⚠️  WORKING WITH RESTRICTIONS ({}):\n", restricted.len()));
    content.push_str(&format!("{}\n", "-".repeat(70)));
    content.push_str("Note: Connection works, but access is limited\n\n");
    
    if restricted.is_empty() {
        content.push_str("None\n");
    } else {
        for result in &restricted {
            content.push_str(&format!(
                "⚠ {}\n  IP: {} | HTTP: {} ({})",
                result.subdomain,
                result.ip,
                result.status_code.unwrap_or(0),
                match result.status_code {
                    Some(403) => "Forbidden - May need authentication",
                    Some(404) => "Not Found - Try specific paths",
                    _ => "Restricted"
                }
            ));
            
            if let Some(ref cf_ray) = result.cf_ray {
                content.push_str(&format!(" | CF-Ray: {}", cf_ray));
            }
            
            content.push_str("\n\n");
        }
    }
    
    // ═══════════════════════════════════════════════════════════════
    // 3. SUBDOMAIN ISSUES
    // ═══════════════════════════════════════════════════════════════
    
    content.push_str(&format!("\n❌ SUBDOMAIN ISSUES ({}):\n", subdomain_issues.len()));
    content.push_str(&format!("{}\n", "-".repeat(70)));
    content.push_str("Note: Problems with subdomain itself (DNS, non-CF IP, SSL failed)\n\n");
    
    if subdomain_issues.is_empty() {
        content.push_str("None\n");
    } else {
        for result in &subdomain_issues {
            content.push_str(&format!(
                "✗ {}\n  IP: {} | Error: {}\n\n",
                result.subdomain,
                result.ip,
                result.error_msg.as_ref().unwrap_or(&"Unknown".to_string())
            ));
        }
    }
    
    // ═══════════════════════════════════════════════════════════════
    // 4. TARGET ISSUES
    // ═══════════════════════════════════════════════════════════════
    
    content.push_str(&format!("\n🎯 TARGET ISSUES ({}):\n", target_issues.len()));
    content.push_str(&format!("{}\n", "-".repeat(70)));
    content.push_str("Note: Subdomain OK, but target domain offline/misconfigured\n\n");
    
    if target_issues.is_empty() {
        content.push_str("None\n");
    } else {
        for result in &target_issues {
            content.push_str(&format!(
                "• {}\n  IP: {} (Cloudflare) | Target Response: HTTP {}\n\n",
                result.subdomain,
                result.ip,
                result.status_code.unwrap_or(0)
            ));
        }
    }
    
    // ═══════════════════════════════════════════════════════════════
    // STATISTICS
    // ═══════════════════════════════════════════════════════════════
    
    content.push_str(&format!("\n{}\n", "=".repeat(70)));
    content.push_str(&format!("STATISTICS:\n"));
    content.push_str(&format!("{}\n", "-".repeat(70)));
    content.push_str(&format!("Total Scanned:        {}\n", results.len()));
    content.push_str(&format!("\n"));
    content.push_str(&format!("✅ Working Bugs:       {} ({:.1}%)\n", 
        working.len(), 
        (working.len() as f64 / results.len().max(1) as f64 * 100.0)
    ));
    content.push_str(&format!("⚠️  Restricted Access:  {} ({:.1}%)\n", 
        restricted.len(),
        (restricted.len() as f64 / results.len().max(1) as f64 * 100.0)
    ));
    content.push_str(&format!("\n"));
    content.push_str(&format!("❌ Subdomain Issues:   {}\n", subdomain_issues.len()));
    content.push_str(&format!("🎯 Target Issues:      {}\n", target_issues.len()));
    content.push_str(&format!("\n"));
    content.push_str(&format!("Success Rate:         {:.1}%\n", 
        ((working.len() + restricted.len()) as f64 / results.len().max(1) as f64 * 100.0)
    ));
    
    // ═══════════════════════════════════════════════════════════════
    // RECOMMENDATIONS
    // ═══════════════════════════════════════════════════════════════
    
    content.push_str(&format!("\n{}\n", "=".repeat(70)));
    content.push_str(&format!("RECOMMENDATIONS:\n"));
    content.push_str(&format!("{}\n", "-".repeat(70)));
    
    if !working.is_empty() {
        content.push_str(&format!("✓ {} working bug(s) ready to use\n", working.len()));
    }
    
    if !restricted.is_empty() {
        content.push_str(&format!("⚠ {} bug(s) with restrictions - connection works, try specific endpoints\n", restricted.len()));
    }
    
    if !target_issues.is_empty() {
        content.push_str(&format!("🎯 {} subdomain(s) OK but target offline - try different target domain\n", target_issues.len()));
    }
    
    if working.is_empty() && restricted.is_empty() {
        content.push_str(&format!("❌ No working bugs found\n"));
        content.push_str(&format!("   Try: Different target domain or subdomain source\n"));
    }
    
    content.push_str(&format!("\n{}\n", "=".repeat(70)));
    content.push_str(&format!("\nExport by InjectTools v3.6\n"));
    content.push_str(&format!("https://github.com/hoshiyomiX/InjectTools\n"));
    
    fs::write(&filepath, content)?;
    
    // Console output
    println!("\n{}", "═".repeat(60).green());
    println!("{}", "📁 RESULTS EXPORTED".green().bold());
    println!("{}", "═".repeat(60).green());
    println!("\n{} {}", "File:".bright_black(), filename.green());
    println!("{} {}", "Path:".bright_black(), filepath.display().to_string().bright_black());
    println!("\n{}", "Summary:".bold());
    println!("  {} {} working", "✅".green(), working.len().to_string().green());
    
    if !restricted.is_empty() {
        println!("  {} {} restricted", "⚠️".yellow(), restricted.len().to_string().yellow());
    }
    
    if !subdomain_issues.is_empty() {
        println!("  {} {} subdomain issues", "❌".red(), subdomain_issues.len().to_string().red());
    }
    
    if !target_issues.is_empty() {
        println!("  {} {} target issues", "🎯".yellow(), target_issues.len().to_string().yellow());
    }
    
    println!("");
    
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
        
        println!("\n{}", "═".repeat(70).cyan());
        println!("{}", content);
        println!("{}", "═".repeat(70).cyan());
    }
    
    Ok(())
}
