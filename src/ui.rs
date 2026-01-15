use colored::Colorize;
use std::io::{self, Write};

pub fn clear_screen() {
    print!("\x1B[2J\x1B[1;1H");
    io::stdout().flush().unwrap();
}

pub fn center_text(text: &str) {
    let width = terminal_size::terminal_size()
        .map(|(w, _)| w.0 as usize)
        .unwrap_or(60);
    
    let text_len = text.len();
    let padding = if width > text_len {
        (width - text_len) / 2
    } else {
        0
    };
    
    println!("{}{}", " ".repeat(padding), text.bold());
}

pub fn print_header(title: &str) {
    let width = terminal_size::terminal_size()
        .map(|(w, _)| w.0 as usize)
        .unwrap_or(60);
    
    println!("{}", "═".repeat(width).cyan());
    center_text(title);
    println!("{}", "═".repeat(width).cyan());
}

pub fn read_line() -> String {
    io::stdout().flush().unwrap();
    let mut input = String::new();
    io::stdin().read_line(&mut input).unwrap();
    input.trim().to_string()
}

pub fn pause() {
    print!("\n{}", "Tekan Enter untuk lanjut...".bright_black());
    read_line();
}