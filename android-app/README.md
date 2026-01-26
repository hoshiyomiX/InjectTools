# InjectTools Android APK

## 🚀 Pure Kotlin Implementation

This Android app uses **100% Kotlin** implementation - **NO Rust compilation required!**

### Why Pure Kotlin?

- ✅ **Faster builds** - No native library compilation
- ✅ **Easier development** - Pure Kotlin stack trace
- ✅ **Smaller APK** - ~1MB saved vs native .so
- ✅ **Same logic** - 100% parity with Rust scanner.rs
- ✅ **Hot reload** - Instant code changes

---

## 📋 Prerequisites

### Option A: Android Studio (Recommended)
- Android Studio Hedgehog (2023.1.1) or newer
- Android SDK 30+ (Android 11+)
- JDK 17

### Option B: Command Line (Termux)
```bash
# Install dependencies
pkg install openjdk-17 gradle

# Set ANDROID_HOME (if not set)
export ANDROID_HOME=$HOME/android-sdk
```

---

## 🔨 Build Instructions

### Method 1: Android Studio GUI

1. **Open Project**
   ```
   File → Open → Select 'android-app' folder
   ```

2. **Sync Gradle**
   ```
   File → Sync Project with Gradle Files
   ```

3. **Build APK**
   ```
   Build → Build Bundle(s) / APK(s) → Build APK(s)
   ```

4. **Output Location**
   ```
   android-app/app/build/outputs/apk/debug/app-debug.apk
   ```

---

### Method 2: Command Line

```bash
# Navigate to android-app folder
cd android-app

# Build Debug APK
./gradlew assembleDebug

# Output:
# app/build/outputs/apk/debug/app-debug.apk

# Build Release APK (optimized)
./gradlew assembleRelease

# Output:
# app/build/outputs/apk/release/app-release-unsigned.apk
```

---

### Method 3: Direct Install to Device

```bash
# Build and install to connected device
./gradlew installDebug

# Launch app
adb shell am start -n com.hoshiyomi.injecttools/.MainActivity
```

---

## ⚡ Quick Start (Termux)

```bash
# Clone repo
git clone https://github.com/hoshiyomiX/InjectTools
cd InjectTools/android-app

# Build APK (one command)
./gradlew assembleDebug

# Copy to storage for easy install
cp app/build/outputs/apk/debug/app-debug.apk ~/storage/downloads/

# Install manually from Files app
```

---

## 📦 APK Sizes

| Build Type | Size | Optimizations |
|------------|------|---------------|
| Debug | ~3.5 MB | None |
| Release | ~2.5 MB | ProGuard, R8 |

---

## 🧪 Development Workflow

### Edit Code
```bash
# Make changes in:
android-app/app/src/main/java/com/hoshiyomi/injecttools/
├── core/InjectToolsKotlin.kt      # Scanner logic
├── ui/screens/                     # UI screens
├── viewmodel/                      # State management
└── MainActivity.kt                 # Entry point
```

### Test Changes
```bash
# Option 1: Hot reload (Android Studio)
# Just save file, compose will reload

# Option 2: Reinstall
./gradlew installDebug

# Option 3: Build new APK
./gradlew assembleDebug
```

---

## 🐛 Troubleshooting

### Build Fails: "SDK not found"
```bash
export ANDROID_HOME=$HOME/android-sdk
# Or install via: pkg install android-sdk
```

### Build Fails: "Java version"
```bash
# Check Java version
java -version
# Should be 17+

# Termux: Install JDK 17
pkg install openjdk-17
```

### Gradle Sync Issues
```bash
# Clean build
./gradlew clean

# Delete cache
rm -rf ~/.gradle/caches

# Re-sync
./gradlew build --refresh-dependencies
```

### APK Install Fails
```bash
# Enable unknown sources in Android settings
# Settings → Security → Unknown Sources

# Or use adb
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 📱 Tested Devices

- ✅ Android 11 (API 30) - Samsung Galaxy S21
- ✅ Android 12 (API 31) - Pixel 6
- ✅ Android 13 (API 33) - OnePlus 11
- ✅ Android 14 (API 34) - Pixel 8

All devices: **Non-rooted**, SELinux **Enforcing**

---

## 🔄 Migration to Rust (Optional)

If you want performance boost, you can later integrate Rust:

1. Build Rust library:
   ```bash
   cargo build --release --lib --target aarch64-linux-android
   ```

2. Copy .so to jniLibs:
   ```bash
   cp target/.../libinjecttools.so app/src/main/jniLibs/arm64-v8a/
   ```

3. Enable JNI in MainActivity:
   ```kotlin
   System.loadLibrary("injecttools")
   ```

See [ANDROID_MIGRATION.md](../ANDROID_MIGRATION.md) for details.

---

## 📝 Notes

- **No root required** - Works on stock Android
- **SELinux safe** - No binary execution
- **Offline capable** - After discovery, scanning is local
- **Battery friendly** - Efficient coroutine usage

---

## 🆘 Support

- **Issues:** https://github.com/hoshiyomiX/InjectTools/issues
- **Telegram:** [@hoshiyomi_id](https://t.me/hoshiyomi_id)
- **Docs:** [Main README](../README.md)

## 📄 License

MIT License - See [LICENSE](../LICENSE)
