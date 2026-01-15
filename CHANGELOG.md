# Changelog

All notable changes to InjectTools will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [v2.3.1] - 2026-01-15

### Removed
- 🚀 **Android/Termux Focused** - Removed Linux/macOS/Windows platform support
  - Simplified codebase for Android-only deployment
  - Removed multi-platform workflows
  - Android-optimized storage paths (`/sdcard/InjectTools`)
  - Dedicated Termux build pipeline

### Changed
- 📚 **Documentation** - Updated all docs for Android-only focus
  - `README.md` - Removed non-Android installation methods
  - `TERMUX_BUILD.md` renamed to `BUILD.md`
  - `RELEASE.md` removed (multi-platform guide)
  - Updated troubleshooting for Android-specific issues

---

## [v2.3.0] - 2026-01-15

### Added
- **crt.sh Integration** 🌐
  - Automatic subdomain discovery via certificate transparency logs
  - Fetch and test subdomains in one workflow
  - JSON API parsing with error handling

- **Batch Testing** 📦
  - Load subdomains from text file (one per line)
  - Progress tracking with live updates
  - Scan interruption support (Ctrl+C)

- **Results Management** 📂
  - Export scan results with timestamps
  - View saved scan results interactively
  - Browse result files with metadata
  - Auto-save to `/sdcard/InjectTools/results/`

- **Signal Handling** ⏸️
  - Graceful Ctrl+C interrupt
  - Auto-save partial results on exit
  - Cleanup on termination

- **Settings Menu** ⚙️
  - Configure target host
  - Adjust timeout settings
  - Persistent configuration

- **Comprehensive Build Logging System** 📝
  - Auto-capture all build output to timestamped log files
  - Push logs to `.github/build-logs/` in repository
  - Prefix logs with status (`success-*` or `failed-*`)
  - Upload as artifacts with 30-day retention
  - Auto-cleanup: keep last 10 logs per architecture
  - Retry push mechanism (up to 3 attempts)
  - Detailed error reporting for failed builds

### Changed
- **UI/UX Enhancements** 🎨
  - Better progress visualization
  - Clearer warning messages
  - Improved menu navigation
  - Color-coded status messages
  - Real-time scan statistics

- **Workflow Improvements** 🔧
  - Enhanced error handling
  - Better build step logging
  - Improved artifact management
  - Added build duration tracking

### Fixed
- 🐛 Build log push conflicts (retry mechanism)
- 🐛 DNS resolver initialization
- 🐛 Scanner async/await patterns
- 🐛 File permission handling on Android
- 🐛 Storage access for `/sdcard` directory

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
- **Initial Rust Implementation** 🦀
  - Migrated from Bash script to Rust
  - Async concurrent scanning with Tokio
  - Native Termux binary support (ARM64 + ARMv7)
  - Zero external dependencies (statically linked)

- **Core Features**
  - Interactive & CLI modes
  - Config persistence (TOML format)
  - Built-in wordlist + SecLists integration
  - DNS resolution with trust-dns
  - Cloudflare IP detection (15 IP ranges)
  - HTTP bug inject testing
  - Real-time progress bars (indicatif)
  - Colorful TUI (dialoguer + colored)

- **Android Optimizations**
  - Native ARM compilation
  - Termux-specific binary targets
  - Small footprint (~5-8 MB stripped)
  - `/sdcard` storage support

### Changed
- 🚀 **Performance** - 10x faster than Bash version
  - Async concurrent DNS resolution
  - Parallel HTTP testing
  - Efficient memory usage

- 🎯 **Reliability** - Better error handling
  - Type-safe Rust implementation
  - Proper error propagation
  - Graceful failure recovery

### Technical Details
- **Language**: Bash → Rust
- **Runtime**: Sequential → Async (Tokio)
- **Binary Size**: N/A → 5-8 MB (stripped)
- **Dependencies**: External (curl, dig) → Statically linked
- **Platforms**: Linux/Termux → Android/Termux focused

---

## Platform Support History

| Version | Android/Termux | Linux | macOS | Windows |
|---------|----------------|-------|-------|----------|
| v2.3.1+ | ✅ Primary | ❌ | ❌ | ❌ |
| v2.3.0 | ✅ Primary | ✅ | ✅ | ✅ |
| v2.0.0 | ✅ | ✅ | ✅ | ✅ |
| v1.x | ✅ | ✅ | ❌ | ❌ |

**Note:** Starting from v2.3.1, InjectTools is Android/Termux exclusive.

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
