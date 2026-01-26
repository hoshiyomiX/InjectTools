//! InjectTools Library
//!
//! This is the library entry point for Android APK integration.
//! When compiled as `cdylib`, it exposes JNI functions for Kotlin/Java.
//!
//! Build targets:
//! - Binary (Termux CLI): cargo build --bin injecttools
//! - Library (Android): cargo build --lib --target aarch64-linux-android

// Public modules
pub mod config;
pub mod scanner;
pub mod dns;
pub mod ui;
pub mod crtsh;
pub mod results;
pub mod http_client;

// JNI bridge (only when building as library)
#[cfg(feature = "jni")]
pub mod jni_bridge;

// Re-export commonly used types
pub use scanner::ScanResult;
pub use config::Config;

// Library version
pub const VERSION: &str = env!("CARGO_PKG_VERSION");

/// Initialize library (called from Android on first load)
pub fn init() {
    // Set up any global state if needed
    // For now, this is a no-op
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_version() {
        assert!(VERSION.starts_with("4.0"));
    }
}
