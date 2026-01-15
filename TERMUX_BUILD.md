# Termux Build Guide

## 🚀 Quick Start - Trigger Build

### Method 1: Via GitHub UI (Termudah)

1. Buka workflow: [Termux Build & Release](https://github.com/hoshiyomiX/InjectTools/actions/workflows/termux-release.yml)
2. Klik tombol **"Run workflow"** (dropdown kanan atas)
3. Pilih branch: `main`
4. Input version: `termux-v2.3.1`
5. Klik **"Run workflow"** (button hijau)

### Method 2: Via Git Tag

```bash
# Clone repo
git clone https://github.com/hoshiyomiX/InjectTools.git
cd InjectTools

# Create & push tag (prefix dengan 'termux-')
git tag termux-v2.3.1
git push origin termux-v2.3.1
```

**⏱️ Build Time:** ~5-8 menit

---

## 📦 Build Targets

Workflow akan build 2 binary untuk Termux:

| Target | Architecture | Devices | Chipsets |
|--------|--------------|---------|----------|
| `aarch64-linux-android` | ARM64 | Modern (2018+) | Snapdragon 845+, Exynos 9810+, Dimensity 700+ |
| `armv7-linux-androideabi` | ARMv7 | Older (2015-2018) | Snapdragon 660-, Exynos 8895-, Helio P60- |

**Cek architecture device:**
```bash
uname -m
# aarch64 = ARM64 (gunakan arm64 binary)
# armv7l/armv8l = ARMv7 (gunakan armv7 binary)
```

---

## 📊 Build Process

### Workflow Steps:

1. ✅ **Create build log directory**
   - `build-logs/build-{target}.log` untuk capture semua output

2. ✅ **Checkout code** dari repository

3. ✅ **Install Rust toolchain**
   - Rust stable dengan Android target support
   - Log: rustc version, cargo version, installed targets

4. ✅ **Cache cargo dependencies**
   - Registry, git, compiled artifacts
   - Dramatically speeds up subsequent builds

5. ✅ **Download Android NDK r26d** (~500 MB)
   - Logged: Download progress, extraction, NDK location

6. ✅ **Configure Android toolchain**
   - Set CC, AR, LINKER environment variables
   - Log: NDK binary path, API level, toolchain config

7. ✅ **Build release binary**
   - `cargo build --release --target {target}`
   - **FULL BUILD OUTPUT CAPTURED** to log file
   - Log: Build start time, all cargo output, build status, finish time

8. ✅ **Strip debug symbols**
   - `llvm-strip` to reduce binary size
   - Log: Before size, after size, bytes saved, percentage

9. ✅ **Package to tar.gz**
   - Create compressed archive
   - Generate SHA256 checksum
   - Log: Package size, checksum

10. ✅ **Compress build logs**
    - `gzip -9` for maximum compression
    - Uploaded as release asset

11. ✅ **Create GitHub Release**
    - Upload binaries (.tar.gz)
    - Upload checksums (.sha256)
    - Upload build logs (.log.gz)
    - Generate release notes

### Build Output Files:

```
release-assets/
├── injecttools-termux-arm64.tar.gz              (~2-3 MB)
├── injecttools-termux-arm64.tar.gz.sha256       (checksum)
├── build-aarch64-linux-android.log.gz          (build log)
├── injecttools-termux-armv7.tar.gz              (~2-3 MB)
├── injecttools-termux-armv7.tar.gz.sha256       (checksum)
├── build-armv7-linux-androideabi.log.gz        (build log)
└── checksums.txt                                (combined checksums)
```

---

## 📝 Build Logs

### What's Logged:

**System Info:**
- Build start/finish timestamps (UTC)
- Target architecture
- Runner OS info

**Rust Toolchain:**
- rustc version
- cargo version
- Installed targets

**Android NDK:**
- Download status
- Extraction status
- NDK location

**Toolchain Config:**
- NDK binary path
- API level
- Environment variables (CC, AR, LINKER)

**Build Output:**
- **Complete cargo build output** (all warnings, errors, dependencies)
- Compilation progress
- Link time
- Final binary info (size, file type)

**Strip Statistics:**
- Before strip size
- After strip size
- Bytes saved
- Percentage reduced

**Package Info:**
- Tar.gz size
- SHA256 checksum

### Accessing Build Logs:

**Option 1: From GitHub Release**
```bash
# Download log dari release
wget https://github.com/hoshiyomiX/InjectTools/releases/download/termux-v2.3.1/build-aarch64-linux-android.log.gz

# Extract & view
gunzip build-aarch64-linux-android.log.gz
less build-aarch64-linux-android.log

# Or view compressed
zless build-aarch64-linux-android.log.gz
```

**Option 2: From GitHub Actions UI**
1. Go to [Actions tab](https://github.com/hoshiyomiX/InjectTools/actions)
2. Click on workflow run
3. Click on job (Build for Termux)
4. View real-time logs
5. Download log archive (top-right menu)

---

## 🔍 Monitor Build

### Real-time Progress:

1. Go to [Actions](https://github.com/hoshiyomiX/InjectTools/actions)
2. Click on running workflow
3. Watch live logs for each step

### Build Status Indicators:

✅ **Success** - Green checkmark  
🟡 **In Progress** - Yellow dot spinning  
❌ **Failed** - Red X  
⏸️ **Cancelled** - Grey circle  

### Common Issues:

**NDK Download Timeout:**
- Retry workflow from GitHub UI
- Usually temporary network issue

**Compilation Error:**
- Check build log for exact error
- Usually missing dependency or syntax error
- Look for "error[E...]" in logs

**Linking Error:**
- Check NDK configuration
- Verify API level compatibility

**Out of Disk Space:**
- GitHub runners have 14GB disk
- Rare, usually indicates cache issue
- Clear caches and retry

---

## 📥 Installation Setelah Build

### ARM64 (Recommended - Modern Devices)

```bash
# Download dari GitHub Releases
cd ~
wget https://github.com/hoshiyomiX/InjectTools/releases/download/termux-v2.3.1/injecttools-termux-arm64.tar.gz

# Verify checksum (optional tapi recommended)
wget https://github.com/hoshiyomiX/InjectTools/releases/download/termux-v2.3.1/injecttools-termux-arm64.tar.gz.sha256
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
wget https://github.com/hoshiyomiX/InjectTools/releases/download/termux-v2.3.1/injecttools-termux-armv7.tar.gz

# Extract & Install
tar xzf injecttools-termux-armv7.tar.gz
mv injecttools $PREFIX/bin/
chmod +x $PREFIX/bin/injecttools

# Run
injecttools
```

---

## 🔧 Local Build (Termux)

Jika ingin build sendiri di device:

### Install Dependencies

```bash
# Update packages
pkg update && pkg upgrade

# Install Rust
pkg install rust binutils clang

# Verify installation
rustc --version
cargo --version
```

### Clone & Build

```bash
# Clone repository
git clone https://github.com/hoshiyomiX/InjectTools.git
cd InjectTools

# Build release (native, tanpa cross-compile)
cargo build --release

# Binary location
ls -lh target/release/injecttools

# Install
cp target/release/injecttools $PREFIX/bin/

# Run
injecttools
```

**Build time di Termux:**

| Device | CPU | Time |
|--------|-----|------|
| Snapdragon 8 Gen 2 | 8 cores | ~5-8 min |
| Snapdragon 860 | 8 cores | ~10-15 min |
| Exynos 2100 | 8 cores | ~12-18 min |
| Snapdragon 660 | 8 cores | ~20-30 min |
| Helio P60 | 8 cores | ~25-35 min |

---

## 🆚 Workflow Comparison

| Workflow | Platforms | Build Time | Logs | Use Case |
|----------|-----------|------------|------|----------|
| `termux-release.yml` | Android (2 targets) | ~5-8 min | ✅ Full | **Termux-focused, fast** |
| `release.yml` | All platforms (8 targets) | ~15-20 min | Basic | Full multi-platform |

**Recommendation:** Use `termux-release.yml` untuk development/testing di Termux.

---

## 🐛 Troubleshooting

### Build Errors

**Error: Dependency conflict**
```bash
# Check Cargo.toml dependencies
# Verify compatible versions
# Look for "error: failed to resolve dependencies" in log
```

**Error: NDK download failed**
```bash
# Re-run workflow from GitHub UI
# Usually temporary CDN issue
```

**Error: Compilation failed**
```bash
# Download build log:
wget https://github.com/hoshiyomiX/InjectTools/releases/download/termux-v2.3.1/build-aarch64-linux-android.log.gz
gunzip build-aarch64-linux-android.log.gz

# Search for error:
grep "error\[E" build-aarch64-linux-android.log
```

**Error: Linking failed**
```bash
# Check NDK configuration in workflow
# Verify API level (30) compatibility
# Look for "ld: error:" in logs
```

### Installation Errors (Termux)

**Permission denied:**
```bash
chmod +x $PREFIX/bin/injecttools
```

**Binary tidak jalan (Exec format error):**
```bash
# Downloaded wrong architecture
uname -m  # Check your arch

# Download correct binary:
# aarch64 -> injecttools-termux-arm64.tar.gz
# armv7l -> injecttools-termux-armv7.tar.gz
```

**DNS resolution errors:**
```bash
pkg install dnsutils
```

**SSL/TLS errors:**
```bash
pkg install ca-certificates openssl
```

**curl command not found:**
```bash
pkg install curl
```

---

## 📝 Release Versioning

**Format:** `termux-v{MAJOR}.{MINOR}.{PATCH}`

**Examples:**
- `termux-v2.3.0` - Major release (new features)
- `termux-v2.3.1` - Bug fix release
- `termux-v2.4.0` - Minor feature update

**Version Update Steps:**

1. Update `Cargo.toml`:
   ```toml
   [package]
   version = "2.3.1"
   ```

2. Update `src/main.rs`:
   ```rust
   #[command(version = "2.3.1")]
   ```

3. Update `README.md` version references

4. Commit changes:
   ```bash
   git add -A
   git commit -m "Bump version to 2.3.1"
   git push
   ```

5. Create & push tag:
   ```bash
   git tag termux-v2.3.1
   git push origin termux-v2.3.1
   ```

---

## 📊 Binary Size Optimization

| Stage | ARM64 | ARMv7 | Notes |
|-------|-------|-------|-------|
| Debug build | ~15 MB | ~14 MB | With debug symbols |
| Release build | ~8 MB | ~7.5 MB | Optimized |
| After strip | ~5.5 MB | ~5 MB | Debug symbols removed |
| Compressed | ~2 MB | ~1.8 MB | gzip compression |

**Cargo.toml optimizations:**
```toml
[profile.release]
opt-level = "z"      # Optimize for size
lto = true           # Link-time optimization
codegen-units = 1    # Better optimization, slower compile
strip = true         # Strip symbols at compile time
panic = "abort"      # Smaller panic handler
```

---

## 🚀 Next Steps

1. **Trigger build** via GitHub UI atau git tag
2. **Monitor progress** di Actions tab
3. **Download build logs** jika ada error
4. **Verify checksums** sebelum install
5. **Test binary** di Termux
6. **Report bugs** di Issues jika ada masalah

---

## 🔗 Useful Links

- **Workflow File**: [`.github/workflows/termux-release.yml`](.github/workflows/termux-release.yml)
- **Actions Dashboard**: [GitHub Actions](https://github.com/hoshiyomiX/InjectTools/actions/workflows/termux-release.yml)
- **Latest Release**: [Releases](https://github.com/hoshiyomiX/InjectTools/releases/latest)
- **Report Issues**: [Issues](https://github.com/hoshiyomiX/InjectTools/issues)
- **Main README**: [README.md](README.md)

---

## 💬 Support

**Questions?** Open an issue or contact:  
**Telegram:** [@hoshiyomi_id](https://t.me/hoshiyomi_id)

---

**Optimized for:** Termux on Android  
**Build System:** GitHub Actions + Android NDK r26d  
**Rust Version:** Stable (latest)