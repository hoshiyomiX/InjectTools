# InjectTools Android App

This directory contains the Android APK implementation of InjectTools.

## Prerequisites

- Android Studio (latest stable)
- Android SDK with API 26+
- Android NDK r26 or later
- Rust toolchain with Android targets

## Setup

### 1. Install Android Targets for Rust

```bash
rustup target add aarch64-linux-android
rustup target add armv7-linux-androideabi
```

### 2. Configure Cargo for Android

Create `.cargo/config.toml` in project root:

```toml
[target.aarch64-linux-android]
linker = "aarch64-linux-android30-clang"
ar = "llvm-ar"

[target.armv7-linux-androideabi]
linker = "armv7a-linux-androideabi30-clang"
ar = "llvm-ar"
```

### 3. Set NDK Path

```bash
export ANDROID_NDK_HOME=~/Android/Sdk/ndk/26.1.10909125
# Or your NDK installation path
```

## Build Instructions

### Quick Build (Recommended)

```bash
# From project root
./android-app/build-rust.sh  # Build Rust libraries
./android-app/copy-libs.sh   # Copy to jniLibs
cd android-app
./gradlew assembleDebug      # Build APK
```

### Manual Build

#### Step 1: Build Rust Libraries

```bash
# ARM64 (modern devices)
cargo build --release --lib --target aarch64-linux-android

# ARMv7 (older devices)
cargo build --release --lib --target armv7-linux-androideabi
```

#### Step 2: Copy Libraries

```bash
mkdir -p android-app/app/src/main/jniLibs/arm64-v8a
mkdir -p android-app/app/src/main/jniLibs/armeabi-v7a

cp target/aarch64-linux-android/release/libinjecttools.so \
   android-app/app/src/main/jniLibs/arm64-v8a/

cp target/armv7-linux-androideabi/release/libinjecttools.so \
   android-app/app/src/main/jniLibs/armeabi-v7a/
```

#### Step 3: Build APK

```bash
cd android-app

# Debug build
./gradlew assembleDebug

# Release build (requires signing)
./gradlew assembleRelease
```

### Output

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release APK: `app/build/outputs/apk/release/app-release.apk`

## Install

```bash
# Install debug APK
adb install app/build/outputs/apk/debug/app-debug.apk

# Or using gradle
./gradlew installDebug
```

## Development

### Open in Android Studio

1. Open Android Studio
2. File → Open → Select `android-app/` directory
3. Wait for Gradle sync
4. Run app (Shift+F10)

### Live Reload

Android Studio supports hot reload for Compose UI changes.

### Debugging

- Kotlin code: Android Studio debugger
- Rust code: Use `android-ndk-gdb` or `lldb`
- JNI issues: Check logcat for `InjectToolsNative` tags

## Troubleshooting

### Library Not Found

```
java.lang.UnsatisfiedLinkError: dlopen failed: library "libinjecttools.so" not found
```

**Solution:** Run `copy-libs.sh` to ensure .so files are in jniLibs/

### ABI Mismatch

```
UnsatisfiedLinkError: ... wrong ELF class: ELFCLASS32
```

**Solution:** Ensure device architecture matches .so (ARM64 vs ARMv7)

### NDK Not Found

```
error: linker 'aarch64-linux-android30-clang' not found
```

**Solution:** Set `ANDROID_NDK_HOME` and add toolchains to PATH

## Architecture

```
Kotlin/Compose UI
    ↓ JNI
libinjecttools.so (Rust)
    ↓
Native HTTP Client (Hyper)
```

## Testing

```bash
# Run unit tests
./gradlew test

# Run instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest
```

## License

MIT - See [../LICENSE](../LICENSE)
