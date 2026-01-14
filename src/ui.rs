use colored::*;
use std::io::{self, Write};

pub fn clear_screen() {
    print!("\x1B[2J\x1B[1;1H");
    io::stdout().flush().ok();
}

pub fn print_header(title: &str) {
    let width = terminal_size::terminal_size()
        .map(|(w, _)| w.0 as usize)
        .unwrap_or(60);
    
    println!("{}", "═".repeat(width).cyan());
    println!("{}", center_text(title, width).white());
    println!("{}", "═".repeat(width).cyan());
}

pub fn print_separator() {
    let width = terminal_size::terminal_size()
        .map(|(w, _)| w.0 as usize)
        .unwrap_or(60);
    println!("{}", "─".repeat(width).cyan());
}

fn center_text(text: &str, width: usize) -> String {
    let text_len = text.len();
    if text_len >= width {
        return text.to_string();
    }
    let padding = (width - text_len) / 2;
    format!("{}{}", " ".repeat(padding), text)
}

pub fn progress_bar(current: usize, total: usize) -> String {
    let percentage = if total > 0 {
        (current * 100) / total
    } else {
        0
    };
    format!("[{:3}%]", percentage)
}