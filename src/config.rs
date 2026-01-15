use serde::{Deserialize, Serialize};
use std::fs;
use std::path::PathBuf;

#[derive(Debug, Serialize, Deserialize)]
pub struct Config {
    pub target_host: String,
    pub timeout: u64,
}

impl Default for Config {
    fn default() -> Self {
        Self {
            target_host: String::new(),
            timeout: 10,
        }
    }
}

impl Config {
    pub fn config_path() -> PathBuf {
        // Try Android/Termux path first
        let android_path = PathBuf::from("/sdcard/InjectTools");
        if android_path.exists() || cfg!(target_os = "android") {
            android_path.join("config.toml")
        } else {
            // Use standard config directory
            dirs::config_dir()
                .unwrap_or_else(|| PathBuf::from("."))
                .join("injecttools")
                .join("config.toml")
        }
    }

    pub fn results_dir() -> PathBuf {
        let android_path = PathBuf::from("/sdcard/InjectTools/results");
        if android_path.parent().unwrap().exists() || cfg!(target_os = "android") {
            android_path
        } else {
            dirs::config_dir()
                .unwrap_or_else(|| PathBuf::from("."))
                .join("injecttools")
                .join("results")
        }
    }

    pub fn load_or_create() -> anyhow::Result<Self> {
        let config_path = Self::config_path();
        
        if config_path.exists() {
            let content = fs::read_to_string(&config_path)?;
            Ok(toml::from_str(&content)?)
        } else {
            let config = Self::default();
            config.save()?;
            Ok(config)
        }
    }

    pub fn save(&self) -> anyhow::Result<()> {
        let config_path = Self::config_path();
        
        if let Some(parent) = config_path.parent() {
            fs::create_dir_all(parent)?;
        }
        
        // Also ensure results dir exists
        let results_dir = Self::results_dir();
        fs::create_dir_all(results_dir)?;
        
        let content = toml::to_string_pretty(self)?;
        fs::write(config_path, content)?;
        
        Ok(())
    }
}