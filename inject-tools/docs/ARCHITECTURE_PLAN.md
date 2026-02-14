# InjectTools MVVM Architecture Plan

## Executive Summary

Dokumen ini menjelaskan rencana refactoring arsitektur InjectTools dari **monolithic Compose** ke **proper MVVM** dengan Clean Architecture principles.

---

## Table of Contents

1. [Current Architecture Analysis](#current-architecture-analysis)
2. [Target Architecture Design](#target-architecture-design)
3. [Module Structure](#module-structure)
4. [Implementation Plan](#implementation-plan)
5. [Code Examples](#code-examples)
6. [Migration Strategy](#migration-strategy)
7. [Testing Strategy](#testing-strategy)

---

## Current Architecture Analysis

### Existing Structure

```
app/src/main/java/com/hoshiyomix/injecttools/
├── MainActivity.kt          ❌ 1000+ lines, UI + State + Navigation
├── Scanner.kt               ✅ Good, pure business logic
├── NetworkUtils.kt          ✅ Good, utility class
├── Crtsh.kt                 ✅ Good, API client
└── HistoryStorage.kt        ✅ Good, persistence layer
```

### Problems Identified

| Issue | Severity | Description |
|-------|----------|-------------|
| God Activity | 🔴 High | MainActivity.kt contains UI, state, navigation, and business logic |
| No ViewModel | 🔴 High | State management in Composables, survives config changes poorly |
| No Repository | 🟡 Medium | Direct data access from UI layer |
| No DI | 🟡 Medium | Manual dependency creation, hard to test |
| Tight Coupling | 🟡 Medium | UI directly depends on Scanner, Crtsh, etc. |
| No Error Handling Layer | 🟡 Medium | Errors handled ad-hoc in UI |

### Current Data Flow (Problematic)

```
┌─────────────────────────────────────────────────────────────┐
│                      MainActivity.kt                         │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐    │
│  │   UI     │  │  State   │  │Navigation│  │ Business │    │
│  │ Compose  │  │ remember │  │  Logic   │  │  Logic   │    │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘    │
│       │             │             │             │           │
│       └─────────────┴─────────────┴─────────────┘           │
│                          │                                   │
│              ┌───────────┴───────────┐                      │
│              ▼                       ▼                      │
│        ┌──────────┐           ┌──────────┐                  │
│        │ Scanner  │           │  Crtsh   │                  │
│        └──────────┘           └──────────┘                  │
└─────────────────────────────────────────────────────────────┘

Problems:
- All concerns mixed in one file
- State lost on configuration changes
- Hard to test individual components
- No clear separation of responsibilities
```

---

## Target Architecture Design

### Clean Architecture + MVVM

```
┌─────────────────────────────────────────────────────────────────────┐
│                           PRESENTATION LAYER                         │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                    Compose UI (Screens)                      │   │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐       │   │
│  │  │MenuScreen│ │ScanScreen│ │CrtshScreen│ │HistoryScreen│   │   │
│  │  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘       │   │
│  └───────┼────────────┼────────────┼────────────┼──────────────┘   │
│          │            │            │            │                   │
│          └────────────┴────────────┴────────────┘                   │
│                              │                                       │
│  ┌───────────────────────────┴───────────────────────────────────┐ │
│  │                    ViewModels (MVVM)                           │ │
│  │  ┌────────────┐ ┌─────────────┐ ┌──────────────┐             │ │
│  │  │MainViewModel│ │ScanViewModel│ │CrtshViewModel│             │ │
│  │  └────────────┘ └─────────────┘ └──────────────┘             │ │
│  └───────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                            DOMAIN LAYER                              │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                      Use Cases                               │   │
│  │  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐        │   │
│  │  │ScanSubdomain │ │FetchSubdomain│ │SaveHistory   │        │   │
│  │  │  UseCase     │ │  UseCase     │ │  UseCase     │        │   │
│  │  └──────────────┘ └──────────────┘ └──────────────┘        │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                   Domain Models                              │   │
│  │  ┌──────────┐ ┌───────────┐ ┌────────────┐ ┌──────────┐    │   │
│  │  │ScanResult│ │Subdomain  │ │NetworkState│ │ScanConfig│    │   │
│  │  └──────────┘ └───────────┘ └────────────┘ └──────────┘    │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │               Repository Interfaces                          │   │
│  │  ┌───────────────┐ ┌─────────────────┐ ┌────────────────┐  │   │
│  │  │ScannerRepo   │ │SubdomainRepo    │ │HistoryRepo     │  │   │
│  │  └───────────────┘ └─────────────────┘ └────────────────┘  │   │
│  └─────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                             DATA LAYER                               │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                   Repository Implementations                 │   │
│  │  ┌───────────────────┐ ┌─────────────────────────────────┐ │   │
│  │  │ScannerRepositoryImpl│ │SubdomainRepositoryImpl        │ │   │
│  │  └───────────────────┘ └─────────────────────────────────┘ │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                      Data Sources                            │   │
│  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────────────┐   │   │
│  │  │RemoteDataSource│ │LocalDataSource│ │NetworkDataSource │   │   │
│  │  │  (CrtshApi)    │ │(HistoryStorage)│ │(NetworkUtils)   │   │   │
│  │  └─────────────┘ └─────────────┘ └─────────────────────┘   │   │
│  └─────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Module Structure

### Proposed Package Structure

```
app/src/main/java/com/hoshiyomix/injecttools/
│
├── InjectToolsApplication.kt          # Application class with DI setup
│
├── di/                                 # Dependency Injection
│   ├── AppModule.kt                   # App-level dependencies
│   ├── NetworkModule.kt               # Retrofit, OkHttp
│   ├── RepositoryModule.kt            # Repository bindings
│   └── ViewModelModule.kt             # ViewModel factories
│
├── domain/                             # Domain Layer (Pure Kotlin)
│   ├── model/                         # Domain Models
│   │   ├── ScanResult.kt
│   │   ├── Subdomain.kt
│   │   ├── NetworkState.kt
│   │   └── ScanConfig.kt
│   │
│   ├── repository/                    # Repository Interfaces
│   │   ├── ScannerRepository.kt
│   │   ├── SubdomainRepository.kt
│   │   └── HistoryRepository.kt
│   │
│   └── usecase/                       # Use Cases
│       ├── ScanSubdomainUseCase.kt
│       ├── FetchSubdomainsUseCase.kt
│       ├── SaveHistoryUseCase.kt
│       ├── LoadHistoryUseCase.kt
│       ├── GetTargetHostUseCase.kt
│       └── SetTargetHostUseCase.kt
│
├── data/                               # Data Layer
│   ├── repository/                    # Repository Implementations
│   │   ├── ScannerRepositoryImpl.kt
│   │   ├── SubdomainRepositoryImpl.kt
│   │   └── HistoryRepositoryImpl.kt
│   │
│   ├── remote/                        # Remote Data Sources
│   │   ├── CrtshApi.kt
│   │   ├── CrtshRemoteDataSource.kt
│   │   └── dto/                       # Data Transfer Objects
│   │       └── CrtShEntryDto.kt
│   │
│   ├── local/                         # Local Data Sources
│   │   ├── HistoryLocalDataSource.kt
│   │   └── PreferencesDataSource.kt
│   │
│   └── mapper/                        # DTO <-> Domain Mappers
│       └── ScanResultMapper.kt
│
├── presentation/                       # Presentation Layer
│   ├── MainActivity.kt                # Single Activity (minimal)
│   │
│   ├── navigation/                    # Navigation
│   │   ├── NavGraph.kt
│   │   └── Screen.kt
│   │
│   ├── theme/                         # Material 3 Theme
│   │   ├── Color.kt
│   │   ├── Theme.kt
│   │   └── Shape.kt
│   │
│   ├── common/                        # Shared UI Components
│   │   ├── components/
│   │   │   ├── AppButton.kt
│   │   │   ├── AppCard.kt
│   │   │   ├── LoadingIndicator.kt
│   │   │   └── ErrorDialog.kt
│   │   └── modifiers/
│   │       └── Conditional.kt
│   │
│   └── screen/                        # Screen Composables
│       ├── menu/
│       │   ├── MenuScreen.kt
│       │   ├── MenuViewModel.kt
│       │   └── MenuState.kt
│       │
│       ├── scan/
│       │   ├── ScanScreen.kt
│       │   ├── ScanViewModel.kt
│       │   └── ScanState.kt
│       │
│       ├── crtsh/
│       │   ├── CrtshScreen.kt
│       │   ├── CrtshViewModel.kt
│       │   └── CrtshState.kt
│       │
│       ├── history/
│       │   ├── HistoryScreen.kt
│       │   ├── HistoryViewModel.kt
│       │   └── HistoryState.kt
│       │
│       └── dialog/
│           ├── FirstRunDialog.kt
│           └── HostEditDialog.kt
│
└── util/                               # Utilities
    ├── NetworkUtils.kt
    ├── CloudflareUtils.kt
    └── DateFormatter.kt
```

---

## Implementation Plan

### Phase 1: Domain Layer (Week 1)

**Priority: High | Effort: Medium | Risk: Low**

#### Tasks:
1. Create domain models (immutable data classes)
2. Define repository interfaces
3. Create use cases with proper error handling
4. Write unit tests for use cases

#### Deliverables:
- [ ] `domain/model/ScanResult.kt`
- [ ] `domain/model/Subdomain.kt`
- [ ] `domain/model/NetworkState.kt`
- [ ] `domain/repository/ScannerRepository.kt`
- [ ] `domain/repository/SubdomainRepository.kt`
- [ ] `domain/repository/HistoryRepository.kt`
- [ ] `domain/usecase/ScanSubdomainUseCase.kt`
- [ ] `domain/usecase/FetchSubdomainsUseCase.kt`

---

### Phase 2: Data Layer (Week 1-2)

**Priority: High | Effort: Medium | Risk: Medium**

#### Tasks:
1. Implement repository interfaces
2. Create data sources (remote/local)
3. Add DTO mappers
4. Write repository tests

#### Deliverables:
- [ ] `data/repository/ScannerRepositoryImpl.kt`
- [ ] `data/repository/SubdomainRepositoryImpl.kt`
- [ ] `data/repository/HistoryRepositoryImpl.kt`
- [ ] `data/remote/CrtshRemoteDataSource.kt`
- [ ] `data/local/HistoryLocalDataSource.kt`
- [ ] `data/local/PreferencesDataSource.kt`

---

### Phase 3: DI Setup (Week 2)

**Priority: High | Effort: Low | Risk: Low**

#### Tasks:
1. Add Hilt dependencies
2. Create DI modules
3. Set up Application class
4. Configure build variants

#### Deliverables:
- [ ] Update `app/build.gradle` with Hilt
- [ ] `di/AppModule.kt`
- [ ] `di/NetworkModule.kt`
- [ ] `di/RepositoryModule.kt`
- [ ] `di/ViewModelModule.kt`
- [ ] `InjectToolsApplication.kt`

---

### Phase 4: Presentation Layer (Week 2-3)

**Priority: High | Effort: High | Risk: Medium**

#### Tasks:
1. Create ViewModels with StateFlow
2. Extract UI composables
3. Implement unidirectional data flow
4. Add proper error handling in UI

#### Deliverables:
- [ ] `presentation/screen/menu/MenuViewModel.kt`
- [ ] `presentation/screen/scan/ScanViewModel.kt`
- [ ] `presentation/screen/crtsh/CrtshViewModel.kt`
- [ ] `presentation/screen/history/HistoryViewModel.kt`
- [ ] Extract all screens from MainActivity
- [ ] Create shared components

---

### Phase 5: Testing & Cleanup (Week 3-4)

**Priority: Medium | Effort: Medium | Risk: Low**

#### Tasks:
1. Write ViewModel tests
2. Add UI tests
3. Performance testing
4. Code cleanup

#### Deliverables:
- [ ] ViewModel unit tests
- [ ] Repository integration tests
- [ ] UI instrumented tests
- [ ] Remove old code

---

## Code Examples

### 1. Domain Model

```kotlin
// domain/model/ScanResult.kt
package com.hoshiyomix.injecttools.domain.model

/**
 * Represents the result of a subdomain scan.
 * Immutable domain model.
 */
data class ScanResult(
    val subdomain: String,
    val ip: String,
    val isWorking: Boolean,
    val isCloudflare: Boolean,
    val targetHost: String,
    val error: ScanError? = null
) {
    val status: ScanStatus
        get() = when {
            error != null -> ScanStatus.ERROR
            isWorking -> ScanStatus.WORKING
            else -> ScanStatus.NOT_WORKING
        }
}

enum class ScanStatus {
    WORKING,
    NOT_WORKING,
    ERROR
}

/**
 * Sealed class for scan errors - type-safe error handling
 */
sealed class ScanError {
    data class DnsFailed(val reason: String) : ScanError()
    data class ConnectionFailed(val reason: String) : ScanError()
    data class SslFailed(val reason: String) : ScanError()
    data class NotCloudflare(val ip: String) : ScanError()
    data class VpnDetected(val type: VpnType) : ScanError()
    data class HostOffline(val httpCode: String) : ScanError()
    data class Unknown(val message: String) : ScanError()
}

enum class VpnType {
    FAKE_DNS,
    VPN_INTERCEPTION,
    ACTIVE_VPN
}
```

### 2. Repository Interface

```kotlin
// domain/repository/ScannerRepository.kt
package com.hoshiyomix.injecttools.domain.repository

import com.hoshiyomix.injecttools.domain.model.ScanResult
import com.hoshiyomix.injecttools.domain.model.NetworkState
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for scanner operations.
 * Domain layer - no Android dependencies.
 */
interface ScannerRepository {

    /**
     * Scan a single subdomain against a target host.
     * @return Result containing scan result or error
     */
    suspend fun scanSubdomain(
        targetHost: String,
        subdomain: String
    ): Result<ScanResult>

    /**
     * Check current network state.
     */
    suspend fun getNetworkState(): NetworkState

    /**
     * Check if device has internet connection.
     */
    suspend fun hasInternetConnection(): Boolean
}
```

### 3. Use Case

```kotlin
// domain/usecase/ScanSubdomainUseCase.kt
package com.hoshiyomix.injecttools.domain.usecase

import com.hoshiyomix.injecttools.domain.model.ScanResult
import com.hoshiyomix.injecttools.domain.repository.ScannerRepository
import com.hoshiyomix.injecttools.domain.repository.HistoryRepository
import javax.inject.Inject

/**
 * Use case for scanning a single subdomain.
 * Encapsulates business logic and coordinates repositories.
 */
class ScanSubdomainUseCase @Inject constructor(
    private val scannerRepository: ScannerRepository,
    private val historyRepository: HistoryRepository
) {
    /**
     * Execute the scan and save result if working.
     *
     * @param targetHost The target injection host
     * @param subdomain The subdomain to scan
     * @return Result containing the scan result or error
     */
    suspend operator fun invoke(
        targetHost: String,
        subdomain: String
    ): Result<ScanResult> {
        // Validate inputs
        if (targetHost.isBlank()) {
            return Result.failure(IllegalArgumentException("Target host cannot be empty"))
        }

        if (subdomain.isBlank()) {
            return Result.failure(IllegalArgumentException("Subdomain cannot be empty"))
        }

        // Perform scan
        val result = scannerRepository.scanSubdomain(targetHost, subdomain)

        // Save to history if working
        result.getOrNull()?.let { scanResult ->
            if (scanResult.isWorking) {
                historyRepository.saveResult(scanResult)
            }
        }

        return result
    }
}
```

### 4. Repository Implementation

```kotlin
// data/repository/ScannerRepositoryImpl.kt
package com.hoshiyomix.injecttools.data.repository

import android.content.Context
import com.hoshiyomix.injecttools.domain.model.*
import com.hoshiyomix.injecttools.domain.repository.ScannerRepository
import com.hoshiyomix.injecttools.data.local.NetworkDataSource
import com.hoshiyomix.injecttools.data.mapper.ScanResultMapper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScannerRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val networkDataSource: NetworkDataSource,
    private val mapper: ScanResultMapper
) : ScannerRepository {

    override suspend fun scanSubdomain(
        targetHost: String,
        subdomain: String
    ): Result<ScanResult> = withContext(Dispatchers.IO) {
        try {
            val rawResult = networkDataSource.performScan(targetHost, subdomain)
            Result.success(mapper.toDomain(rawResult))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getNetworkState(): NetworkState {
        return networkDataSource.getNetworkState()
    }

    override suspend fun hasInternetConnection(): Boolean {
        return networkDataSource.hasInternetConnection()
    }
}
```

### 5. ViewModel

```kotlin
// presentation/screen/scan/ScanViewModel.kt
package com.hoshiyomix.injecttools.presentation.screen.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoshiyomix.injecttools.domain.model.ScanError
import com.hoshiyomix.injecttools.domain.model.ScanResult
import com.hoshiyomix.injecttools.domain.usecase.ScanSubdomainUseCase
import com.hoshiyomix.injecttools.domain.usecase.GetTargetHostUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the single subdomain scan screen.
 * Follows unidirectional data flow (UDF) pattern.
 */
@HiltViewModel
class ScanViewModel @Inject constructor(
    private val scanSubdomainUseCase: ScanSubdomainUseCase,
    private val getTargetHostUseCase: GetTargetHostUseCase
) : ViewModel() {

    // Private mutable state
    private val _state = MutableStateFlow(ScanState())

    // Public immutable state exposed to UI
    val state: StateFlow<ScanState> = _state.asStateFlow()

    // Side effects for one-time events (navigation, toasts, etc.)
    private val _effect = MutableSharedFlow<ScanEffect>()
    val effect: SharedFlow<ScanEffect> = _effect.asSharedFlow()

    init {
        loadTargetHost()
    }

    private fun loadTargetHost() {
        viewModelScope.launch {
            val host = getTargetHostUseCase()
            _state.update { it.copy(targetHost = host) }
        }
    }

    /**
     * Handle UI events from the screen.
     */
    fun onEvent(event: ScanEvent) {
        when (event) {
            is ScanEvent.SubdomainChanged -> {
                _state.update { it.copy(subdomain = event.value) }
            }

            is ScanEvent.ClearSubdomain -> {
                _state.update { it.copy(subdomain = "") }
            }

            is ScanEvent.ScanClicked -> {
                performScan()
            }

            is ScanEvent.ResultDismissed -> {
                _state.update { it.copy(showResult = false) }
            }
        }
    }

    private fun performScan() {
        val currentState = _state.value

        if (currentState.subdomain.isBlank()) {
            viewModelScope.launch {
                _effect.emit(ScanEffect.ShowToast("Please enter a subdomain"))
            }
            return
        }

        viewModelScope.launch {
            // Update state to loading
            _state.update {
                it.copy(isScanning = true, error = null)
            }

            // Execute use case
            val result = scanSubdomainUseCase(
                targetHost = currentState.targetHost,
                subdomain = currentState.subdomain
            )

            // Update state based on result
            result.fold(
                onSuccess = { scanResult ->
                    _state.update {
                        it.copy(
                            isScanning = false,
                            lastResult = scanResult,
                            showResult = true,
                            recentResults = listOf(scanResult) + it.recentResults.take(4)
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isScanning = false,
                            error = error.message ?: "Unknown error"
                        )
                    }
                }
            )
        }
    }
}

/**
 * Immutable UI state.
 */
data class ScanState(
    val targetHost: String = "",
    val subdomain: String = "",
    val isScanning: Boolean = false,
    val lastResult: ScanResult? = null,
    val showResult: Boolean = false,
    val recentResults: List<ScanResult> = emptyList(),
    val error: String? = null
)

/**
 * UI events from the screen.
 */
sealed class ScanEvent {
    data class SubdomainChanged(val value: String) : ScanEvent()
    object ClearSubdomain : ScanEvent()
    object ScanClicked : ScanEvent()
    object ResultDismissed : ScanEvent()
}

/**
 * One-time side effects.
 */
sealed class ScanEffect {
    data class ShowToast(val message: String) : ScanEffect()
    data class ShowErrorDialog(val error: ScanError) : ScanEffect()
}
```

### 6. Compose Screen

```kotlin
// presentation/screen/scan/ScanScreen.kt
package com.hoshiyomix.injecttools.presentation.screen.scan

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest

@Composable
fun ScanRoute(
    viewModel: ScanViewModel = hiltViewModel(),
    onShowNetworkWarning: (String) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Handle side effects
    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is ScanEffect.ShowToast -> {
                    // Show toast via callback or local state
                }
                is ScanEffect.ShowErrorDialog -> {
                    onShowNetworkWarning(effect.error.toString())
                }
            }
        }
    }

    ScanScreen(
        state = state,
        onEvent = viewModel::onEvent
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanScreen(
    state: ScanState,
    onEvent: (ScanEvent) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        // Input Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = ShapeLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "Enter Subdomain",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = state.subdomain,
                    onValueChange = { onEvent(ScanEvent.SubdomainChanged(it)) },
                    placeholder = { Text("subdomain.example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (state.subdomain.isNotEmpty()) {
                            IconButton(onClick = { onEvent(ScanEvent.ClearSubdomain) }) {
                                Icon(Icons.Default.Clear, "Clear")
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { onEvent(ScanEvent.ScanClicked) },
                    enabled = !state.isScanning && state.subdomain.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    if (state.isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scanning...")
                    } else {
                        Icon(Icons.Default.Search, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Test Subdomain")
                    }
                }
            }
        }

        // Results Section
        if (state.recentResults.isNotEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "Result",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(12.dp))

            state.lastResult?.let { result ->
                ResultItem(
                    result = result,
                    onTap = { /* Navigate or copy */ }
                )
            }
        }

        // Error display
        state.error?.let { error ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
```

### 7. DI Module

```kotlin
// di/RepositoryModule.kt
package com.hoshiyomix.injecttools.di

import com.hoshiyomix.injecttools.domain.repository.*
import com.hoshiyomix.injecttools.data.repository.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindScannerRepository(
        impl: ScannerRepositoryImpl
    ): ScannerRepository

    @Binds
    @Singleton
    abstract fun bindSubdomainRepository(
        impl: SubdomainRepositoryImpl
    ): SubdomainRepository

    @Binds
    @Singleton
    abstract fun bindHistoryRepository(
        impl: HistoryRepositoryImpl
    ): HistoryRepository
}
```

### 8. Navigation

```kotlin
// presentation/navigation/NavGraph.kt
package com.hoshiyomix.injecttools.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hoshiyomix.injecttools.presentation.screen.menu.MenuRoute
import com.hoshiyomix.injecttools.presentation.screen.scan.ScanRoute
import com.hoshiyomix.injecttools.presentation.screen.crtsh.CrtshRoute
import com.hoshiyomix.injecttools.presentation.screen.history.HistoryRoute

@Composable
fun InjectToolsNavGraph(
    navController: NavHostController = rememberNavController(),
    onShowNetworkWarning: (String) -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Menu.route
    ) {
        composable(route = Screen.Menu.route) {
            MenuRoute(
                onNavigateToScan = {
                    navController.navigate(Screen.Scan.route)
                },
                onNavigateToCrtsh = {
                    navController.navigate(Screen.Crtsh.route)
                },
                onNavigateToHistory = {
                    navController.navigate(Screen.History.route)
                }
            )
        }

        composable(route = Screen.Scan.route) {
            ScanRoute(
                onShowNetworkWarning = onShowNetworkWarning
            )
        }

        composable(route = Screen.Crtsh.route) {
            CrtshRoute(
                onNavigateToHistory = {
                    navController.navigate(Screen.History.route) {
                        popUpTo(Screen.Menu.route)
                    }
                },
                onShowNetworkWarning = onShowNetworkWarning
            )
        }

        composable(route = Screen.History.route) {
            HistoryRoute()
        }
    }
}

// presentation/navigation/Screen.kt
sealed class Screen(val route: String) {
    object Menu : Screen("menu")
    object Scan : Screen("scan")
    object Crtsh : Screen("crtsh")
    object History : Screen("history")
}
```

---

## Migration Strategy

### Step-by-Step Migration

```
Current State                    Target State
─────────────                    ────────────
MainActivity.kt (1000+ lines)    → Multiple files (50-100 lines each)
           │
           ├── UI Code           → screen/*.kt (Compose)
           ├── State Management  → ViewModel + StateFlow
           ├── Business Logic    → Use Cases
           ├── Data Access       → Repositories
           └── Navigation        → NavGraph.kt
```

### Migration Order (Minimize Breaking Changes)

1. **Extract Domain Models First**
   - Create domain model classes
   - Update existing code to use them
   - No behavior change, just data class migration

2. **Create Repository Interfaces**
   - Define interfaces in domain layer
   - Create implementations that delegate to existing code
   - No UI changes yet

3. **Add DI (Hilt)**
   - Set up Hilt modules
   - Wire up existing classes
   - Test dependency injection works

4. **Create ViewModels**
   - Extract state from Composables
   - Move to StateFlow
   - Update Composables to observe state

5. **Extract Screens**
   - One screen at a time
   - Start with simplest (History)
   - End with most complex (Crtsh)

6. **Remove Old Code**
   - Delete original MainActivity code
   - Clean up unused imports
   - Update tests

---

## Testing Strategy

### Unit Tests

```kotlin
// domain/usecase/ScanSubdomainUseCaseTest.kt
class ScanSubdomainUseCaseTest {

    @Test
    fun `invoke with empty target host returns failure`() = runTest {
        // Given
        val useCase = ScanSubdomainUseCase(mockScannerRepo, mockHistoryRepo)

        // When
        val result = useCase("", "subdomain.example.com")

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `invoke saves working result to history`() = runTest {
        // Given
        val workingResult = ScanResult(
            subdomain = "test.example.com",
            ip = "104.16.0.1",
            isWorking = true,
            isCloudflare = true,
            targetHost = "target.com"
        )
        whenever(mockScannerRepo.scanSubdomain(any(), any()))
            .thenReturn(Result.success(workingResult))

        // When
        val result = useCase("target.com", "test.example.com")

        // Then
        verify(mockHistoryRepo).saveResult(workingResult)
    }
}
```

### ViewModel Tests

```kotlin
// presentation/screen/scan/ScanViewModelTest.kt
class ScanViewModelTest {

    @get:Rule
    val dispatcherRule = StandardTestDispatcher()

    private lateinit var viewModel: ScanViewModel

    @Before
    fun setup() {
        viewModel = ScanViewModel(
            scanSubdomainUseCase = mockScanUseCase,
            getTargetHostUseCase = mockGetTargetHostUseCase
        )
    }

    @Test
    fun `initial state is correct`() = runTest {
        // When
        val state = viewModel.state.value

        // Then
        assertEquals("", state.subdomain)
        assertFalse(state.isScanning)
        assertNull(state.lastResult)
    }

    @Test
    fun `onEvent SubdomainChanged updates state`() = runTest {
        // When
        viewModel.onEvent(ScanEvent.SubdomainChanged("test.example.com"))

        // Then
        assertEquals("test.example.com", viewModel.state.value.subdomain)
    }
}
```

---

## Dependencies to Add

```groovy
// app/build.gradle

plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
    id 'org.jetbrains.kotlin.plugin.compose'
    id 'com.google.dagger.hilt.android'  // Add this
    id 'kotlin-kapt'                       // Add this for Hilt
}

dependencies {
    // ... existing dependencies ...

    // Hilt Dependency Injection
    implementation 'com.google.dagger:hilt-android:2.52'
    kapt 'com.google.dagger:hilt-compiler:2.52'

    // Hilt Navigation Compose
    implementation 'androidx.hilt:hilt-navigation-compose:1.2.0'

    // Lifecycle & ViewModel
    implementation 'androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7'
    implementation 'androidx.lifecycle:lifecycle-runtime-compose:2.8.7'

    // Testing
    testImplementation 'org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0'
    testImplementation 'app.cash.turbine:turbine:1.2.0'
    testImplementation 'io.mockk:mockk:1.13.13'
    testImplementation 'com.google.truth:truth:1.4.4'
}
```

---

## Timeline Summary

| Phase | Duration | Key Deliverables |
|-------|----------|-----------------|
| Phase 1 | Week 1 | Domain models, interfaces, use cases |
| Phase 2 | Week 1-2 | Repository implementations, data sources |
| Phase 3 | Week 2 | DI setup with Hilt |
| Phase 4 | Week 2-3 | ViewModels, UI extraction |
| Phase 5 | Week 3-4 | Testing, cleanup, documentation |

---

## Success Metrics

| Metric | Current | Target |
|--------|---------|--------|
| MainActivity.kt lines | ~1000 | <100 |
| ViewModel count | 0 | 4 |
| Test coverage | ~0% | >70% |
| Coupling score | High | Low |
| Configuration survival | No | Yes |

---

**Prepared by:** GitHub Pro Agent
**Date:** 2026
**Version:** 1.0
