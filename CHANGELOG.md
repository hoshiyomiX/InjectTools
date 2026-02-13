# Changelog

All notable changes to InjectTools will be documented in this file.

## [1.1.1] - 2026-02-12

### Changed
- ✨ Refined staggered entrance animation for menu tiles
- ✨ Replaced bouncy spring animations with smooth EaseOutExpo curves
- ✨ Added subtle vertical slide (12dp) for depth perception on entrance
- ✨ Unified press feedback across all interactive components (buttons, cards, tiles)
- ✨ Reduced scale delta from 8% to 4% for more subtle, premium feel

### Technical
- Removed spring-based animations in favor of tween with cubic-bezier easing
- Added proper staggered timing (45ms per item) for cascade effect
- Consistent 120ms duration for all press feedback animations

---

## [1.1.0] - 2026-02-12

### Added
- ✨ Material You design system implementation
- ✨ Custom cubic-bezier easing curves for smooth animations
- ✨ Spring physics for natural press effects
- ✨ New app logo with inject bug concept
- ✨ Breathing animation for logo

### Changed
- 🎨 Complete UI redesign with Material 3
- 🚀 Smoother animations with custom interpolation
- 📱 Better visual feedback on interactions

### Fixed
- 🐛 PNG format issue for app icon
- 🐛 Animation stutter issues
- 🐛 Various UI glitches

### Technical
- Removed legacy Rust/Termux codebase
- Clean repository structure for Android-only
- Updated build configuration

---

## [1.0.0] - Initial Android Release

### Added
- Single subdomain testing
- Batch scan via crt.sh
- DNS resolution with Cloudflare detection
- Scan history
- Material Design basics
- Target host configuration
