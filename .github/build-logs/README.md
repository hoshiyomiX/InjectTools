# Build Logs

Folder ini berisi log dari setiap build Termux yang dilakukan via GitHub Actions.

## 📝 Format Log File

```
{status}-{arch}-{timestamp}.log
```

**Contoh:**
- `success-aarch64-20260115-120530.log` - Build sukses untuk ARM64
- `failed-armv7a-20260115-120545.log` - Build gagal untuk ARMv7

## 📁 Log Contents

Setiap log file berisi:

### Header
- Target architecture (aarch64/armv7a)
- Android API level
- Rust & Cargo versions
- Trigger info (tag/manual)
- Commit SHA
- Build start timestamp

### Build Steps
1. 📦 **NDK Download** - Download Android NDK r26d
2. ⚙️ **Environment Setup** - Configure linkers & compilers
3. 🔨 **Cargo Build** - Compile Rust code
4. ✂️ **Binary Stripping** - Remove debug symbols
5. 🧪 **Testing** - Validate binary
6. 📦 **Compression** - Create tar.gz archive
7. 🔐 **Checksums** - Generate SHA256

### Footer
- Build status (success/failed)
- Duration
- Error details (jika failed)
- Artifact list

## 🔍 Reading Logs

### Via GitHub Web

1. Buka [build-logs folder](https://github.com/hoshiyomiX/InjectTools/tree/main/.github/build-logs)
2. Klik file log yang ingin dibaca
3. View content atau download

### Via Git Clone

```bash
git clone https://github.com/hoshiyomiX/InjectTools.git
cd InjectTools/.github/build-logs
ls -lt  # List logs by date
cat success-aarch64-*.log  # View latest ARM64 success
```

### Via Raw URL

```bash
# Latest log for ARM64
curl -s https://raw.githubusercontent.com/hoshiyomiX/InjectTools/main/.github/build-logs/success-aarch64-{timestamp}.log
```

## 📈 Log Retention

- **Repository**: Last 10 logs per architecture (auto-cleanup)
- **Artifacts**: 30 days retention
- **Older logs**: Available di [Actions artifacts](https://github.com/hoshiyomiX/InjectTools/actions)

## 🐛 Debugging dengan Logs

### Build Failed

1. Cari log dengan prefix `failed-{arch}-`
2. Check error section:
   ```
   [HH:MM:SS] ❌ Build failed with exit code: 101
   ```
3. Scroll up untuk lihat error details
4. Common issues:
   - **Dependency errors**: Check Cargo.toml
   - **Linker errors**: Check NDK configuration
   - **Compilation errors**: Check source code

### Build Success tapi Binary Error

1. Cari log dengan prefix `success-{arch}-`
2. Check sections:
   - **Binary size**: Pastikan tidak terlalu kecil/besar
   - **File command**: Verify ELF format
   - **Strip results**: Check savings

## 🔗 Related Files

- [termux-release.yml](../workflows/termux-release.yml) - Workflow file
- [release.yml](../workflows/release.yml) - Full platform release
- [Cargo.toml](../../Cargo.toml) - Dependencies
- [TERMUX_BUILD.md](../../TERMUX_BUILD.md) - Build guide

## 📊 Statistics

Log files help track:
- Build success rate
- Compilation times
- Binary size trends
- Error patterns
- NDK compatibility

## 🔄 Log Rotation

Workflow otomatis cleanup:
```bash
ls -t *-{arch}-*.log | tail -n +11 | xargs rm
```

Keep only last 10 per architecture untuk save space.

## 📝 Log Format Example

```
╔══════════════════════════════════════════════════════════════╗
║          InjectTools Termux Build Log                        ║
╚══════════════════════════════════════════════════════════════╝

Build Information:
------------------
Target: aarch64-linux-android
Architecture: aarch64
Android API: 30
Runner OS: ubuntu-latest
Rust Version: rustc 1.75.0
Cargo Version: cargo 1.75.0

Triggered by: workflow_dispatch
Commit SHA: abc123def456
Ref: refs/heads/main
Actor: hoshiyomiX

Started at: 2026-01-15 04:05:30 UTC

══════════════════════════════════════════════════════════════

[04:05:35] 📦 Downloading Android NDK r26d...
[04:05:45] 📂 Extracting NDK...
[04:06:20] ✅ NDK installed at: /home/runner/work/android-ndk-r26d

[04:06:21] ⚙️ Configuring Android build environment...
[04:06:22] ✅ Environment variables configured

[04:06:23] 🔨 Starting cargo build for aarch64-linux-android...

   Compiling injecttools v2.3.0 (/home/runner/work/InjectTools)
    Finished release [optimized] target(s) in 2m 15s

[04:08:38] ✅ Build completed successfully

Build artifacts:
-rwxr-xr-x 1 runner 8567432 Jan 15 04:08 injecttools

[04:08:39] ✂️ Stripping debug symbols...
Binary size before strip: 8.2MiB
Binary size after strip: 5.8MiB
Space saved: 2.4MiB

[04:08:40] 🧪 Testing binary validity...

injecttools: ELF 64-bit LSB shared object, ARM aarch64

-rwxr-xr-x 1 runner 6094112 Jan 15 04:08 injecttools

[04:08:40] ✅ Binary is valid

[04:08:41] 📦 Compressing binary...
Compressed size: 2.1MiB
-rw-r--r-- 1 runner 2215936 Jan 15 04:08 injecttools-termux-arm64.tar.gz

[04:08:42] 🔐 Generating SHA256 checksum...
f3e4d2c1a9b8... injecttools-termux-arm64.tar.gz

══════════════════════════════════════════════════════════════

Build Summary:
--------------
Status: success
Finished at: 2026-01-15 04:08:43 UTC
Duration: 193 seconds

✅ Build completed successfully!

Artifacts:
  - injecttools-termux-arm64.tar.gz
  - injecttools-termux-arm64.tar.gz.sha256

╔══════════════════════════════════════════════════════════════╗
║                    End of Build Log                          ║
╚══════════════════════════════════════════════════════════════╝
```

---

**Created by:** [@hoshiyomi_id](https://t.me/hoshiyomi_id)  
**Auto-generated** by GitHub Actions Workflow
