# Changelog

All notable changes to InjectTools will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [v2.3.2] - 2026-01-15 (Hotfix)

### Fixed
- 🔒 **HTTPS-First Logic**
  - Fixed: Timeout issue pada HTTPS-only servers (contoh: hoshiyomi.qzz.io)
  - Scanner sekarang try HTTPS first, fallback ke HTTP
  - Proper handling 400 Bad Request ("plain HTTP to HTTPS port")
  - Added DNS pre-check sebelum HTTP request
  - Separate `connect_timeout` (5s) dari request timeout

- 🐛 **Test Target Improvements**
  - Better error messages dengan detailed troubleshooting
  - Show protocol used (HTTP/HTTPS) di success output
  - Display port info (80/443) di result summary
  - Detect Cloudflare IP di test target output

- 🛠️ **CLI Mode Enhancement**
  - Support: `injecttools -t host.com --non-interactive` (test target only)
  - Fixed: "Error: Gunakan --subdomain atau --crtsh --domain"
  - CLI sekarang bisa test target tanpa --subdomain/--crtsh flag
  - Improved usage hints untuk invalid commands

### Changed
- 🔧 **Scanner Module** (`src/scanner.rs`)
  - `test_target()`: DNS check → HTTPS → HTTP fallback
  - `test_single()`: HTTPS-first untuk subdomain testing
  - `batch_test()`: HTTPS fallback untuk mass scanning
  - Skip HTTP 400 errors di batch mode

- 📝 **Main Module** (`src/main.rs`)
  - Allow test target without subdomain/domain di CLI
  - Version bump: 2.3.1 → 2.3.2
  - Better error messages dengan usage examples

### Technical Details

**Root Cause:**  
Modern servers (especially behind Cloudflare) often:
- Block HTTP port 80 entirely
- Redirect HTTP → HTTPS (301/302)
- Return 400 "Bad Request" untuk plain HTTP

Tool lama kirim HTTP first → timeout/fail pada HTTPS-only hosts.

**Solution:**  
1. DNS resolution check first (fast fail jika domain invalid)
2. Try HTTPS (port 443) dengan TLS
3. Fallback ke HTTP (port 80) jika HTTPS gagal
4. Skip HTTP 400 errors di batch mode (server wants HTTPS)

**Impact:**  
- ✅ HTTPS-only servers sekarang reachable
- ✅ Faster connection (HTTPS typically succeeds first)
- ✅ Better error messages (protocol/port specific)
- ✅ No false negatives dari 400 errors

**Testing:**  
```bash
# Working sekarang (was timeout before)
injecttools -t hoshiyomi.qzz.io --non-interactive

# Output:
# 🔍 Checking DNS resolution...
# ✓ hoshiyomi.qzz.io → 198.19.137.249
# ☁️ Cloudflare IP detected
# 📡 Testing: https://hoshiyomi.qzz.io
# ✓ Status: 200 via HTTPS
# ✅ TARGET HOST IS REACHABLE
```

**Related Commits:**
- [888e1e4](https://github.com/hoshiyomiX/InjectTools/commit/888e1e41a5ed08cd5437ff8a922bab0f0cc848ee) - scanner.rs HTTPS-first logic
- [a3e86fd](https://github.com/hoshiyomiX/InjectTools/commit/a3e86fd24e768051a34de588bda8ea4c369611ee) - main.rs CLI improvements

---

## [v2.3.1] - 2026-01-15 (Hotfix)

### Fixed
- 🐛 **Android Networking Issues**
  - Fixed: "Cannot connect to internet" error di Termux
  - Switched dari `rustls-tls` ke `native-tls` untuk better Android support
  - Enabled `default-features = true` untuk reqwest (include necessary components)
  - Tool sekarang properly menggunakan system OpenSSL

- 🐛 **Scanner Error Handling**
  - Added detailed error logging untuk debugging
  - HTTP client build status visibility
  - Error type detection (timeout vs connect vs request)
  - User-friendly troubleshooting hints

### Changed
- 🔧 **Dependencies** (`Cargo.toml`)
  - `reqwest`: `rustls-tls` → `native-tls`
  - `reqwest`: `default-features = false` → `default-features = true`
  - Better Android/Termux compatibility

- 📊 **Scanner Module** (`src/scanner.rs`)
  - Enhanced error messages dengan specific failure types
  - Show timeout settings di test output
  - Display HTTP client build status
  - Debug hints untuk connection failures

### Technical Details
**Root Cause:** rustls di Android/Termux kadang tidak bisa establish TLS connections

**Solution:** native-tls menggunakan system OpenSSL (available di Termux via `pkg install openssl`)

**Impact:** 
- ✅ Basic HTTP requests working
- ✅ crt.sh API accessible
- ✅ DNS resolution stable
- ✅ Test target host functional

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

## Menu Structure History

| Version | Menu Options | Features |
|---------|--------------|----------|
| **v2.3.2** | 6 | Test Target (HTTPS-first), Single Test, crt.sh, Results, Settings, Exit |
| v2.3.1 | 6 | Test Target, Single Test, crt.sh, Results, Settings, Exit |
| v2.3.0 | 6 | Same as v2.3.1 |
| v2.0.0 | 8 | Included Full Scan + Batch Test |
| v1.1.0 | 8 | Bash version with all features |

**Note:** "Full Scan" dan "DNS Brute Force" dihapus di v2.3.0 untuk fokus ke crt.sh-based discovery.

---

## Platform Support History

| Version | Android/Termux | Linux | macOS | Windows |
|---------|----------------|-------|-------|----------|
| v2.3.2+ | ✅ Primary | ❌ | ❌ | ❌ |
| v2.3.1 | ✅ Primary | ❌ | ❌ | ❌ |
| v2.3.0 | ✅ Primary | ✅ | ✅ | ✅ |
| v2.0.0 | ✅ | ✅ | ✅ | ✅ |
| v1.x | ✅ | ✅ | ❌ | ❌ |

**Note:** Starting from v2.3.1, InjectTools is Android/Termux exclusive.

---

## Troubleshooting v2.3.2

### Issue: Timeout pada HTTPS-only servers

**Symptoms:**
- `error sending request for url (http://...): operation timed out`
- `⚠️  Timeout: Request exceeded timeout limit`
- Server yang accessible via browser timeout di tool

**Cause:** Server requires HTTPS tapi tool kirim HTTP request

**Solution:** Update ke v2.3.2+ (HTTPS-first logic)
```bash
# Update tool
cd ~/InjectTools
git pull
cargo build --release --target aarch64-linux-android
cp target/aarch64-linux-android/release/injecttools $PREFIX/bin/
```

**Verification:**
```bash
injecttools -t hoshiyomi.qzz.io --non-interactive
# Should show: "✓ Status: 200 via HTTPS"
```

---

### Issue: "Cannot connect to internet" / Network errors (v2.3.1)

**Cause:** Missing OpenSSL atau TLS configuration issue

**Solution:**
```bash
# Install OpenSSL di Termux
pkg install openssl openssl-tool

# Rebuild binary
cd InjectTools
cargo clean
cargo build --release
```

---

### Issue: "Failed to build HTTP client"

**Cause:** Missing system dependencies

**Solution:**
```bash
# Install required packages
pkg update && pkg upgrade
pkg install rust binutils openssl

# Verify installation
openssl version
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
