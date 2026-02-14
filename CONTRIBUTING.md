# Contributing to InjectTools

Thank you for your interest in contributing to InjectTools! This document provides guidelines and instructions for contributing.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Setup](#development-setup)
- [Coding Standards](#coding-standards)
- [Commit Guidelines](#commit-guidelines)
- [Pull Request Process](#pull-request-process)
- [Project Structure](#project-structure)

---

## Code of Conduct

- Be respectful and inclusive
- Welcome newcomers and help them get started
- Focus on what is best for the community
- Show empathy towards other community members

---

## Getting Started

### Prerequisites

- **JDK 17** or newer
- **Android Studio Hedgehog** (2023.1.1) or newer
- **Android SDK 35**
- **Git** configured with your identity

### Fork and Clone

```bash
# Fork the repository on GitHub, then:
git clone https://github.com/YOUR_USERNAME/InjectTools.git
cd InjectTools

# Add upstream remote
git remote add upstream https://github.com/hoshiyomiX/InjectTools.git
```

---

## Development Setup

### 1. Open in Android Studio

1. Open Android Studio
2. Select "Open an existing Android Studio project"
3. Navigate to the cloned `InjectTools` directory
4. Wait for Gradle sync to complete

### 2. Build the Project

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Run lint checks
./gradlew lint

# Run tests
./gradlew test
```

### 3. Project Configuration

Create a `local.properties` file (never commit this):

```properties
sdk.dir=/path/to/Android/sdk
```

---

## Coding Standards

### Kotlin Style Guide

We follow the [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html) with these specifics:

#### Naming Conventions

```kotlin
// Classes: PascalCase
class ScanResult { }

// Functions: camelCase
fun testSubdomain() { }

// Constants: SCREAMING_SNAKE_CASE
const val MAX_RETRIES = 3

// Variables: camelCase with descriptive names
var scanHistory: List<ScanResult>

// Avoid: Single-letter variables except in loops
// Bad: val r = scan()
// Good: val result = scan()
```

#### File Organization

```kotlin
// 1. File header (license if needed)
// 2. Package statement
package com.hoshiyomix.injecttools

// 3. Import statements (alphabetized)
import android.os.Bundle
import androidx.activity.ComponentActivity

// 4. Class/Object declaration
class MainActivity : ComponentActivity() {
    // Properties
    // Initializer blocks
    // Public functions
    // Internal functions
    // Private functions
    // Companion object
}
```

#### Code Formatting

- **Indentation:** 4 spaces (no tabs)
- **Line length:** Maximum 120 characters
- **Blank lines:** Between logical sections

### Compose Best Practices

```kotlin
// Prefer stateless composables
@Composable
fun ResultItem(
    result: ScanResult,      // Data passed in
    onTap: (ScanResult) -> Unit  // Events passed out
) {
    // Implementation
}

// Use remember for expensive computations
val processedResults = remember(scanResults) {
    scanResults.filter { it.isWorking }
}

// Use derivedStateOf for derived state
val workingCount by remember {
    derivedStateOf { results.count { it.isWorking } }
}
```

### Error Handling

```kotlin
// Use sealed classes for result states
sealed class ScanState {
    object Idle : ScanState()
    object Loading : ScanState()
    data class Success(val results: List<ScanResult>) : ScanState()
    data class Error(val message: String) : ScanState()
}

// Prefer specific exception handling
try {
    socket.connect(address, timeout)
} catch (e: SocketTimeoutException) {
    return ScanResult.Error("Connection timed out")
} catch (e: IOException) {
    return ScanResult.Error("Network error: ${e.message}")
}
```

---

## Commit Guidelines

We use [Conventional Commits](https://www.conventionalcommits.org/) specification.

### Format

```
<type>(<scope>): <description>

[optional body]

[optional footer(s)]
```

### Types

| Type | Description |
|------|-------------|
| `feat` | New feature |
| `fix` | Bug fix |
| `docs` | Documentation only |
| `style` | Code style (formatting, semicolons) |
| `refactor` | Code change without fix/feature |
| `perf` | Performance improvement |
| `test` | Adding/updating tests |
| `chore` | Build, CI, dependencies |
| `ci` | CI/CD configuration |

### Scopes

- `ui` - UI components and screens
- `scanner` - Scanning functionality
- `network` - Network utilities
- `storage` - Data persistence
- `ci` - CI/CD workflows

### Examples

```bash
# Feature
feat(scanner): add timeout configuration for DNS resolution

# Bug fix
fix(ui): resolve history not persisting after app restart

# Breaking change
feat(api)!: change ScanResult constructor signature

BREAKING CHANGE: `targetHost` parameter is now required in ScanResult

# Multiple paragraphs
fix(storage): resolve ProGuard R8 minification issues

- Add TypeToken preservation rules
- Update ProGuard configuration
- Add debug logging for JSON parsing

Closes #42
```

---

## Pull Request Process

### Before Submitting

1. **Sync with upstream**
   ```bash
   git fetch upstream
   git checkout dev
   git merge upstream/dev
   ```

2. **Run checks**
   ```bash
   ./gradlew lint
   ./gradlew test
   ./gradlew assembleRelease
   ```

3. **Update documentation** if needed

### PR Template

```markdown
## Description
Brief description of changes

## Type of Change
- [ ] Bug fix (non-breaking change)
- [ ] New feature (non-breaking change)
- [ ] Breaking change
- [ ] Documentation update

## Testing
Describe tests performed

## Screenshots (if applicable)
Add screenshots here

## Checklist
- [ ] Code follows style guidelines
- [ ] Self-review completed
- [ ] Comments added for complex logic
- [ ] Documentation updated
- [ ] No new warnings introduced
```

### Review Process

1. At least one approval required
2. All CI checks must pass
3. No merge conflicts
4. Squash and merge to `dev`

---

## Project Structure

```
InjectTools/
├── app/
│   ├── src/main/
│   │   ├── java/com/hoshiyomix/injecttools/
│   │   │   ├── MainActivity.kt      # UI & Navigation
│   │   │   ├── Scanner.kt           # Scan Engine
│   │   │   ├── NetworkUtils.kt      # Network utilities
│   │   │   ├── Crtsh.kt             # crt.sh API client
│   │   │   └── HistoryStorage.kt    # Data persistence
│   │   ├── res/                     # Android resources
│   │   └── AndroidManifest.xml
│   ├── build.gradle                 # App-level config
│   └── proguard-rules.pro           # ProGuard/R8 rules
├── gradle/                          # Gradle wrapper
├── .github/workflows/               # CI/CD pipelines
├── build.gradle.kts                 # Root config
├── settings.gradle.kts              # Project settings
├── README.md                        # Project overview
├── CHANGELOG.md                     # Version history
├── CONTRIBUTING.md                  # This file
└── LICENSE                          # MIT License
```

---

## Need Help?

- Open a [Discussion](https://github.com/hoshiyomiX/InjectTools/discussions)
- Join our Telegram: [@hoshiyomi_id](https://t.me/hoshiyomi_id)

---

Thank you for contributing! 🎉
