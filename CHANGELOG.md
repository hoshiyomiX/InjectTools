# Changelog

All notable changes to InjectTools will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [v2.3.0] - 2026-01-15

### Added
- **Comprehensive Build Logging System** 📝
  - Auto-capture all build output to timestamped log files
  - Push logs to `.github/build-logs/` in repository
  - Prefix logs with status (`success-*` or `failed-*`)
  - Upload as artifacts with 30-day retention
  - Auto-cleanup: keep last 10 logs per architecture
  - Retry push mechanism (up to 3 attempts)
  - Detailed error reporting for failed builds

- **Batch Subdomain Testing**
  - Manual input mode (one-by-one)
  - File input mode (load from text file)
  - Progress tracking with percentage
  - Scan cancellation support (Ctrl+C)

- **Results Management**
  - View saved scan results
  - Browse/preview result files
  - Delete individual or all results
  - File metadata display (size, timestamp)

- **Enhanced Scanner Features**
  - Ping test with response time
  - crt.sh subdomain enumeration
  - Cloudflare IP detection
  - Real-time progress indicators
  - Detailed scan statistics

### Changed
- **Workflow Improvements**
  - Enhanced error handling
  - Better build step logging
  - Improved artifact management
  - Added build duration tracking

- **UI/UX Enhancements**
  - Better progress visualization
  - Clearer warning messages
  - Improved menu navigation
  - Color-coded status messages

### Fixed
- Build log push conflicts (retry mechanism)
- DNS resolver initialization
- Scanner async/await patterns
- File permission handling

### Documentation
- Added [`.github/build-logs/README.md`](.github/build-logs/README.md)
  - Log format documentation
  - Reading methods (web, git, curl)
  - Debugging guide
  - Example log output
  - Retention policy

---

## [v2.0.0] - 2026-01-14

### Added
- Initial Rust implementation
- Async concurrent scanning with Tokio
- Native Termux binary support
- Interactive & CLI modes
- Config persistence (TOML)
- Built-in wordlist + SecLists integration
- DNS resolution with trust-dns
- HTTP bug inject testing
- Progress bars with indicatif
- Colorful TUI with dialoguer

### Changed
- Migrated from Bash to Rust
- Improved performance (async scanning)
- Better error handling
- Reduced binary size (~5-8 MB)

---

## Build Log Examples

### Success Log
```
.github/build-logs/success-aarch64-20260115-120530.log
```

### Failed Log
```
.github/build-logs/failed-armv7a-20260115-120545.log
```

View all logs: [Build Logs Directory](.github/build-logs/)

---

## Links

- **Repository**: https://github.com/hoshiyomiX/InjectTools
- **Issues**: https://github.com/hoshiyomiX/InjectTools/issues
- **Releases**: https://github.com/hoshiyomiX/InjectTools/releases
- **Telegram**: [@hoshiyomi_id](https://t.me/hoshiyomi_id)

## Legend

- ✨ **Added**: New features
- 🔄 **Changed**: Changes in existing functionality
- 🛠️ **Fixed**: Bug fixes
- 📝 **Documentation**: Documentation updates
- ⚠️ **Deprecated**: Soon-to-be removed features
- 🗑️ **Removed**: Removed features
- 🔒 **Security**: Security fixes
