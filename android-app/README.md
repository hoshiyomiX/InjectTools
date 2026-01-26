# InjectTools Android App

## Overview

Native Android application for InjectTools with Material 3 design.

## Requirements

- **Android SDK:** API 30+ (Android 11+)
- **JDK:** 17
- **Gradle:** 8.5
- **Native Libraries:** libinjecttools.so (ARM64 & ARMv7)

## Project Structure

```
android-app/
├── app/
│   ├── src/main/
│   │   ├── java/com/hoshiyomi/injecttools/
│   │   │   ├── MainActivity.kt
│   │   │   ├── core/
│   │   │   │   └── InjectToolsNative.kt    # JNI wrapper
│   │   │   └── ui/
│   │   │       ├── theme/
│   │   │       └── screens/
│   │   │           └── HomeScreen.kt
│   │   ├── jniLibs/
│   │   │   ├── arm64-v8a/
│   │   │   │   └── libinjecttools.so
│   │   │   └── armeabi-v7a/
│   │   │       └── libinjecttools.so
│   │   ├── res/
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## Building

### Manual Build

1. **Build Rust libraries first:**
   ```bash
   cd ..
   cargo build --release --lib --target aarch64-linux-android
   cargo build --release --lib --target armv7-linux-androideabi
   ```

2. **Copy .so files:**
   ```bash
   cp ../target/aarch64-linux-android/release/libinjecttools.so \
      app/src/main/jniLibs/arm64-v8a/
   
   cp ../target/armv7-linux-androideabi/release/libinjecttools.so \
      app/src/main/jniLibs/armeabi-v7a/
   ```

3. **Build APK:**
   ```bash
   ./gradlew assembleDebug      # Debug APK
   ./gradlew assembleRelease    # Release APK
   ```

### CI/CD Build

The GitHub Actions workflow automatically:
1. Builds Rust .so for both architectures
2. Copies them to jniLibs/
3. Builds and signs APK
4. Creates GitHub Release on tags

## Running

```bash
# Install debug APK
./gradlew installDebug

# Or manually
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Features

- ✅ Material 3 Design
- ✅ Jetpack Compose UI
- ✅ Type-safe JNI integration
- ✅ Dark/Light theme support
- ✅ Edge-to-edge display
- ✅ Kotlin Coroutines for async ops

## Architecture

```
UI Layer (Compose)
    ↓
ViewModel (Coroutines)
    ↓
InjectToolsNative (JNI Wrapper)
    ↓
libinjecttools.so (Rust)
    ↓
HTTP Client / Scanner / DNS
```

## Dependencies

- **Jetpack Compose:** Material 3, Navigation
- **Kotlin:** Coroutines, Serialization
- **AndroidX:** Core-KTX, Lifecycle, Activity

## Permissions

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

## Support

- **Min SDK:** 30 (Android 11)
- **Target SDK:** 34 (Android 14)
- **ABIs:** arm64-v8a, armeabi-v7a

## License

MIT License - See [../LICENSE](../LICENSE)
