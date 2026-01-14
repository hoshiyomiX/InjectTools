# Release Guide

## Cara Trigger Release Build

Ada 2 cara untuk trigger automated build & release:

### Method 1: Via Git Tag (Recommended)

```bash
# Clone repo
git clone https://github.com/hoshiyomiX/InjectTools.git
cd InjectTools

# Create & push tag
git tag v1.1.0
git push origin v1.1.0
```

**Workflow akan otomatis:**
1. Build untuk semua platform (Linux, Android, Windows, macOS)
2. Compress binaries
3. Generate checksums
4. Create GitHub Release
5. Upload semua artifacts

### Method 2: Manual Trigger via GitHub UI

1. Buka https://github.com/hoshiyomiX/InjectTools/actions/workflows/release.yml
2. Klik tombol **"Run workflow"** (dropdown di kanan atas)
3. Pilih branch: `main`
4. Input version: `v1.1.0`
5. Klik **"Run workflow"** (button hijau)

## Build Status

Cek progress build di:
- https://github.com/hoshiyomiX/InjectTools/actions

Build membutuhkan waktu ~15-20 menit untuk complete semua platform.

## Setelah Build Selesai

Release akan tersedia di:
- https://github.com/hoshiyomiX/InjectTools/releases

Download binaries sesuai platform:
- **Android (Termux)**: `injecttools-android-aarch64.tar.gz`
- **Linux**: `injecttools-linux-x86_64.tar.gz`
- **Windows**: `injecttools-windows-x86_64.exe.zip`
- **macOS**: `injecttools-macos-aarch64.tar.gz`

## Instalasi Post-Release

### Android/Termux

```bash
wget https://github.com/hoshiyomiX/InjectTools/releases/download/v1.1.0/injecttools-android-aarch64.tar.gz
tar xzf injecttools-android-aarch64.tar.gz
mv injecttools $PREFIX/bin/
chmod +x $PREFIX/bin/injecttools
injecttools
```

### Linux

```bash
wget https://github.com/hoshiyomiX/InjectTools/releases/download/v1.1.0/injecttools-linux-x86_64.tar.gz
tar xzf injecttools-linux-x86_64.tar.gz
sudo mv injecttools /usr/local/bin/
injecttools
```

## Troubleshooting Build

Jika build gagal:

1. **Check Logs**: Klik pada failed job di Actions tab
2. **Common Issues**:
   - Dependency conflicts: Update Cargo.toml
   - Cross-compilation errors: Check target configuration
   - Android NDK issues: Workflow akan auto-download NDK

3. **Re-trigger**:
   ```bash
   # Delete tag
   git tag -d v1.1.0
   git push origin :refs/tags/v1.1.0
   
   # Re-create after fix
   git tag v1.1.0
   git push origin v1.1.0
   ```

## Versioning

Follow Semantic Versioning:
- **Major** (v2.0.0): Breaking changes
- **Minor** (v1.2.0): New features, backward compatible
- **Patch** (v1.1.1): Bug fixes

## Release Checklist

- [ ] Update version di `Cargo.toml`
- [ ] Update CHANGELOG (jika ada)
- [ ] Test build locally: `cargo build --release`
- [ ] Commit changes
- [ ] Create & push tag
- [ ] Monitor Actions build
- [ ] Verify release artifacts
- [ ] Test download & installation
- [ ] Announce release (Telegram, etc.)
