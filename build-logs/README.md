# Build Logs

Folder ini berisi **detailed build logs** dari setiap Termux build yang di-trigger via GitHub Actions.

## 📋 Purpose

Build logs di-commit ke repository (bukan artifact) untuk:

✅ **Transparency** - Full visibility dari proses build  
✅ **Debugging** - Trace compile errors atau warnings  
✅ **Audit Trail** - Historical record dari setiap build  
✅ **Reproducibility** - Verify exact build conditions  

## 📁 Log Files

Setiap build menghasilkan 2 log files:

- `build-aarch64-linux-android.log` - ARM64 build log
- `build-armv7-linux-androideabi.log` - ARMv7 build log

## 📊 Log Contents

Setiap log file berisi:

1. **Build Metadata**
   - Timestamp (UTC)
   - Target architecture
   - Runner OS info

2. **Rust Toolchain**
   - `rustc` version
   - `cargo` version
   - Installed targets

3. **Android NDK**
   - NDK version (r26d)
   - Download & extraction logs
   - NDK path

4. **Toolchain Configuration**
   - Compiler paths (clang)
   - Linker configuration
   - API level

5. **Build Output**
   - Full `cargo build` output
   - Compilation warnings/errors
   - Dependency resolution
   - Build timing

6. **Binary Info**
   - File size (before/after strip)
   - File type
   - Saved space from stripping

7. **Packaging**
   - tar.gz creation
   - SHA256 checksum
   - Package size

## 🔍 Example Log Entry

```
Build started at 2026-01-15 06:30:42 UTC
Target: aarch64-linux-android
Runner OS: Linux
========================================

📦 Rust Toolchain
========================================
rustc 1.75.0 (82e1608df 2023-12-21)
cargo 1.75.0 (1d8b05cdd 2023-11-20)
aarch64-linux-android

📥 Downloading Android NDK
========================================
Downloading NDK r26d...
Extracting NDK...
NDK location: /home/runner/work/InjectTools/android-ndk-r26d
✅ NDK ready

🔧 Configuring Toolchain
========================================
NDK Binary Path: /home/runner/.../bin
API Level: 30
✅ Toolchain configured

🔨 Building Binary
========================================
Target: aarch64-linux-android
Build started: 2026-01-15 06:31:15 UTC

   Compiling injecttools v2.3.0
    Finished release [optimized] target(s) in 4m 23s

✅ Build successful
Build finished: 2026-01-15 06:35:38 UTC

📊 Binary Info:
-rwxr-xr-x 1 runner docker 8.2M injecttools
injecttools: ELF 64-bit LSB shared object, ARM aarch64

✂️  Stripping Debug Symbols
========================================
Before strip: 8.2M
After strip:  3.1M
Saved:        5.1M (62%)
✅ Strip complete

📦 Packaging
========================================
fd8a3b... injecttools-termux-arm64.tar.gz
Package size: 2.8M
✅ Packaging complete

========================================
Build finished at 2026-01-15 06:36:02 UTC
```

## 🔄 Automatic Updates

Logs are automatically committed by GitHub Actions after each successful build:

1. Build completes
2. Log file generated with full output
3. GitHub Actions bot commits log to this folder
4. Latest logs always available in `main` branch

## 📖 Viewing Logs

**Via GitHub:**
- Browse directly: [build-logs/](https://github.com/hoshiyomiX/InjectTools/tree/main/build-logs)
- Click on any `.log` file to view

**Via Git:**
```bash
git clone https://github.com/hoshiyomiX/InjectTools.git
cd InjectTools/build-logs
cat build-aarch64-linux-android.log
```

**Via Raw URL:**
```bash
curl https://raw.githubusercontent.com/hoshiyomiX/InjectTools/main/build-logs/build-aarch64-linux-android.log
```

## ⚠️ Important Notes

- Logs are **plain text** (not compressed)
- Logs are **overwritten** on each new build
- Only **latest build logs** are kept (not historical)
- Average log size: **50-200 KB** per file

## 🔗 Related

- [GitHub Actions Workflow](../.github/workflows/termux-release.yml)
- [Build Documentation](../TERMUX_BUILD.md)
- [Latest Release](https://github.com/hoshiyomiX/InjectTools/releases/latest)

---

**Last Updated:** Auto-updated by GitHub Actions  
**Maintained by:** [@hoshiyomi_id](https://t.me/hoshiyomi_id)