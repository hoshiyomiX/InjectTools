# InjectTools MVVM Architecture Refactoring Plan

## Overview

This document provides a complete MVVM architecture refactoring plan for InjectTools Android app. The current codebase has all logic in `MainActivity.kt` (1000+ lines), making it difficult to test, maintain, and scale. This plan transforms it into a modern, testable, and maintainable architecture.

---

## Current vs Target Architecture

### Current Problems

```
┌─────────────────────────────────────────────────────────────┐
│                    CURRENT ARCHITECTURE                      │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│   MainActivity.kt (1000+ lines)                              │
│   ┌─────────────────────────────────────────────────────┐   │
│   │  UI  │  State Management  │  Business Logic  │  Nav  │   │
│   └─────────────────────────────────────────────────────┘   │
│                          │                                   │
│            ┌─────────────┼─────────────┐                    │
│            ▼             ▼             ▼                    │
│      Scanner.kt     Crtsh.kt    NetworkUtils.kt             │
│                                                              │
│   ❌ Problems:                                               │
│   - All logic in Composables (untestable)                   │
│   - No separation of concerns                                │
│   - State doesn't survive configuration changes             │
│   - Hard to test, maintain, and scale                        │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### Target Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    TARGET MVVM ARCHITECTURE                  │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌───────────────────────────────────────────────────────┐  │
│  │                    PRESENTATION LAYER                  │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌───────────────┐  │  │
│  │  │ Composables │  │ ViewModels  │  │   UI States   │  │  │
│  │  │ - Screens   │  │ - ScanVM    │  │ - ScanUiState │  │  │
│  │  │ - Components│  │ - HistoryVM │  │ - MenuUiState │  │  │
│  │  └──────┬──────┘  └──────┬──────┘  └───────────────┘  │  │
│  └─────────│────────────────│────────────────────────────┘  │
│            │       ┌────────┴────────┐                       │
│            │       │    Hilt DI      │                       │
│            │       └────────┬────────┘                       │
│  ┌─────────│────────────────│────────────────────────────┐  │
│  │         │   DOMAIN LAYER │                             │  │
│  │         │  ┌─────────────┴─────────────┐              │  │
│  │         │  │        Use Cases          │              │  │
│  │         │  │ - ScanSubdomainUseCase    │              │  │
│  │         │  │ - FetchSubdomainsUseCase  │              │  │
│  │         │  │ - BatchScanUseCase        │              │  │
│  │         │  └─────────────┬─────────────┘              │  │
│  └─────────│────────────────│────────────────────────────┘  │
│            │                │                                │
│  ┌─────────│────────────────│────────────────────────────┐  │
│  │         │    DATA LAYER  │                             │  │
│  │         │  ┌─────────────┴─────────────┐              │  │
│  │         │  │       Repository          │              │  │
│  │         │  │  ┌──────────┬───────────┐ │              │  │
│  │         │  │  │ ScanRepo │ HistoryRep│ │              │  │
│  │         │  │  └────┬─────┴─────┬─────┘ │              │  │
│  │         │  └───────│───────────│───────┘              │  │
│  │         │    ┌─────┴────┐ ┌────┴──────┐               │  │
│  │         │    │  Remote  │ │   Local   │               │  │
│  │         │    │ DataSource│ │ DataSource│               │  │
│  │         │    └──────────┘ └───────────┘               │  │
│  └─────────│─────────────────────────────────────────────┘  │
│            │                                                 │
│  ✅ Benefits:                                                │
│  - Testable (each layer independent)                        │
│  - Scalable (easy to add features)                          │
│  - Maintainable (clear separation)                          │
│  - Config-change safe (ViewModels survive rotation)         │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## Project Structure

```
app/src/main/java/com/hoshiyomix/injecttools/
├── InjectToolsApp.kt                    # Application class with Hilt
│
├── di/                                  # Dependency Injection
│   ├── AppModule.kt                     # App-level dependencies
│   ├── NetworkModule.kt                 # Retrofit, OkHttp
│   └── RepositoryModule.kt              # Repository bindings
│
├── data/                                # Data Layer
│   ├── remote/
│   │   ├── CrtshApi.kt                  # Retrofit interface
│   │   ├── CrtshRemoteDataSource.kt     # Remote data source
│   │   └── ScannerRemoteDataSource.kt   # Scanner operations
│   │
│   ├── local/
│   │   ├── HistoryLocalDataSource.kt    # Local storage
│   │   └── SettingsLocalDataSource.kt   # SharedPreferences
│   │
│   ├── repository/
│   │   ├── ScanRepository.kt            # Scan operations
│   │   ├── ScanRepositoryImpl.kt
│   │   ├── HistoryRepository.kt         # History operations
│   │   └── HistoryRepositoryImpl.kt
│   │
│   └── model/
│       ├── ScanResult.kt                # Domain model
│       ├── ScanProgress.kt              # Progress state
│       └── NetworkStatus.kt             # Network info
│
├── domain/                              # Domain Layer
│   ├── usecase/
│   │   ├── ScanSubdomainUseCase.kt
│   │   ├── FetchSubdomainsUseCase.kt
│   │   ├── BatchScanUseCase.kt
│   │   └── GetScanHistoryUseCase.kt
│   │
│   └── repository/                      # Repository interfaces
│       ├── IScanRepository.kt
│       └── IHistoryRepository.kt
│
├── presentation/                        # Presentation Layer
│   ├── MainActivity.kt                  # Minimal Activity
│   │
│   ├── viewmodel/
│   │   ├── ScanViewModel.kt             # Scan operations
│   │   └── HistoryViewModel.kt          # History management
│   │
│   ├── uistate/
│   │   ├── ScanUiState.kt               # Scan screen state
│   │   ├── HistoryUiState.kt            # History screen state
│   │   └── MenuUiState.kt               # Menu screen state
│   │
│   ├── navigation/
│   │   └── AppNavigation.kt             # Navigation graph
│   │
│   └── ui/                              # Composable screens
│       ├── screens/
│       │   ├── MenuScreen.kt
│       │   ├── ManualScanScreen.kt
│       │   ├── CrtshScanScreen.kt
│       │   └── HistoryScreen.kt
│       │
│       ├── components/                  # Reusable components
│       │   ├── ResultItem.kt
│       │   ├── ScanProgressCard.kt
│       │   ├── NetworkWarningDialog.kt
│       │   └── MenuTileCard.kt
│       │
│       ├── dialogs/
│       │   ├── FirstRunDialog.kt
│       │   ├── HostEditDialog.kt
│       │   └── TestConfirmDialog.kt
│       │
│       └── theme/
│           ├── Theme.kt
│           ├── Color.kt
│           └── Shape.kt
│
└── util/                                # Utilities
    ├── NetworkUtils.kt
    ├── CloudflareIpMatcher.kt
    └── Constants.kt
```

---

## Implementation Files

### 1. Application Class

**File:** `InjectToolsApp.kt`

```kotlin
package com.hoshiyomix.injecttools

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class InjectToolsApp : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
```

### 2. DI Modules

**File:** `di/AppModule.kt`

```kotlin
package com.hoshiyomix.injecttools.di

import android.content.Context
import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSharedPreferences(
        @ApplicationContext context: Context
    ): SharedPreferences {
        return context.getSharedPreferences("InjectToolsPrefs", Context.MODE_PRIVATE)
    }

    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context = context
}
```

**File:** `di/NetworkModule.kt`

```kotlin
package com.hoshiyomix.injecttools.di

import com.hoshiyomix.injecttools.data.remote.CrtshApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val CRTSH_BASE_URL = "https://crt.sh/"
    private const val TIMEOUT_SECONDS = 60L

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(CRTSH_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideCrtshApi(retrofit: Retrofit): CrtshApi {
        return retrofit.create(CrtshApi::class.java)
    }
}
```

**File:** `di/RepositoryModule.kt`

```kotlin
package com.hoshiyomix.injecttools.di

import com.hoshiyomix.injecttools.data.local.*
import com.hoshiyomix.injecttools.data.remote.*
import com.hoshiyomix.injecttools.data.repository.*
import com.hoshiyomix.injecttools.domain.repository.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    // Data Sources
    @Binds @Singleton
    abstract fun bindCrtshRemoteDataSource(impl: CrtshRemoteDataSourceImpl): CrtshRemoteDataSource

    @Binds @Singleton
    abstract fun bindScannerRemoteDataSource(impl: ScannerRemoteDataSourceImpl): ScannerRemoteDataSource

    @Binds @Singleton
    abstract fun bindHistoryLocalDataSource(impl: HistoryLocalDataSourceImpl): HistoryLocalDataSource

    @Binds @Singleton
    abstract fun bindSettingsLocalDataSource(impl: SettingsLocalDataSourceImpl): SettingsLocalDataSource

    // Repositories
    @Binds @Singleton
    abstract fun bindScanRepository(impl: ScanRepositoryImpl): IScanRepository

    @Binds @Singleton
    abstract fun bindHistoryRepository(impl: HistoryRepositoryImpl): IHistoryRepository
}
```

### 3. Data Models

**File:** `data/model/ScanResult.kt`

```kotlin
package com.hoshiyomix.injecttools.data.model

data class ScanResult(
    val subdomain: String,
    val ip: String,
    val isWorking: Boolean,
    val isCloudflare: Boolean,
    val errorMsg: String? = null,
    val targetHost: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    val statusMessage: String
        get() = when {
            isWorking -> "Working ✓"
            errorMsg != null -> errorMsg
            else -> "Unknown status"
        }
}
```

**File:** `data/model/ScanProgress.kt`

```kotlin
package com.hoshiyomix.injecttools.data.model

sealed class ScanProgress {
    data object Idle : ScanProgress()
    data object Connecting : ScanProgress()
    data class Fetching(val attempt: Int = 1, val maxAttempts: Int = 3) : ScanProgress()
    data class Parsing(val current: Int = 0, val total: Int = 0) : ScanProgress()
    data class ValidatingDns(val current: Int = 0, val total: Int = 0, val validCount: Int = 0) : ScanProgress()
    data class Scanning(val current: Int = 0, val total: Int = 0, val workingCount: Int = 0, val currentSubdomain: String = "") : ScanProgress()
    data class Complete(val totalScanned: Int, val workingCount: Int) : ScanProgress()
    data class Error(val message: String) : ScanProgress()

    fun toProgressFloat(): Float = when (this) {
        is Idle -> 0f
        is Connecting -> 0.05f
        is Fetching -> 0.05f + (0.05f * attempt / maxAttempts)
        is Parsing -> 0.1f + (0.1f * current / total.coerceAtLeast(1))
        is ValidatingDns -> 0.2f + (0.1f * current / total.coerceAtLeast(1))
        is Scanning -> 0.3f + (0.7f * current / total.coerceAtLeast(1))
        is Complete -> 1f
        is Error -> 0f
    }
}
```

**File:** `data/model/NetworkStatus.kt`

```kotlin
package com.hoshiyomix.injecttools.data.model

enum class NetworkStatus {
    NO_INTERNET_NO_VPN,    // Ideal for injection mode
    INTERNET_NO_VPN,       // Warning: may interfere
    NO_INTERNET_VPN,       // Warning: VPN may intercept DNS
    INTERNET_VPN,          // Error: must disable both
    DISCONNECTED;          // Error: no network

    fun canScan(): Boolean = this == NO_INTERNET_NO_VPN

    fun getWarningMessage(): String? = when (this) {
        INTERNET_NO_VPN -> "Regular internet detected! Disable WiFi/Data or use injection mode."
        NO_INTERNET_VPN -> "VPN is active! Please disable VPN before scanning."
        INTERNET_VPN -> "VPN + Internet detected! Disable both VPN and regular connection."
        DISCONNECTED -> "No network connection. Connect to WiFi/Data first."
        else -> null
    }

    fun hasInternet(): Boolean = this == INTERNET_NO_VPN || this == INTERNET_VPN
}
```

### 4. Repository Interfaces

**File:** `domain/repository/IScanRepository.kt`

```kotlin
package com.hoshiyomix.injecttools.domain.repository

import com.hoshiyomix.injecttools.data.model.ScanProgress
import com.hoshiyomix.injecttools.data.model.ScanResult

interface IScanRepository {
    suspend fun testSubdomain(targetHost: String, subdomain: String): Result<ScanResult>
    suspend fun fetchSubdomains(domain: String, onProgress: (ScanProgress) -> Unit = {}): Result<List<String>>
    suspend fun batchScan(
        targetHost: String,
        subdomains: List<String>,
        onProgress: (Int, Int, ScanResult) -> Unit = { _, _, _ -> }
    ): List<ScanResult>
}
```

**File:** `domain/repository/IHistoryRepository.kt`

```kotlin
package com.hoshiyomix.injecttools.domain.repository

import com.hoshiyomix.injecttools.data.model.ScanResult
import kotlinx.coroutines.flow.Flow

interface IHistoryRepository {
    fun getHistoryFlow(): Flow<List<ScanResult>>
    suspend fun getHistory(): List<ScanResult>
    suspend fun addToHistory(result: ScanResult)
    suspend fun addToHistory(results: List<ScanResult>)
    suspend fun clearHistory()
    suspend fun removeFromHistory(result: ScanResult)
}
```

### 5. Use Cases

**File:** `domain/usecase/ScanSubdomainUseCase.kt`

```kotlin
package com.hoshiyomix.injecttools.domain.usecase

import com.hoshiyomix.injecttools.data.model.ScanResult
import com.hoshiyomix.injecttools.data.repository.HistoryRepository
import com.hoshiyomix.injecttools.domain.repository.IScanRepository
import javax.inject.Inject

class ScanSubdomainUseCase @Inject constructor(
    private val scanRepository: IScanRepository,
    private val historyRepository: HistoryRepository
) {
    suspend operator fun invoke(
        targetHost: String,
        subdomain: String,
        saveToHistory: Boolean = true
    ): Result<ScanResult> {
        val result = scanRepository.testSubdomain(targetHost, subdomain)

        if (saveToHistory && result.isSuccess) {
            result.getOrNull()?.takeIf { it.isWorking }?.let {
                historyRepository.addToHistory(it)
            }
        }

        return result
    }
}
```

**File:** `domain/usecase/BatchScanUseCase.kt`

```kotlin
package com.hoshiyomix.injecttools.domain.usecase

import com.hoshiyomix.injecttools.data.model.ScanProgress
import com.hoshiyomix.injecttools.data.model.ScanResult
import com.hoshiyomix.injecttools.data.repository.HistoryRepository
import com.hoshiyomix.injecttools.domain.repository.IScanRepository
import javax.inject.Inject

class BatchScanUseCase @Inject constructor(
    private val scanRepository: IScanRepository,
    private val historyRepository: HistoryRepository,
    private val fetchSubdomainsUseCase: FetchSubdomainsUseCase
) {
    sealed class BatchScanResult {
        data class Success(
            val subdomains: List<String>,
            val scanResults: List<ScanResult>,
            val workingCount: Int
        ) : BatchScanResult()

        data class FetchFailed(val error: Throwable) : BatchScanResult()
    }

    suspend operator fun invoke(
        targetHost: String,
        domain: String,
        onFetchProgress: (ScanProgress) -> Unit = {},
        onScanProgress: (Int, Int, ScanResult) -> Unit = { _, _, _ -> }
    ): BatchScanResult {
        // Phase 1: Fetch
        val fetchResult = fetchSubdomainsUseCase(domain, onFetchProgress)

        if (fetchResult.isFailure) {
            return BatchScanResult.FetchFailed(fetchResult.exceptionOrNull()!!)
        }

        val subdomains = fetchResult.getOrNull() ?: emptyList()

        // Phase 2: Scan
        val scanResults = scanRepository.batchScan(targetHost, subdomains, onScanProgress)

        // Phase 3: Save to history
        scanResults.filter { it.isWorking }.let {
            if (it.isNotEmpty()) historyRepository.addToHistory(it)
        }

        return BatchScanResult.Success(
            subdomains = subdomains,
            scanResults = scanResults,
            workingCount = scanResults.count { it.isWorking }
        )
    }
}
```

### 6. UI States

**File:** `presentation/uistate/ScanUiState.kt`

```kotlin
package com.hoshiyomix.injecttools.presentation.uistate

import com.hoshiyomix.injecttools.data.model.ScanProgress
import com.hoshiyomix.injecttools.data.model.ScanResult

data class ScanUiState(
    val inputText: String = "",
    val targetHost: String = "",
    val isScanning: Boolean = false,
    val isFetching: Boolean = false,
    val progress: ScanProgress = ScanProgress.Idle,
    val progressPercent: Float = 0f,
    val recentResults: List<ScanResult> = emptyList(),
    val pendingSubdomains: List<String> = emptyList(),
    val errorMessage: String? = null
) {
    val isBusy: Boolean get() = isScanning || isFetching
    val workingCount: Int get() = recentResults.count { it.isWorking }
    val canSubmit: Boolean get() = inputText.isNotBlank() && targetHost.isNotBlank() && !isBusy
}

sealed class ScanIntent {
    data class UpdateInput(val text: String) : ScanIntent()
    data class ScanSubdomain(val subdomain: String) : ScanIntent()
    data class BatchScan(val domain: String) : ScanIntent()
    data object RetryPending : ScanIntent()
    data object ClearPending : ScanIntent()
    data object DismissError : ScanIntent()
}
```

### 7. ViewModels

**File:** `presentation/viewmodel/ScanViewModel.kt`

```kotlin
package com.hoshiyomix.injecttools.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoshiyomix.injecttools.data.model.ScanProgress
import com.hoshiyomix.injecttools.domain.usecase.BatchScanUseCase
import com.hoshiyomix.injecttools.domain.usecase.ScanSubdomainUseCase
import com.hoshiyomix.injecttools.presentation.uistate.ScanIntent
import com.hoshiyomix.injecttools.presentation.uistate.ScanUiState
import com.hoshiyomix.injecttools.util.NetworkUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val scanSubdomainUseCase: ScanSubdomainUseCase,
    private val batchScanUseCase: BatchScanUseCase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private val _navigation = MutableSharedFlow<ScanNavigation>()
    val navigation: SharedFlow<ScanNavigation> = _navigation.asSharedFlow()

    fun setTargetHost(host: String) {
        _uiState.update { it.copy(targetHost = host) }
    }

    fun processIntent(intent: ScanIntent) {
        when (intent) {
            is ScanIntent.UpdateInput -> _uiState.update { it.copy(inputText = intent.text) }
            is ScanIntent.ScanSubdomain -> scanSubdomain(intent.subdomain)
            is ScanIntent.BatchScan -> batchScan(intent.domain)
            is ScanIntent.RetryPending -> retryPending()
            is ScanIntent.ClearPending -> _uiState.update { it.copy(pendingSubdomains = emptyList()) }
            is ScanIntent.DismissError -> _uiState.update { it.copy(errorMessage = null) }
        }
    }

    private fun scanSubdomain(subdomain: String) {
        val state = _uiState.value
        if (state.isBusy) return

        val networkStatus = NetworkUtils.checkNetworkStatus(context)
        if (!networkStatus.canScan()) {
            viewModelScope.launch {
                _navigation.emit(ScanNavigation.ShowNetworkWarning(networkStatus.getWarningMessage()!!))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true) }

            scanSubdomainUseCase(state.targetHost, subdomain).fold(
                onSuccess = { result ->
                    _uiState.update {
                        it.copy(
                            isScanning = false,
                            recentResults = listOf(result) + it.recentResults.take(4)
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isScanning = false, errorMessage = error.message) }
                }
            )
        }
    }

    private fun batchScan(domain: String) {
        // Implementation similar to scanSubdomain but with progress tracking
    }

    private fun retryPending() {
        // Retry pending subdomains after network fix
    }
}

sealed class ScanNavigation {
    data class ShowNetworkWarning(val message: String) : ScanNavigation()
    data object NavigateToHistory : ScanNavigation()
}
```

### 8. Composable Screen Example

**File:** `presentation/ui/screens/ManualScanScreen.kt`

```kotlin
package com.hoshiyomix.injecttools.presentation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoshiyomix.injecttools.presentation.uistate.ScanIntent
import com.hoshiyomix.injecttools.presentation.viewmodel.ScanViewModel

@Composable
fun ManualScanScreen(
    viewModel: ScanViewModel = hiltViewModel(),
    onNavigateToHistory: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.navigation.collect { navigation ->
            when (navigation) {
                is ScanNavigation.NavigateToHistory -> onNavigateToHistory()
                is ScanNavigation.ShowNetworkWarning -> { /* Show dialog */ }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        // Input Card
        ScanInputCard(
            inputText = uiState.inputText,
            isScanning = uiState.isScanning,
            canSubmit = uiState.canSubmit,
            onInputChange = { viewModel.processIntent(ScanIntent.UpdateInput(it)) },
            onScan = { viewModel.processIntent(ScanIntent.ScanSubdomain(uiState.inputText)) }
        )

        // Results
        if (uiState.recentResults.isNotEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))
            // Show results...
        }
    }
}
```

---

## Build.gradle Updates

Add Hilt dependencies to `app/build.gradle`:

```groovy
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
    id 'org.jetbrains.kotlin.plugin.compose'
    id 'kotlin-kapt'  // Required for Hilt
    id 'com.google.dagger.hilt.android' version '2.52' apply true
}

android {
    // ... existing config ...
    namespace 'com.hoshiyomix.injecttools'
}

dependencies {
    // Hilt
    implementation 'com.google.dagger:hilt-android:2.52'
    kapt 'com.google.dagger:hilt-compiler:2.52'
    implementation 'androidx.hilt:hilt-navigation-compose:1.2.0'

    // ... existing dependencies ...
}

kapt {
    correctErrorTypes true
}
```

---

## Migration Steps

### Phase 1: Setup (1-2 hours)
1. Add Hilt plugin and dependencies to build.gradle
2. Create `InjectToolsApp.kt` with `@HiltAndroidApp`
3. Update `AndroidManifest.xml` to use the new Application class
4. Create folder structure

### Phase 2: Data Layer (2-3 hours)
1. Create data models (`ScanResult`, `ScanProgress`, `NetworkStatus`)
2. Create remote data sources (`CrtshRemoteDataSource`, `ScannerRemoteDataSource`)
3. Create local data sources (`HistoryLocalDataSource`, `SettingsLocalDataSource`)
4. Create repository implementations
5. Create DI modules

### Phase 3: Domain Layer (1-2 hours)
1. Create repository interfaces
2. Create use cases (`ScanSubdomainUseCase`, `BatchScanUseCase`, etc.)

### Phase 4: Presentation Layer (3-4 hours)
1. Create UI states
2. Create ViewModels (`ScanViewModel`, `HistoryViewModel`)
3. Refactor composables into separate files
4. Update MainActivity to use ViewModels

### Phase 5: Testing (2-3 hours)
1. Add unit tests for repositories
2. Add unit tests for use cases
3. Add unit tests for ViewModels
4. Add UI tests for composables

---

## Testing Strategy

### Unit Tests

```kotlin
// Example: ScanViewModelTest
@Test
fun `scanSubdomain emits success result`() = runTest {
    // Given
    val mockResult = ScanResult("test.example.com", "1.2.3.4", true, true)
    coEvery { scanRepository.testSubdomain(any(), any()) } returns Result.success(mockResult)

    // When
    viewModel.processIntent(ScanIntent.ScanSubdomain("test.example.com"))

    // Then
    val state = viewModel.uiState.first()
    assertFalse(state.isScanning)
    assertEquals(1, state.recentResults.size)
}
```

### Repository Tests

```kotlin
@Test
fun `fetchSubdomains returns valid subdomains`() = runTest {
    // Given
    coEvery { api.search(any()) } returns listOf(CrtShEntry("sub.example.com"))

    // When
    val result = repository.fetchSubdomains("example.com")

    // Then
    assertTrue(result.isSuccess)
    assertTrue(result.getOrNull()?.contains("sub.example.com") == true)
}
```

---

## Benefits Summary

| Aspect | Before | After |
|--------|--------|-------|
| **Testability** | ❌ No unit tests possible | ✅ Each layer testable |
| **Maintainability** | ❌ 1000+ lines in one file | ✅ Separated concerns |
| **Scalability** | ❌ Hard to add features | ✅ Easy to extend |
| **Config Changes** | ❌ State lost on rotation | ✅ ViewModels survive |
| **Dependency Management** | ❌ Manual instantiation | ✅ Hilt auto-injection |
| **State Management** | ❌ remember() | ✅ StateFlow + ViewModel |

---

## Conclusion

This MVVM architecture refactoring transforms InjectTools from a monolithic codebase into a modern, testable, and maintainable Android application. The key benefits include:

1. **Separation of Concerns**: Each layer has a single responsibility
2. **Testability**: ViewModels, repositories, and use cases can be unit tested
3. **Dependency Injection**: Hilt manages all dependencies automatically
4. **Reactive UI**: StateFlow provides real-time UI updates
5. **Configuration Safe**: ViewModels survive configuration changes

The migration can be done incrementally, starting with the data layer and working up to the presentation layer.
