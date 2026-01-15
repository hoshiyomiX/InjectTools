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
    /// Get config path - Android/Termux only
    pub fn config_path() -> PathBuf {
        PathBuf::from("/sdcard/InjectTools/config.toml")
    }

    /// Get results directory - Android/Termux only
    pub fn results_dir() -> PathBuf {
        PathBuf::from("/sdcard/InjectTools/results")
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
        
        // Create /sdcard/InjectTools directory
        if let Some(parent) = config_path.parent() {
            fs::create_dir_all(parent)?;
        }
        
        // Create results directory
        let results_dir = Self::results_dir();
        fs::create_dir_all(results_dir)?;
        
        let content = toml::to_string_pretty(self)?;
        fs::write(config_path, content)?;
        
        Ok(())
    }
}