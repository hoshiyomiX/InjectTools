# Build Logs

📝 **Automatic build logs** dari setiap Termux build via GitHub Actions.

## 📂 Location

Logs ada di **root repo** `build-logs/` (bukan di `.github/`) biar lebih gampang diakses.

```
InjectTools/
├── build-logs/           ⬅️ DI SINI
│   ├── success-aarch64-20260115-120530.log
│   ├── success-armv7a-20260115-120545.log
│   └── failed-aarch64-20260115-121030.log
├── src/
├── Cargo.toml
└── README.md
```

## 📝 Format Filename

```
{status}-{arch}-{timestamp}.log
```

**Examples:**
- `success-aarch64-20260115-120530.log` → ARM64 build success
- `failed-armv7a-20260115-120545.log` → ARMv7 build failed

## 📄 Log Contents

### Header
```
╔══════════════════════════════════════════════════════════════╗
║          InjectTools Termux Build Log                        ║
╚══════════════════════════════════════════════════════════════╝

Build Information:
══════════════════
Target: aarch64-linux-android
Architecture: aarch64
Android API: 30
Rust: rustc 1.75.0
Cargo: cargo 1.75.0
...
```

### Build Steps
1. 📦 **NDK Download** - Android NDK r26d
2. ⚙️ **Toolchain Setup** - Configure compilers/linkers
3. 🔨 **Cargo Build** - Compile Rust to Android binary
4. ✂️ **Strip** - Remove debug symbols
5. 📦 **Package** - Create `.tar.gz` + SHA256

### Footer
```
Build Summary
═════════════
Status:   SUCCESS
Finished: 2026-01-15 04:08:43 UTC
Duration: 193s

✅ SUCCESS - Artifacts ready
```

## 🔍 Reading Logs

### Via GitHub Web

1. Browse ke https://github.com/hoshiyomiX/InjectTools/tree/main/build-logs
2. Klik file `.log` yang mau dibaca
3. View atau download

### Via Git

```bash
git clone https://github.com/hoshiyomiX/InjectTools.git
cd InjectTools/build-logs

# List logs (newest first)
ls -lt

# Read latest success log
cat success-aarch64-*.log | tail -n 50

# Search for errors
grep -i error *.log
```

### Via cURL

```bash
# Download specific log
curl -O https://raw.githubusercontent.com/hoshiyomiX/InjectTools/main/build-logs/success-aarch64-20260115-120530.log

# View in terminal
curl -s https://raw.githubusercontent.com/hoshiyomiX/InjectTools/main/build-logs/success-aarch64-20260115-120530.log | less
```

### Via Termux

```bash
# Clone repo
pkg install git
git clone https://github.com/hoshiyomiX/InjectTools

# Read logs
cd InjectTools/build-logs
cat success-*.log
```

## 🔄 Auto Rotation

Workflow **otomatis keep last 10 logs** per architecture:

```bash
ls -t *-aarch64-*.log | tail -n +11 | xargs rm
```

Older logs tetap tersedia di:
- **Actions Artifacts** (30 days retention)
- **Git History** (permanent via commits)

## 🐛 Debugging

### Build Failed?

1. Cari log dengan prefix `failed-{arch}-`
2. Check error lines:
   ```bash
   grep -A 5 -B 5 "FAILED" failed-*.log
   ```

3. Common issues:
   - **Dependency errors**: Check `Compiling` lines
   - **Linker errors**: Check NDK toolchain setup
   - **Timeout**: Check duration in summary

### Build Success tapi Binary Error?

1. Check binary size:
   ```bash
   grep "Binary size" success-*.log
   ```

2. Verify ELF format:
   ```bash
   grep "ELF" success-*.log
   ```

3. Check strip results:
   ```bash
   grep -A 3 "Stripping" success-*.log
   ```

## 📊 Log Statistics

Logs help track:
- ✅ Build success rate per arch
- ⏱️ Compilation time trends
- 💾 Binary size before/after strip
- 🐛 Error patterns
- 🔧 NDK compatibility

## 🔗 Related Files

- [`.github/workflows/termux-release.yml`](../.github/workflows/termux-release.yml) - Build workflow
- [`CHANGELOG.md`](../CHANGELOG.md) - Version history
- [`Cargo.toml`](../Cargo.toml) - Dependencies

## ❓ FAQ

**Q: Kenapa logs di root bukan di `.github/`?**  
A: Biar lebih gampang diakses via web/mobile tanpa scroll ke subfolder.

**Q: Logs consume banyak space?**  
A: No, max 10 logs x 2 arch = 20 files (~2-5 KB each = ~100 KB total).

**Q: Bisa lihat logs dari HP?**  
A: Yes! Browse via GitHub mobile app atau web browser.

**Q: Logs hilang setelah berapa lama?**  
A: Di repo: permanent (sampai rotation). Di artifacts: 30 days.

**Q: Bisa disable logging?**  
A: Edit `.github/workflows/termux-release.yml` dan comment step "Push log to repo".

---

**Created by:** [@hoshiyomi_id](https://t.me/hoshiyomi_id)  
**Auto-generated** by GitHub Actions
