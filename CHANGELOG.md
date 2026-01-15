# Changelog

All notable changes to InjectTools will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [v2.3.1] - 2026-01-15 (Hotfix)

### Added
- **🔄 crt.sh Retry Mechanism**
  - Auto-retry 3x dengan progressive timeout (30s → 45s → 60s)
  - Better error messages (timeout, connect, parse)
  - Connection timeout 10s untuk early failure detection
  - Improved User-Agent header untuk compatibility

- **💡 DNS Brute Force Fallback**
  - Menu baru: "4. DNS Brute Force Scan"
  - Auto-offer fallback saat crt.sh gagal
  - Interactive prompt untuk switch ke brute force
  - Integrated dengan built-in wordlist

### Changed
- **📡 crt.sh Error Handling**
  - Distinguish error types: timeout vs network vs parse
  - User-friendly error messages dengan tips
  - Automatic fallback suggestion
  
- **🎨 UI Improvements**
  - Added helpful tips saat crt.sh down
  - Better progress feedback dengan attempt counter
  - Clearer menu layout (7 options)

### Fixed
- 🐛 **crt.sh Connection Errors**
  - Fix "unexpected end of file" error
  - Better TLS handshake handling
  - Proper timeout configuration
  - Retry mechanism untuk unstable API

### Technical Details
- **crt.sh Module** (`src/crtsh.rs`)
  - Added `fetch_with_timeout()` helper
  - Progressive retry delays (1s, 2s, 3s)
  - Better validation untuk empty results
  - Improved subdomain filtering logic

- **Main Module** (`src/main.rs`)
  - Integrated wordlist module
  - Added fallback prompt logic
  - Version bump: 2.3.0 → 2.3.1

---

## [v2.3.0] - 2026-01-15

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

## Troubleshooting crt.sh Issues (v2.3.1+)

### Issue: "connection error: unexpected end of file"

**Cause:** crt.sh API sering down/slow atau network timeout

**Solutions:**
1. ✅ **Auto-Retry**: Tool akan retry 3x otomatis
2. ✅ **Fallback**: Gunakan "DNS Brute Force Scan" sebagai alternatif
3. ✅ **Manual**: Coba lagi beberapa menit kemudian

**Alternative Method:**
```bash
# Gunakan menu option 4 untuk DNS brute force
4. 🚀 DNS Brute Force Scan
```

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
