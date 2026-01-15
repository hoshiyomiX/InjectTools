# InjectTools v2.3

[![Termux Build](https://github.com/hoshiyomiX/InjectTools/actions/workflows/termux-release.yml/badge.svg)](https://github.com/hoshiyomiX/InjectTools/actions/workflows/termux-release.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

**Bug Inject Scanner for Cloudflare Subdomains** - Android/Termux Only

High-performance Rust implementation optimized for Android devices running Termux.

## 🚀 Quick Install

```bash
curl -sSL https://raw.githubusercontent.com/hoshiyomiX/InjectTools/main/install.sh | bash
```

---

## ✨ Features v2.3

### Core Features
- ⚡ **Async Concurrent Scanning** - Powered by Tokio
- 🔍 **DNS Resolution** - Multi-resolver with fallback
- ☁️ **Cloudflare Detection** - Auto-detect CF IP ranges
- 📊 **Real-time Progress** - Live progress bars & statistics
- 💾 **Config Persistence** - TOML-based configuration
- 🌐 **crt.sh Integration** - Automatic subdomain discovery
- 📝 **Export Results** - Save scan results with timestamps
- 📂 **View Results** - Browse previous scan results
- ⏸️ **Signal Handling** - Graceful interrupt (Ctrl+C)

### Menu Options
1. 🎯 **Test Target Host** - Verify target reachability
2. 🔍 **Test Single Subdomain** - Quick single test
3. 🌐 **Fetch & Test dari crt.sh** - Auto-discover subdomains
4. 📊 **View Exported Results** - Browse past scans
5. ⚙️ **Settings** - Configure target & timeout
6. 🚪 **Exit**

---

## Installation

### Requirements
- **Android device** with Termux installed
- **ARM64** (aarch64) or **ARMv7** architecture
- Internet connection

### Method 1: One-Liner (Recommended)

```bash
curl -sSL https://raw.githubusercontent.com/hoshiyomiX/InjectTools/main/install.sh | bash
```

**Features:**
- ✅ Auto-detect latest release
- ✅ Fallback to build from source
- ✅ Auto-install dependencies
- ✅ Backup existing installation

### Method 2: Install Specific Version

```bash
# Install specific release
curl -sSL https://raw.githubusercontent.com/hoshiyomiX/InjectTools/main/install.sh | bash -s termux-v2.3.0

# Or any other version
curl -sSL https://raw.githubusercontent.com/hoshiyomiX/InjectTools/main/install.sh | bash -s v1.1.0
```

### Method 3: Manual Installation

**Step 1: Check Architecture**
```bash
uname -m
# aarch64 = ARM64 (modern devices)
# armv7l/armv8l = ARMv7 (older devices)
```

**Step 2: Download Binary**

**For ARM64:**
```bash
wget https://github.com/hoshiyomiX/InjectTools/releases/latest/download/injecttools-termux-arm64.tar.gz
tar xzf injecttools-termux-arm64.tar.gz
mv injecttools $PREFIX/bin/
chmod +x $PREFIX/bin/injecttools
```

**For ARMv7:**
```bash
wget https://github.com/hoshiyomiX/InjectTools/releases/latest/download/injecttools-termux-armv7.tar.gz
tar xzf injecttools-termux-armv7.tar.gz
mv injecttools $PREFIX/bin/
chmod +x $PREFIX/bin/injecttools
```

**Step 3: Run**
```bash
injecttools
```

### Method 4: Build from Source

```bash
# Install dependencies
pkg install rust git binutils -y

# Clone & build
git clone https://github.com/hoshiyomiX/InjectTools.git
cd InjectTools
cargo build --release --target aarch64-linux-android

# Install
cp target/aarch64-linux-android/release/injecttools $PREFIX/bin/
injecttools --version
```

---

## Usage

### Interactive Mode
```bash
injecttools
```

### CLI Mode

**Test Target Host:**
```bash
injecttools -t tunnel.example.com
```

**Test Single Subdomain:**
```bash
injecttools -t tunnel.example.com -s cdn.cloudflare.com
```

**Fetch from crt.sh & Test:**
```bash
injecttools -t tunnel.example.com -d cloudflare.com --crtsh
```

**View Results:**
```bash
injecttools --view-results
```

### CLI Options
```
Options:
  -t, --target <TARGET>      Target host (tunnel/proxy)
  -d, --domain <DOMAIN>      Domain to scan (with --crtsh)
  -s, --subdomain <SUB>      Test single subdomain
      --crtsh                Fetch from crt.sh
      --timeout <SECS>       Timeout [default: 10]
      --non-interactive      CLI mode only
      --view-results         View scan results
  -h, --help                 Print help
  -V, --version              Print version
```

---

## Configuration

**Config Location:**
```
/sdcard/InjectTools/config.toml
```

**Results Location:**
```
/sdcard/InjectTools/results/
```

**Config Format:**
```toml
target_host = "tunnel.example.com"
timeout = 10
```

**Accessing Files:**
```bash
# View config
cat /sdcard/InjectTools/config.toml

# List results
ls -lh /sdcard/InjectTools/results/

# View latest result
cat /sdcard/InjectTools/results/*.txt | tail -100
```

---

## Output Example

```
════════════════════════════════════════════════════════════
                    HASIL SCAN
════════════════════════════════════════════════════════════

✅ Working Bugs (3):
  🟢 cdn.cloudflare.com (104.16.1.1)
  🟢 api.cloudflare.com (104.16.2.2)
  🟢 static.cloudflare.com (104.16.3.3)

────────────────────────────────────────────────────────────
Statistik:
  Scanned: 250/250 (100%)
  CF Found: 3 | Non-CF: 45
  
File: scan_cloudflare_com_20260115_135530.txt
Path: /sdcard/InjectTools/results/scan_cloudflare_com_20260115_135530.txt
```

---

## What's New in v2.3

✅ **crt.sh Integration** - Automatic subdomain discovery  
✅ **Export Results** - Save scans with timestamps  
✅ **View Results** - Browse past scan results  
✅ **Signal Handling** - Graceful Ctrl+C interrupt  
✅ **Improved UI** - Better progress tracking  
✅ **Settings Menu** - Configure target & timeout  
✅ **Android Optimized** - `/sdcard/InjectTools` storage  
🚨 **BREAKING** - Android/Termux only (Linux/Windows/macOS support removed)  

---

## Performance

| Device | Subdomains | Time | Speed |
|--------|------------|------|-------|
| Snapdragon 8 Gen 2 | 250 subs | ~30s | 8.3 req/s |
| Snapdragon 888 | 250 subs | ~40s | 6.2 req/s |
| Exynos 2100 | 250 subs | ~50s | 5 req/s |
| MediaTek Dimensity 1200 | 250 subs | ~45s | 5.5 req/s |

*Tested with 10s timeout on Termux*

---

## Project Structure

```
InjectTools/
├── src/
│   ├── main.rs        # Entry point & menu
│   ├── config.rs      # Config management
│   ├── scanner.rs     # Scan engine
│   ├── dns.rs         # DNS + CF detection
│   ├── crtsh.rs       # crt.sh integration
│   ├── results.rs     # Export & view results
│   └── ui.rs          # Terminal UI
├── .github/workflows/
│   └── termux-release.yml # Termux build
├── install.sh        # Smart installer
├── Cargo.toml        # Dependencies
└── README.md
```

---

## Troubleshooting

### Installation Issues

**curl not found:**
```bash
pkg install curl
```

**wget not found:**
```bash
pkg install wget
```

**Permission denied:**
```bash
chmod +x $PREFIX/bin/injecttools
```

**Storage permission denied:**
```bash
termux-setup-storage
# Allow storage access when prompted
```

**No release available:**
```bash
# Installer will auto-build from source
# Or manually:
pkg install rust git binutils
git clone https://github.com/hoshiyomiX/InjectTools.git
cd InjectTools
cargo build --release --target aarch64-linux-android
```

### Runtime Issues

**DNS errors:**
```bash
pkg install dnsutils
```

**SSL/TLS errors:**
```bash
pkg install ca-certificates openssl
```

**Wrong architecture:**
```bash
uname -m  # Check your arch
# Download matching binary (arm64 or armv7)
```

**Config file errors:**
```bash
# Remove old config
rm /sdcard/InjectTools/config.toml
# Run again to recreate
injecttools
```

### Build from Source Issues

**Rust not installed:**
```bash
pkg install rust
```

**Out of memory during build:**
```bash
# Use swap file
pkg install tsu
sudo dd if=/dev/zero of=/swapfile bs=1M count=2048
sudo mkswap /swapfile
sudo swapon /swapfile
```

**Compilation errors:**
```bash
# Update packages
pkg update && pkg upgrade
# Clean and rebuild
cargo clean
cargo build --release
```

---

## Device Compatibility

### Supported Architectures
- ✅ **ARM64** (aarch64) - Most devices 2018+
- ✅ **ARMv7** (armv7l) - Older devices 2015-2018

### Tested Devices
- ✅ Samsung Galaxy S21/S22/S23 series
- ✅ Xiaomi Redmi Note series
- ✅ POCO X3/X4/F3/F4 series
- ✅ OnePlus 7/8/9/10 series
- ✅ Realme GT series

### Requirements
- **Termux** app (from F-Droid, not Google Play)
- **Android 7.0+** (API 24+)
- **Storage permission** granted
- **100MB** free space (after installation: ~10MB)

---

## Changelog

### v2.3.0 (2026-01-15)
- ✨ NEW: crt.sh integration for subdomain discovery
- ✨ NEW: Export results to file with timestamps
- ✨ NEW: View exported results
- ✨ NEW: Signal handling (Ctrl+C graceful exit)
- ✨ NEW: Settings menu
- 🐛 FIX: Android /sdcard path support
- 🚀 IMPROVE: Better progress tracking
- 🚀 IMPROVE: Enhanced UI/UX
- 🚨 BREAKING: Android/Termux only (removed Linux/Windows/macOS support)

### v2.0.0 (2026-01-14)
- Initial Rust implementation
- Migration from Bash script
- Async concurrent scanning
- Native Termux binary
- Config persistence

---

## Documentation

- 📖 [README.md](README.md) - This file
- 📱 [BUILD.md](TERMUX_BUILD.md) - Build guide for Termux
- 💾 [install.sh](install.sh) - Installer script

---

## Contributing

Contributions welcome! Submit a Pull Request.

---

## License

MIT License - see [LICENSE](LICENSE)

---

## Credits

**Created by:** [@hoshiyomi_id](https://t.me/hoshiyomi_id)

**Powered by:**
- [Rust](https://www.rust-lang.org/) - Programming language
- [Tokio](https://tokio.rs/) - Async runtime
- [Reqwest](https://github.com/seanmonstar/reqwest) - HTTP client
- [Trust-DNS](https://github.com/bluejekyll/trust-dns) - DNS resolver
- [crt.sh](https://crt.sh) - Certificate transparency logs

---

## Disclaimer

For **educational purposes** and **authorized testing only**.

---

⭐ **Star** this repo if it helps you!

🐛 Report bugs: [Issues](https://github.com/hoshiyomiX/InjectTools/issues)