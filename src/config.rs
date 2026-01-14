use anyhow::Result;
use serde::{Deserialize, Serialize};
use std::fs;
use std::path::PathBuf;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Config {
    pub target_host: String,
    pub default_subdomain: String,
    pub default_domain: String,
    pub timeout: u64,
    pub active_wordlist: Option<String>,
}

impl Default for Config {
    fn default() -> Self {
        Self {
            target_host: String::new(),
            default_subdomain: String::new(),
            default_domain: String::new(),
            timeout: 10,
            active_wordlist: None,
        }
    }
}

impl Config {
    pub fn config_path() -> Result<PathBuf> {
        let home = dirs::home_dir()
            .ok_or_else(|| anyhow::anyhow!("Could not find home directory"))?;
        Ok(home.join(".config").join("injecttools").join("config.toml"))
    }

    pub fn load() -> Result<Self> {
        let path = Self::config_path()?;
        let content = fs::read_to_string(path)?;
        Ok(toml::from_str(&content)?)
    }

    pub fn save(&self) -> Result<()> {
        let path = Self::config_path()?;
        if let Some(parent) = path.parent() {
            fs::create_dir_all(parent)?;
        }
        let content = toml::to_string_pretty(self)?;
        fs::write(path, content)?;
        Ok(())
    }
}

pub fn extract_domain(subdomain: &str) -> String {
    let subdomain = subdomain.trim_start_matches("http://").trim_start_matches("https://");
    let parts: Vec<&str> = subdomain.split('.').collect();
    
    if parts.len() >= 2 {
        format!("{}.{}", parts[parts.len() - 2], parts[parts.len() - 1])
    } else {
        subdomain.to_string()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_extract_domain() {
        assert_eq!(extract_domain("cdn.example.com"), "example.com");
        assert_eq!(extract_domain("api.sub.example.com"), "example.com");
        assert_eq!(extract_domain("https://cdn.example.com"), "example.com");
        assert_eq!(extract_domain("example.com"), "example.com");
    }
}