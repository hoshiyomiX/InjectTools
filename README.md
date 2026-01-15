# InjectTools v2.3

[![Termux Build](https://github.com/hoshiyomiX/InjectTools/actions/workflows/termux-release.yml/badge.svg)](https://github.com/hoshiyomiX/InjectTools/actions/workflows/termux-release.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

**Bug Inject Scanner for Cloudflare Subdomains** - High-performance Rust implementation

## 🚀 Quick Install (Termux)

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
- 📦 **Batch Testing** - Test from file (one subdomain per line)
- 📝 **Export Results** - Save scan results with timestamps
- 📂 **View Results** - Browse previous scan results
- ⏸️ **Signal Handling** - Graceful interrupt (Ctrl+C)

### Menu Options
1. 🎯 **Test Target Host** - Verify target reachability
2. 🔍 **Test Single Subdomain** - Quick single test
3. 🌐 **Fetch & Test dari crt.sh** - Auto-discover subdomains
4. 📦 **Batch Test dari File** - Bulk testing
5. 🚀 **Full Domain Scan** - Scan common subdomains
6. 📊 **View Exported Results** - Browse past scans
7. ⚙️ **Settings** - Configure target & timeout
8. 🚪 **Exit**

### Platform Support
- 📱 Android/Termux (ARM64, ARMv7)
- 💻 Linux (x86_64, ARM64, ARMv7)
- 💙 Windows (x86_64)
- 🍎 macOS (Intel & Apple Silicon)

---

## Installation

### Termux (Recommended)

**Method 1: One-Liner (Auto-install)**
```bash
curl -sSL https://raw.githubusercontent.com/hoshiyomiX/InjectTools/main/install.sh | bash
```

**Method 2: Manual**
```bash
# Check architecture
uname -m  # aarch64=ARM64, armv7l=ARMv7

# ARM64 (Modern devices)
wget https://github.com/hoshiyomiX/InjectTools/releases/latest/download/injecttools-termux-arm64.tar.gz
tar xzf injecttools-termux-arm64.tar.gz && mv injecttools $PREFIX/bin/ && chmod +x $PREFIX/bin/injecttools

# ARMv7 (Older devices)
wget https://github.com/hoshiyomiX/InjectTools/releases/latest/download/injecttools-termux-armv7.tar.gz
tar xzf injecttools-termux-armv7.tar.gz && mv injecttools $PREFIX/bin/ && chmod +x $PREFIX/bin/injecttools

# Run
injecttools
```

### Linux
```bash
wget https://github.com/hoshiyomiX/InjectTools/releases/latest/download/injecttools-linux-x86_64.tar.gz
tar xzf injecttools-linux-x86_64.tar.gz
sudo mv injecttools /usr/local/bin/
injecttools
```

### Build from Source
```bash
git clone https://github.com/hoshiyomiX/InjectTools.git
cd InjectTools
cargo build --release
./target/release/injecttools
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

**Batch Test:**
```bash
injecttools -t tunnel.example.com -b subdomains.txt
```

**Full Scan:**
```bash
injecttools -t tunnel.example.com -d cloudflare.com
```

**View Results:**
```bash
injecttools --view-results
```

### CLI Options
```
Options:
  -t, --target <TARGET>      Target host (tunnel/proxy)
  -d, --domain <DOMAIN>      Domain to scan
  -s, --subdomain <SUB>      Test single subdomain
  -b, --batch <FILE>         Batch test file
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
- **Termux:** `/sdcard/InjectTools/config.toml`
- **Linux/macOS:** `~/.config/injecttools/config.toml`
- **Windows:** `%USERPROFILE%\.config\injecttools\config.toml`

**Results Location:**
- **Termux:** `/sdcard/InjectTools/results/`
- **Linux/macOS:** `~/.config/injecttools/results/`
- **Windows:** `%USERPROFILE%\.config\injecttools\results\`

**Config Format:**
```toml
target_host = "tunnel.example.com"
timeout = 10
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
✅ **Batch Testing** - Test from file input  
✅ **Export Results** - Save scans with timestamps  
✅ **View Results** - Browse past scan results  
✅ **Signal Handling** - Graceful Ctrl+C interrupt  
✅ **Improved UI** - Better progress tracking  
✅ **Settings Menu** - Configure target & timeout  
✅ **Android Path Support** - `/sdcard/InjectTools` storage  

---

## Performance

| Device | Subdomains | Time | Speed |
|--------|------------|------|-------|
| Snapdragon 8 Gen 2 | 250 subs | ~30s | 8.3 req/s |
| Exynos 2100 | 250 subs | ~50s | 5 req/s |
| Linux VPS (4 core) | 250 subs | ~20s | 12.5 req/s |

*Tested with 10s timeout*

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
│   ├── release.yml        # Multi-platform build
│   └── termux-release.yml # Termux-only (fast)
├── install.sh        # One-liner installer
├── Cargo.toml        # Dependencies
└── README.md
```

---

## Build Your Own

### Termux Build (Fast)

**Via GitHub UI:**
1. Go to [Termux Workflow](https://github.com/hoshiyomiX/InjectTools/actions/workflows/termux-release.yml)
2. Click "Run workflow"
3. Version: `termux-v2.3.0`

**Via Git Tag:**
```bash
git tag termux-v2.3.0
git push origin termux-v2.3.0
```

See [TERMUX_BUILD.md](TERMUX_BUILD.md) for details.

---

## Troubleshooting

### Termux Issues

**curl not found:**
```bash
pkg install curl
```

**Permission denied:**
```bash
chmod +x $PREFIX/bin/injecttools
```

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

### Build from Source

**Missing Rust:**
```bash
# Termux
pkg install rust

# Linux
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
```

---

## Documentation

- 📖 [README.md](README.md) - This file
- 📱 [TERMUX_BUILD.md](TERMUX_BUILD.md) - Build guide
- 🚀 [RELEASE.md](RELEASE.md) - Multi-platform release
- 💾 [install.sh](install.sh) - Installer script

---

## Changelog

### v2.3.0 (2026-01-15)
- ✨ NEW: crt.sh integration for subdomain discovery
- ✨ NEW: Batch testing from file
- ✨ NEW: Export results to file with timestamps
- ✨ NEW: View exported results
- ✨ NEW: Signal handling (Ctrl+C graceful exit)
- ✨ NEW: Settings menu
- 🐛 FIX: Android /sdcard path support
- 🚀 IMPROVE: Better progress tracking
- 🚀 IMPROVE: Enhanced UI/UX

### v1.1.0 (2026-01-14)
- Initial Rust implementation
- Basic scanning features
- Config persistence

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