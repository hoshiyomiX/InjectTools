# Changelog

All notable changes to InjectTools will be documented in this file.

## [1.2.3] - 2026-02-13

### Added
- **Fetch Progress Details**: Real-time progress during crt.sh fetch phase
  - Phase 1: Connecting to crt.sh (0-5%)
  - Phase 2: Fetching certificates with retry info (5-10%)
  - Phase 3: Parsing certificates (showing count)
  - Phase 4: DNS validation with valid count

### Changed
- Progress bar now shows detailed status for each fetch phase
- Test phase progress starts at 10% (after fetch complete)

### Technical
- Added `FetchProgress` data class in Crtsh.kt
- Added `onProgress` callback parameter to `fetchSubdomains()`
- Progress scaling: Fetch 0-10%, Test 10-100%

---

## [1.2.2] - 2026-02-13

### Fixed
- **Retry Without Re-fetch**: User no longer needs to re-fetch from crt.sh after network validation fails
  - Added "Pending Subdomains Card" with retry button when subdomains are fetched but not tested
  - Split dialog callbacks: `onCancel` (clear data) vs `onDismiss` (preserve data)
  - `pendingSubdomains` now preserved when network check fails during testing phase

### Added
- New `Pending Subdomains Card` UI component showing:
  - Count of fetched subdomains ready for testing
  - Reminder to switch to injection mode
  - "Retry Test" button to attempt testing again
  - "Cancel" button to discard fetched data

### Technical
- `TestConfirmationDialog` now has 3 callbacks: `onConfirm`, `onCancel`, `onDismiss`
- Clear pending subdomains only after successful test or explicit user cancel

---

## [1.2.1] - 2026-02-13

### Fixed
- **Batch Scan Flow**: Network validation now correctly happens AFTER fetching subdomains from crt.sh
  - Previous: Check injection mode first → blocked fetch from crt.sh
  - Now: Fetch from crt.sh (needs internet) → Confirm → Check injection mode → Test
- Added `hasInternetConnection()` helper in NetworkUtils for internet-dependent operations

### Added
- New `TestConfirmationDialog` component for better UX before testing phase
- Separate states for `isFetching` and `isScanning` in Batch Scan screen

### Technical
- Split network validation logic: fetch requires internet, testing requires injection mode
- Better user feedback with distinct "Fetching..." and "Testing..." states

---

## [1.2.0] - 2026-02-12

### Changed
- Removed all animations for simpler, faster UI
- Removed spring and tween animations
- Removed breathing animation from logos
- Removed staggered entrance animations
- Removed press feedback animations
- Simplified screen transitions

### Technical
- Removed animation imports (androidx.compose.animation.*)
- Reduced code complexity significantly
- Improved performance by removing animation overhead

---

## [1.1.1] - 2026-02-12

### Changed
- Refined staggered entrance animation for menu tiles
- Replaced bouncy spring animations with smooth EaseOutExpo curves
- Added subtle vertical slide (12dp) for depth perception on entrance
- Unified press feedback across all interactive components (buttons, cards, tiles)
- Reduced scale delta from 8% to 4% for more subtle, premium feel

### Technical
- Removed spring-based animations in favor of tween with cubic-bezier easing
- Added proper staggered timing (45ms per item) for cascade effect
- Consistent 120ms duration for all press feedback animations

---

## [1.1.0] - 2026-02-12

### Added
- Material You design system implementation
- Custom cubic-bezier easing curves for smooth animations
- Spring physics for natural press effects
- New app logo with inject bug concept
- Breathing animation for logo

### Changed
- Complete UI redesign with Material 3
- Smoother animations with custom interpolation
- Better visual feedback on interactions

### Fixed
- PNG format issue for app icon
- Animation stutter issues
- Various UI glitches

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
