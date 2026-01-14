# Termux Build Guide

## 🚀 Quick Start - Trigger Termux Build

### Method 1: Via GitHub UI (Termudah)

1. Buka workflow: [Termux Build & Release](https://github.com/hoshiyomiX/InjectTools/actions/workflows/termux-release.yml)
2. Klik tombol **"Run workflow"** (dropdown kanan atas)
3. Pilih branch: `main`
4. Input version: `termux-v1.1.0`
5. Klik **"Run workflow"** (button hijau)

### Method 2: Via Git Tag

```bash
# Clone repo
git clone https://github.com/hoshiyomiX/InjectTools.git
cd InjectTools

# Create & push tag (prefix dengan 'termux-')
git tag termux-v1.1.0
git push origin termux-v1.1.0
```

**⏱️ Build Time:** ~5-8 menit (jauh lebih cepat dari full release)

---

## 📦 Build Targets

Workflow akan build 2 binary untuk Termux:

| Target | Architecture | Devices |
|--------|--------------|----------|
| `aarch64-linux-android` | ARM64 | Modern Android (2018+), Snapdragon 845+, Exynos 9810+ |
| `armv7-linux-androideabi` | ARMv7 | Older Android (2015-2018), Snapdragon 660-, Exynos 8895- |

**Cek architecture device kamu:**
```bash
uname -m
# aarch64 = ARM64 (gunakan arm64 binary)
# armv7l/armv8l = ARMv7 (gunakan armv7 binary)
```

---

## 📊 Build Process

### Steps yang dilakukan workflow:

1. ✅ **Checkout code**
2. ✅ **Install Rust toolchain** dengan Android targets
3. ✅ **Cache dependencies** (cargo registry, index, build)
4. ✅ **Download Android NDK r26d** (~500 MB)
5. ✅ **Configure cross-compilation** (linker, ar)
6. ✅ **Build release binary** (`cargo build --release`)
7. ✅ **Strip debug symbols** (reduce size)
8. ✅ **Compress to tar.gz**
9. ✅ **Generate SHA256 checksums**
10. ✅ **Create GitHub Release** with assets

### Build Output:

```
injecttools-termux-arm64.tar.gz          (~5-6 MB)
injecttools-termux-arm64.tar.gz.sha256   (checksum)
injecttools-termux-armv7.tar.gz          (~5-6 MB)
injecttools-termux-armv7.tar.gz.sha256   (checksum)
checksums.txt                             (combined)
```

---

## 🔍 Monitor Build

### Check build progress:

1. Buka [Actions tab](https://github.com/hoshiyomiX/InjectTools/actions)
2. Klik workflow run yang sedang berjalan
3. Expand job "Build for Termux" untuk detail logs

### Build Success:

✅ Green checkmark di Actions  
✅ Release tersedia di [Releases page](https://github.com/hoshiyomiX/InjectTools/releases)

### Build Failed:

❌ Red X di Actions  
📋 Klik job yang failed untuk lihat error logs

**Common Issues:**
- **Dependency error**: Check Cargo.toml dependencies
- **NDK download timeout**: Re-run workflow
- **Linking error**: Check cargo config.toml

---

## 📥 Installation Setelah Build

### ARM64 (Recommended)

```bash
# Download dari GitHub Releases
cd ~
wget https://github.com/hoshiyomiX/InjectTools/releases/download/termux-v1.1.0/injecttools-termux-arm64.tar.gz

# Verify checksum (optional tapi recommended)
wget https://github.com/hoshiyomiX/InjectTools/releases/download/termux-v1.1.0/injecttools-termux-arm64.tar.gz.sha256
sha256sum -c injecttools-termux-arm64.tar.gz.sha256

# Extract
tar xzf injecttools-termux-arm64.tar.gz

# Move to bin
mv injecttools $PREFIX/bin/

# Make executable
chmod +x $PREFIX/bin/injecttools

# Test
injecttools --version
injecttools --help

# Run
injecttools
```

### ARMv7 (Older Devices)

```bash
# Download
wget https://github.com/hoshiyomiX/InjectTools/releases/download/termux-v1.1.0/injecttools-termux-armv7.tar.gz

# Extract & Install
tar xzf injecttools-termux-armv7.tar.gz
mv injecttools $PREFIX/bin/
chmod +x $PREFIX/bin/injecttools

# Run
injecttools
```

---

## 🔧 Local Build (Termux)

Jika mau build sendiri di Termux:

### Install Dependencies

```bash
# Update packages
pkg update && pkg upgrade

# Install Rust
pkg install rust binutils

# Install build tools
pkg install git clang
```

### Clone & Build

```bash
# Clone repository
git clone https://github.com/hoshiyomiX/InjectTools.git
cd InjectTools

# Build release (native, tanpa cross-compile)
cargo build --release

# Binary ada di:
ls -lh target/release/injecttools

# Install
cp target/release/injecttools $PREFIX/bin/

# Run
injecttools
```

**Build time di Termux:**  
- Snapdragon 8 Gen 2: ~5-8 menit  
- Snapdragon 860: ~10-15 menit  
- Snapdragon 660: ~20-30 menit

---

## 🆚 Workflow Comparison

| Workflow | Platforms | Build Time | Use Case |
|----------|-----------|------------|----------|
| `release.yml` | Linux, Android, Windows, macOS (8 targets) | ~15-20 min | Full release semua platform |
| `termux-release.yml` | Android only (2 targets) | ~5-8 min | **Termux-focused, cepat** |

**Recommendation:** Pakai `termux-release.yml` untuk development/testing di Termux

---

## 🐛 Troubleshooting

### Build Errors

**Error: NDK download timeout**
```yaml
# Re-run workflow dari GitHub UI
# Atau tunggu beberapa menit lalu retry
```

**Error: Linking failed**
```bash
# Check cargo config di workflow
# Pastikan NDK path benar
```

**Error: Out of disk space**
```bash
# GitHub runners punya 14GB disk
# Rust build butuh ~3-4GB
# Biasanya tidak terjadi, re-run workflow
```

### Installation Errors (Termux)

**Permission denied**
```bash
chmod +x $PREFIX/bin/injecttools
```

**Binary tidak jalan (Exec format error)**
```bash
# Download binary yang salah architecture
# Cek lagi dengan: uname -m
# Download yang sesuai (arm64 atau armv7)
```

**DNS resolution errors**
```bash
pkg install dnsutils
```

**SSL/TLS errors**
```bash
pkg install ca-certificates openssl
```

---

## 📝 Release Versioning

**Format:** `termux-v{MAJOR}.{MINOR}.{PATCH}`

**Examples:**
- `termux-v1.0.0` - Initial release
- `termux-v1.1.0` - New features
- `termux-v1.1.1` - Bug fixes

**Update version di:**
1. `Cargo.toml` → `version = "1.1.0"`
2. Git tag → `termux-v1.1.0`

---

## 📊 Binary Size

| Stage | ARM64 Size | ARMv7 Size |
|-------|------------|------------|
| Debug build | ~15 MB | ~14 MB |
| Release build | ~8 MB | ~7.5 MB |
| After strip | ~5.5 MB | ~5 MB |
| Compressed (.tar.gz) | ~2 MB | ~1.8 MB |

**Optimizations applied:**
- `opt-level = "z"` - Size optimization
- `lto = true` - Link-time optimization
- `strip = true` - Remove debug symbols
- `codegen-units = 1` - Better optimization

---

## 🚀 Next Steps

1. **Trigger build** (pilih method di atas)
2. **Monitor progress** di Actions tab
3. **Download binary** dari Releases
4. **Test di Termux**
5. **Report bugs** jika ada issue

---

## 🔗 Links

- **Workflow File**: [`.github/workflows/termux-release.yml`](.github/workflows/termux-release.yml)
- **Actions**: [GitHub Actions](https://github.com/hoshiyomiX/InjectTools/actions/workflows/termux-release.yml)
- **Releases**: [GitHub Releases](https://github.com/hoshiyomiX/InjectTools/releases)
- **Issues**: [Report Issues](https://github.com/hoshiyomiX/InjectTools/issues)

---

**Created by:** [@hoshiyomi_id](https://t.me/hoshiyomi_id)  
**Optimized for:** Termux on Android
