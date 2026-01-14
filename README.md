# InjectTools

[![Build and Release](https://github.com/hoshiyomiX/InjectTools/actions/workflows/release.yml/badge.svg)](https://github.com/hoshiyomiX/InjectTools/actions/workflows/release.yml)
[![Termux Build](https://github.com/hoshiyomiX/InjectTools/actions/workflows/termux-release.yml/badge.svg)](https://github.com/hoshiyomiX/InjectTools/actions/workflows/termux-release.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

**Bug Inject Scanner for Cloudflare Subdomains** - High-performance Rust implementation

## 🚀 Quick Install (Termux)

```bash
curl -sSL https://raw.githubusercontent.com/hoshiyomiX/InjectTools/main/install.sh | bash
```

**Features:**
- ✅ Auto-detect architecture (ARM64/ARMv7)
- ✅ Download & verify checksum
- ✅ Install ke `$PREFIX/bin`
- ✅ Auto backup & cleanup

---

## Features

✨ **Core Features:**
- ⚡ Async concurrent scanning dengan Tokio
- 🔍 DNS resolution dengan fallback ke multiple resolvers
- ☁️ Auto-detection Cloudflare IP ranges
- 📊 Progress bar real-time dengan statistik
- 💾 Config persistence (TOML format)
- 📥 Download wordlist dari SecLists (5K - 110K patterns)
- 📝 Export hasil scan ke file
- 🛠️ Interactive TUI & CLI mode

💻 **Cross-Platform:**
- Linux (x86_64, ARM64, ARMv7)
- Android/Termux (ARM64, ARMv7)
- Windows (x86_64)
- macOS (Intel & Apple Silicon)

## Installation

### Android/Termux

**Method 1: One-Liner (Recommended)**

```bash
curl -sSL https://raw.githubusercontent.com/hoshiyomiX/InjectTools/main/install.sh | bash
```

**Method 2: Manual One-Liner**

```bash
# Check architecture first
uname -m  # aarch64=ARM64, armv7l/armv8l=ARMv7

# ARM64 (Modern devices - Snapdragon 845+, Exynos 9810+)
wget https://github.com/hoshiyomiX/InjectTools/releases/latest/download/injecttools-termux-arm64.tar.gz && tar xzf injecttools-termux-arm64.tar.gz && mv injecttools $PREFIX/bin/ && chmod +x $PREFIX/bin/injecttools && injecttools --version

# ARMv7 (Older devices - Snapdragon 660-, Exynos 8895-)
wget https://github.com/hoshiyomiX/InjectTools/releases/latest/download/injecttools-termux-armv7.tar.gz && tar xzf injecttools-termux-armv7.tar.gz && mv injecttools $PREFIX/bin/ && chmod +x $PREFIX/bin/injecttools && injecttools --version
```

**Method 3: Step-by-Step**

```bash
# Download binary
wget https://github.com/hoshiyomiX/InjectTools/releases/latest/download/injecttools-termux-arm64.tar.gz

# Extract
tar xzf injecttools-termux-arm64.tar.gz

# Install
mv injecttools $PREFIX/bin/
chmod +x $PREFIX/bin/injecttools

# Run
injecttools
```

### Linux

```bash
# Download
wget https://github.com/hoshiyomiX/InjectTools/releases/latest/download/injecttools-linux-x86_64.tar.gz

# Extract & install
tar xzf injecttools-linux-x86_64.tar.gz
sudo mv injecttools /usr/local/bin/

# Run
injecttools
```

### Build from Source

```bash
# Clone repository
git clone https://github.com/hoshiyomiX/InjectTools.git
cd InjectTools

# Build release
cargo build --release

# Binary ada di target/release/injecttools
./target/release/injecttools
```

## Usage

### Interactive Mode (Default)

```bash
injecttools
```

First-time setup akan memandu kamu mengatur:
- Target host (tunnel/proxy domain)
- Default subdomain untuk quick test
- Wordlist preferences

### CLI Mode

```bash
# Test single subdomain
injecttools -t tunnel.example.com -s cdn.cloudflare.com

# Full scan dengan custom wordlist
injecttools -t tunnel.example.com -d cloudflare.com -w ./custom-wordlist.txt

# Non-interactive scan
injecttools --non-interactive -t tunnel.example.com -d cloudflare.com
```

### CLI Options

```
Options:
  -t, --target <TARGET>        Target host (tunnel/proxy domain)
  -d, --domain <DOMAIN>        Domain to scan
  -s, --subdomain <SUBDOMAIN>  Test single subdomain
  -w, --wordlist <WORDLIST>    Wordlist file path
      --timeout <TIMEOUT>      Timeout in seconds [default: 10]
      --non-interactive        Skip interactive mode
  -h, --help                   Print help
  -V, --version                Print version
```

## Configuration

Config file location:
- Linux/macOS: `~/.config/injecttools/config.toml`
- Android (Termux): `$HOME/.config/injecttools/config.toml`
- Windows: `%USERPROFILE%\.config\injecttools\config.toml`

Example config:

```toml
target_host = "your-tunnel.com"
default_subdomain = "cdn.example.com"
default_domain = "example.com"
timeout = 10
active_wordlist = "/home/user/bug-wordlists/seclists-110k.txt"
```

## Wordlists

Tool ini support multiple wordlist sources:

1. **Embedded** - Built-in 80 common patterns (default)
2. **SecLists Download** - Via menu:
   - Small: 5,000 subdomain patterns (~90 KB)
   - Medium: 20,000 subdomain patterns (~350 KB)
   - Large: 110,000 subdomain patterns (~2 MB)
3. **Custom** - Import wordlist kamu sendiri

Wordlists disimpan di `~/bug-wordlists/`

## Output Example

```
══════════════════════════════════════════════════════════
                    HASIL SCAN
══════════════════════════════════════════════════════════

✅ Working Bugs (3):
  🟢 cdn.example.com (104.16.1.1)
  🟢 api.example.com (104.16.2.2)
  🟢 static.example.com (104.16.3.3)

──────────────────────────────────────────────────────────
Statistik:
  Scanned: 110000/110000 (100%)
  CF Found: 3 | Non-CF: 250
  Waktu: 45s
```

## Performance

Benchmark pada device berbeda:

| Device | Wordlist Size | Scan Time | Speed |
|--------|---------------|-----------|-------|
| Snapdragon 8 Gen 2 | 110K patterns | ~2 min | 916 req/s |
| Exynos 2100 | 110K patterns | ~3.5 min | 523 req/s |
| Linux VPS (4 core) | 110K patterns | ~1.5 min | 1,222 req/s |

*Test dengan timeout 10s, concurrent limit default*

## Development

### Project Structure

```
InjectTools/
├── src/
│   ├── main.rs        # Entry point & menu system
│   ├── config.rs      # Config management
│   ├── scanner.rs     # Scanning engine
│   ├── dns.rs         # DNS resolver
│   ├── wordlist.rs    # Wordlist manager
│   └── ui.rs          # TUI helpers
├── wordlists/
│   └── embedded.txt  # Default wordlist
├── .github/
│   └── workflows/
│       ├── release.yml        # Full multi-platform build
│       └── termux-release.yml # Termux-only build (faster)
├── install.sh        # One-liner installer
├── Cargo.toml
└── README.md
```

### Build Your Own Release

**Termux Build (Fast - ~5-8 min):**
```bash
# Via GitHub UI
# 1. Go to: https://github.com/hoshiyomiX/InjectTools/actions/workflows/termux-release.yml
# 2. Click "Run workflow"
# 3. Input: termux-v1.1.0

# Or via Git Tag
git tag termux-v1.1.0
git push origin termux-v1.1.0
```
See [TERMUX_BUILD.md](TERMUX_BUILD.md) for detailed guide.

**Full Release (All Platforms - ~15-20 min):**
```bash
git tag v1.1.0
git push origin v1.1.0
```
See [RELEASE.md](RELEASE.md) for detailed guide.

## Troubleshooting

### Termux Issues

**Error: curl not found**
```bash
pkg install curl
```

**Error: Permission denied**
```bash
chmod +x $PREFIX/bin/injecttools
```

**Error: DNS resolution failed**
```bash
pkg install dnsutils
```

**Error: TLS/SSL errors**
```bash
pkg install ca-certificates openssl
```

**Error: Binary tidak jalan (Exec format error)**
```bash
# Check architecture
uname -m

# Download binary yang sesuai:
# aarch64 -> use arm64 binary
# armv7l/armv8l -> use armv7 binary
```

### Build Issues

**Missing dependencies (Termux)**
```bash
pkg install rust binutils clang
```

**Missing dependencies (Debian/Ubuntu)**
```bash
sudo apt install build-essential pkg-config libssl-dev
```

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## Documentation

- 📖 [README.md](README.md) - Main documentation (this file)
- 📱 [TERMUX_BUILD.md](TERMUX_BUILD.md) - Termux build guide
- 🚀 [RELEASE.md](RELEASE.md) - Full release guide
- 💾 [install.sh](install.sh) - One-liner installer script

## License

MIT License - see [LICENSE](LICENSE) file for details

## Credits

**Created by:** [@hoshiyomi_id](https://t.me/hoshiyomi_id)

**Powered by:**
- [Rust](https://www.rust-lang.org/) - Programming language
- [Tokio](https://tokio.rs/) - Async runtime
- [Reqwest](https://github.com/seanmonstar/reqwest) - HTTP client
- [Trust-DNS](https://github.com/bluejekyll/trust-dns) - DNS resolver
- [SecLists](https://github.com/danielmiessler/SecLists) - Wordlists

## Disclaimer

Tool ini dibuat untuk **educational purposes** dan **authorized testing only**.

---

**⭐ Star** repository ini jika kamu merasa terbantu!

Report bugs atau request features di [Issues](https://github.com/hoshiyomiX/InjectTools/issues)