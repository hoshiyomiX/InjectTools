# InjectTools

[![Android Build](https://github.com/hoshiyomiX/InjectTools/actions/workflows/android.yml/badge.svg)](https://github.com/hoshiyomiX/InjectTools/actions/workflows/android.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

**Bug Inject Scanner for Cloudflare Subdomains** - Android Native App

High-performance Android app with Material You design for scanning Cloudflare subdomains.

## 🚀 Features

### Core Features
- ⚡ **Fast Scanning** - Concurrent subdomain testing
- 🔍 **DNS Resolution** - Multi-resolver with fallback
- ☁️ **Cloudflare Detection** - Auto-detect CF IP ranges
- 📊 **Real-time Progress** - Live progress tracking
- 💾 **Scan History** - View previous scan results
- 🌐 **crt.sh Integration** - Automatic subdomain discovery

### UI/UX
- 🎨 **Material You Design** - Modern Android design
- 🌙 **Dark Theme** - Eye-friendly dark mode
- ✨ **Smooth Animations** - Fluid transitions
- 📱 **Native Performance** - Built with Jetpack Compose

---

## 📥 Installation

### Download APK

Download the latest APK from [GitHub Releases](https://github.com/hoshiyomiX/InjectTools/releases)

### Requirements
- **Android 7.0+** (API 24+)
- **ARM64** device (most modern phones)
- Internet connection

### Install
1. Download APK
2. Enable "Install from unknown sources" in settings
3. Open APK and install
4. Launch app and set your target host

---

## 📱 Usage

### First Run
1. Open app
2. Enter your target host (e.g., `tunnel.example.com`)
3. Click "Get Started"

### Test Single Subdomain
1. Tap "Test Subdomain"
2. Enter subdomain to test
3. View results instantly

### Batch Scan via crt.sh
1. Tap "Batch Scan"
2. Enter domain (e.g., `cloudflare.com`)
3. App fetches subdomains from crt.sh
4. Tests all subdomains automatically
5. View working bugs in History

### View History
- Tap "History" to see previous scan results
- Shows working bugs with IP addresses
- Indicates Cloudflare status

---

## 🎯 What It Does

InjectTools scans subdomains to find "bug inject" targets that work with your tunnel/proxy host:

1. **Resolve DNS** - Get IP address of subdomain
2. **Test Connection** - Check if subdomain is reachable
3. **Detect Cloudflare** - Identify CF-protected domains
4. **Report Results** - Show working bugs with details

---

## 🔧 Technical Details

### Built With
- **Kotlin** - Primary language
- **Jetpack Compose** - Modern UI toolkit
- **Material You** - Design system
- **Retrofit** - HTTP client
- **Coroutines** - Async operations

### Architecture
- MVVM pattern
- Single Activity app
- Compose Navigation

### Permissions
- `INTERNET` - For network operations
- `ACCESS_NETWORK_STATE` - Network status

---

## 📊 Project Structure

```
InjectTools/
├── app/
│   ├── src/main/
│   │   ├── java/com/hoshiyomix/injecttools/
│   │   │   ├── MainActivity.kt    # UI & Navigation
│   │   │   ├── Scanner.kt         # Scan Engine
│   │   │   ├── NetworkUtils.kt    # Network utilities
│   │   │   └── Crtsh.kt           # crt.sh API
│   │   ├── res/                   # Resources
│   │   └── AndroidManifest.xml
│   └── build.gradle               # Build config
├── gradle/                        # Gradle wrapper
├── build.gradle.kts               # Root config
└── settings.gradle.kts            # Settings
```

---

## 🔄 Build from Source

### Requirements
- Android Studio Hedgehog or newer
- JDK 17
- Android SDK 34

### Steps
```bash
# Clone repository
git clone https://github.com/hoshiyomiX/InjectTools.git
cd InjectTools

# Open in Android Studio
# OR build via command line:
./gradlew assembleRelease

# APK location:
# app/build/outputs/apk/release/
```

---

## 📝 Changelog

### v1.1.0 (Current)
- ✨ Material You redesign
- ✨ Smooth animations with custom easing
- ✨ New app logo
- 🐛 Bug fixes and performance improvements

### v1.0.0
- Initial Android release
- Single subdomain test
- crt.sh batch scan
- Scan history

---

## 🤝 Contributing

Contributions welcome! Submit a Pull Request.

---

## 📄 License

MIT License - see [LICENSE](LICENSE)

---

## 👤 Credits

**Created by:** [@hoshiyomi_id](https://t.me/hoshiyomi_id)

**Powered by:**
- [Kotlin](https://kotlinlang.org/)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Material Design](https://m3.material.io/)
- [crt.sh](https://crt.sh) - Certificate transparency logs

---

## ⚠️ Disclaimer

For **educational purposes** and **authorized testing only**.

---

⭐ **Star** this repo if it helps you!

🐛 Report bugs: [Issues](https://github.com/hoshiyomiX/InjectTools/issues)
