# InjectTools Android App - MVVM Architecture Plan

## Executive Summary

This document outlines a comprehensive migration plan from the current monolithic architecture to a modern MVVM (Model-View-ViewModel) architecture using Clean Architecture principles, Hilt dependency injection, and Kotlin 2.0 best practices.

---

## 1. Current State Analysis

### 1.1 Existing Structure
```
app/src/main/java/com/hoshiyomix/injecttools/
├── MainActivity.kt          (1000+ lines - UI + Business Logic mixed)
├── Scanner.kt               (Scanning functionality)
├── NetworkUtils.kt          (Network operations)
├── Crtsh.kt                 (CRT.sh API integration)
└── HistoryStorage.kt        (Local data storage)
```

### 1.2 Current Issues
| Issue | Impact |
|-------|--------|
| Monolithic MainActivity | Hard to test, maintain, and scale |
| No separation of concerns | Business logic coupled with UI |
| No dependency injection | Tight coupling, hard to mock |
| No Repository pattern | Data sources not abstracted |
| No error handling strategy | Inconsistent error handling |
| No unit test support | Code is untestable |

---

## 2. Proposed Package Structure

### 2.1 Complete Package Diagram
```
com.hoshiyomix.injecttools/
│
├── InjectToolsApplication.kt          # Hilt Application class
│
├── di/                                 # Dependency Injection
│   ├── AppModule.kt                    # App-level dependencies
│   ├── NetworkModule.kt                # Retrofit, OkHttp, Gson
│   ├── DatabaseModule.kt               # Room database
│   └── RepositoryModule.kt             # Repository bindings
│
├── data/                               # Data Layer
│   ├── remote/                         # Remote Data Sources
│   │   ├── api/
│   │   │   ├── CrtshApiService.kt      # CRT.sh API interface
│   │   │   └── ScannerApiService.kt    # Scanner API interface
│   │   ├── dto/                        # Data Transfer Objects
│   │   │   ├── CrtshResponseDto.kt
│   │   │   └── ScanResultDto.kt
│   │   └── datasource/
│   │       ├── RemoteDataSource.kt     # Interface
│   │       └── RemoteDataSourceImpl.kt
│   │
│   ├── local/                          # Local Data Sources
│   │   ├── database/
│   │   │   ├── AppDatabase.kt          # Room Database
│   │   │   ├── dao/
│   │   │   │   └── HistoryDao.kt
│   │   │   └── entities/
│   │   │       └── HistoryEntity.kt
│   │   └── datasource/
│   │       ├── LocalDataSource.kt      # Interface
│   │       └── LocalDataSourceImpl.kt
│   │
│   ├── mapper/                         # DTO ↔ Domain mappers
│   │   ├── CrtshMapper.kt
│   │   └── ScanResultMapper.kt
│   │
│   └── repository/
│       ├── CrtshRepositoryImpl.kt
│       └── ScannerRepositoryImpl.kt
│
├── domain/                             # Domain Layer (Pure Kotlin)
│   ├── model/                          # Domain Models
│   │   ├── CrtshResult.kt
│   │   ├── ScanResult.kt
│   │   └── HistoryEntry.kt
│   │
│   ├── repository/                     # Repository Interfaces
│   │   ├── CrtshRepository.kt
│   │   └── ScannerRepository.kt
│   │
│   └── usecase/                        # Use Cases (Single Responsibility)
│       ├── ScanDomainUseCase.kt
│       ├── GetCrtshDataUseCase.kt
│       ├── GetHistoryUseCase.kt
│       └── SaveHistoryUseCase.kt
│
├── presentation/                       # Presentation Layer
│   ├── main/                           # Main Screen
│   │   ├── MainScreen.kt               # Compose UI
│   │   ├── MainViewModel.kt            # ViewModel
│   │   ├── MainContract.kt             # UI State & Events
│   │   └── components/                 # UI Components
│   │       ├── ScanResultCard.kt
│   │       ├── HistoryList.kt
│   │       └── LoadingIndicator.kt
│   │
│   ├── history/                        # History Screen
│   │   ├── HistoryScreen.kt
│   │   ├── HistoryViewModel.kt
│   │   └── HistoryContract.kt
│   │
│   ├── scanner/                        # Scanner Screen
│   │   ├── ScannerScreen.kt
│   │   ├── ScannerViewModel.kt
│   │   └── ScannerContract.kt
│   │
│   ├── components/                     # Shared UI Components
│   │   ├── ErrorDialog.kt
│   │   ├── LoadingOverlay.kt
│   │   └── CustomTextField.kt
│   │
│   └── theme/                          # Material3 Theme
│       ├── Theme.kt
│       ├── Color.kt
│       └── Type.kt
│
├── common/                             # Common Utilities
│   ├── util/
│   │   ├── NetworkUtil.kt
│   │   ├── DateUtil.kt
│   │   └── Constants.kt
│   │
│   └── result/                         # Result wrapper
│       └── AppResult.kt                # Sealed class for results
│
└── MainActivity.kt                     # Single Activity (entry point)
```

---

## 3. Layer Responsibilities

### 3.1 Presentation Layer (UI)
| Component | Responsibility |
|-----------|---------------|
| **Activity** | Navigation host, minimal code |
| **Compose Screens** | Declarative UI, stateless when possible |
| **ViewModel** | Hold UI state, handle events, expose StateFlow |
| **Contract** | Define UI State, Events, and Effects |

**Key Principles:**
- Screens observe StateFlow from ViewModel
- Events flow from UI to ViewModel via callbacks
- No business logic in Compose functions
- Single source of truth in ViewModel

### 3.2 Domain Layer (Business Logic)
| Component | Responsibility |
|-----------|---------------|
| **Use Cases** | Single business operation |
| **Repository Interfaces** | Define data contracts |
| **Domain Models** | Pure Kotlin data classes |

**Key Principles:**
- Zero Android dependencies
- Pure Kotlin for maximum testability
- Each UseCase has single responsibility
- Repository interfaces defined here, implemented in Data layer

### 3.3 Data Layer (Data Access)
| Component | Responsibility |
|-----------|---------------|
| **Repository Implementations** | Coordinate data sources |
| **Remote DataSource** | Network operations |
| **Local DataSource** | Database/SharedPreferences |
| **Mappers** | Convert between DTO and Domain models |

**Key Principles:**
- Repository decides data source (cache vs network)
- Mappers keep DTO separate from Domain
- Data sources are interchangeable
- Error handling at repository level

---

## 4. Key Classes & Interfaces

### 4.1 Result Wrapper (Sealed Class)

```kotlin
// common/result/AppResult.kt
package com.hoshiyomix.injecttools.common.result

sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    data class Error(
        val exception: Throwable,
        val message: String? = exception.message,
        val errorCode: ErrorCode = ErrorCode.UNKNOWN
    ) : AppResult<Nothing>()
    
    data object Loading : AppResult<Nothing>()
    
    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
    val isLoading: Boolean get() = this is Loading
    
    fun getOrNull(): T? = when (this) {
        is Success -> data
        else -> null
    }
    
    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error -> throw exception
        is Loading -> throw IllegalStateException("Result is still loading")
    }
    
    inline fun <R> map(transform: (T) -> R): AppResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
        is Loading -> this
    }
    
    inline fun onSuccess(action: (T) -> Unit): AppResult<T> {
        if (this is Success) action(data)
        return this
    }
    
    inline fun onError(action: (Error) -> Unit): AppResult<T> {
        if (this is Error) action(this)
        return this
    }
}

enum class ErrorCode {
    NETWORK_ERROR,
    SERVER_ERROR,
    AUTH_ERROR,
    NOT_FOUND,
    VALIDATION_ERROR,
    UNKNOWN
}

// Extension functions for try-catch wrapping
inline fun <T> safeCall(block: () -> T): AppResult<T> {
    return try {
        Success(block())
    } catch (e: Exception) {
        Error(e, e.message, e.toErrorCode())
    }
}

suspend inline fun <T> safeApiCall(crossinline block: suspend () -> T): AppResult<T> {
    return try {
        Success(block())
    } catch (e: Exception) {
        Error(e, e.message, e.toErrorCode())
    }
}

fun Exception.toErrorCode(): ErrorCode = when (this) {
    is java.net.UnknownHostException -> ErrorCode.NETWORK_ERROR
    is java.net.SocketTimeoutException -> ErrorCode.NETWORK_ERROR
    is retrofit2.HttpException -> when (code()) {
        401 -> ErrorCode.AUTH_ERROR
        404 -> ErrorCode.NOT_FOUND
        in 400..499 -> ErrorCode.VALIDATION_ERROR
        in 500..599 -> ErrorCode.SERVER_ERROR
        else -> ErrorCode.UNKNOWN
    }
    else -> ErrorCode.UNKNOWN
}
```

### 4.2 Domain Models

```kotlin
// domain/model/ScanResult.kt
package com.hoshiyomix.injecttools.domain.model

data class ScanResult(
    val id: String,
    val domain: String,
    val ipAddress: String?,
    val ports: List<PortInfo>,
    val technologies: List<Technology>,
    val headers: Map<String, String>,
    val sslInfo: SslInfo?,
    val scannedAt: Long,
    val status: ScanStatus
)

data class PortInfo(
    val port: Int,
    val service: String,
    val state: PortState,
    val banner: String?
)

enum class PortState { OPEN, CLOSED, FILTERED }

data class Technology(
    val name: String,
    val version: String?,
    val category: String
)

data class SslInfo(
    val issuer: String,
    val validFrom: Long,
    val validTo: Long,
    val protocol: String
)

enum class ScanStatus { PENDING, IN_PROGRESS, COMPLETED, FAILED }
```

```kotlin
// domain/model/CrtshResult.kt
package com.hoshiyomix.injecttools.domain.model

data class CrtshResult(
    val id: Long,
    val domain: String,
    val subdomains: List<SubdomainEntry>,
    val queriedAt: Long
)

data class SubdomainEntry(
    val name: String,
    val firstSeen: Long?,
    val lastSeen: Long?
)
```

```kotlin
// domain/model/HistoryEntry.kt
package com.hoshiyomix.injecttools.domain.model

data class HistoryEntry(
    val id: Long = 0,
    val query: String,
    val type: HistoryType,
    val result: String,
    val timestamp: Long,
    val isFavorite: Boolean = false
)

enum class HistoryType {
    DOMAIN_SCAN,
    CRTSH_QUERY,
    NETWORK_SCAN
}
```

### 4.3 Repository Interfaces

```kotlin
// domain/repository/ScannerRepository.kt
package com.hoshiyomix.injecttools.domain.repository

import com.hoshiyomix.injecttools.common.result.AppResult
import com.hoshiyomix.injecttools.domain.model.ScanResult
import kotlinx.coroutines.flow.Flow

interface ScannerRepository {
    suspend fun scanDomain(domain: String): AppResult<ScanResult>
    fun getScanHistory(): Flow<List<ScanResult>>
    suspend fun getScanById(id: String): AppResult<ScanResult>
    suspend fun cancelScan(id: String): AppResult<Unit>
}
```

```kotlin
// domain/repository/CrtshRepository.kt
package com.hoshiyomix.injecttools.domain.repository

import com.hoshiyomix.injecttools.common.result.AppResult
import com.hoshiyomix.injecttools.domain.model.CrtshResult
import kotlinx.coroutines.flow.Flow

interface CrtshRepository {
    suspend fun querySubdomains(domain: String): AppResult<CrtshResult>
    fun getCachedResults(domain: String): Flow<CrtshResult?>
    suspend fun refreshSubdomains(domain: String): AppResult<CrtshResult>
}
```

### 4.4 Use Cases

```kotlin
// domain/usecase/ScanDomainUseCase.kt
package com.hoshiyomix.injecttools.domain.usecase

import com.hoshiyomix.injecttools.common.result.AppResult
import com.hoshiyomix.injecttools.domain.model.ScanResult
import com.hoshiyomix.injecttools.domain.repository.ScannerRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.*
import javax.inject.Inject

class ScanDomainUseCase @Inject constructor(
    private val scannerRepository: ScannerRepository,
    private val ioDispatcher: CoroutineDispatcher
) {
    operator fun invoke(domain: String): Flow<AppResult<ScanResult>> = flow {
        emit(AppResult.Loading)
        
        // Validate input
        if (!isValidDomain(domain)) {
            emit(AppResult.Error(
                IllegalArgumentException("Invalid domain format"),
                errorCode = ErrorCode.VALIDATION_ERROR
            ))
            return@flow
        }
        
        // Perform scan
        val result = scannerRepository.scanDomain(domain.trim().lowercase())
        emit(result)
    }
        .flowOn(ioDispatcher)
        .catch { e ->
            emit(AppResult.Error(e, e.message))
        }
    
    private fun isValidDomain(domain: String): Boolean {
        val domainRegex = Regex(
            "^(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}$"
        )
        return domain.matches(domainRegex)
    }
}
```

```kotlin
// domain/usecase/GetCrtshDataUseCase.kt
package com.hoshiyomix.injecttools.domain.usecase

import com.hoshiyomix.injecttools.common.result.AppResult
import com.hoshiyomix.injecttools.domain.model.CrtshResult
import com.hoshiyomix.injecttools.domain.repository.CrtshRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.*
import javax.inject.Inject

class GetCrtshDataUseCase @Inject constructor(
    private val crtshRepository: CrtshRepository,
    private val ioDispatcher: CoroutineDispatcher
) {
    operator fun invoke(domain: String, forceRefresh: Boolean = false): Flow<AppResult<CrtshResult>> = flow {
        emit(AppResult.Loading)
        
        val result = if (forceRefresh) {
            crtshRepository.refreshSubdomains(domain)
        } else {
            crtshRepository.querySubdomains(domain)
        }
        
        emit(result)
    }
        .flowOn(ioDispatcher)
        .catch { e ->
            emit(AppResult.Error(e, e.message))
        }
}
```

```kotlin
// domain/usecase/GetHistoryUseCase.kt
package com.hoshiyomix.injecttools.domain.usecase

import com.hoshiyomix.injecttools.domain.model.HistoryEntry
import com.hoshiyomix.injecttools.domain.repository.HistoryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.*
import javax.inject.Inject

class GetHistoryUseCase @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val ioDispatcher: CoroutineDispatcher
) {
    operator fun invoke(
        filter: HistoryFilter = HistoryFilter.ALL,
        limit: Int = 100
    ): Flow<List<HistoryEntry>> = historyRepository
        .getHistory(filter, limit)
        .flowOn(ioDispatcher)
        .catch { emit(emptyList()) }
}

enum class HistoryFilter { ALL, FAVORITES, DOMAIN_SCAN, CRTSH_QUERY }
```

### 4.5 ViewModel with Contract (MVI Pattern)

```kotlin
// presentation/main/MainContract.kt
package com.hoshiyomix.injecttools.presentation.main

import com.hoshiyomix.injecttools.domain.model.HistoryEntry
import com.hoshiyomix.injecttools.domain.model.ScanResult
import com.hoshiyomix.injecttools.domain.model.CrtshResult

data class MainState(
    val isLoading: Boolean = false,
    val scanResult: ScanResult? = null,
    val crtshResult: CrtshResult? = null,
    val history: List<HistoryEntry> = emptyList(),
    val searchQuery: String = "",
    val selectedTab: MainTab = MainTab.SCAN,
    val error: MainError? = null,
    val showHistory: Boolean = false
)

sealed interface MainEvent {
    data class SearchDomain(val domain: String) : MainEvent
    data object ScanDomain : MainEvent
    data class QueryCrtsh(val domain: String) : MainEvent
    data object ClearResults : MainEvent
    data class SelectTab(val tab: MainTab) : MainEvent
    data class ToggleHistoryFavorite(val entryId: Long) : MainEvent
    data class DeleteHistoryEntry(val entryId: Long) : MainEvent
    data object ToggleHistoryPanel : MainEvent
    data class DismissError(val errorId: String) : MainEvent
}

sealed interface MainEffect {
    data class ShowSnackbar(val message: String) : MainEffect
    data class NavigateToResult(val resultId: String) : MainEffect
    data class ShareResult(val content: String) : MainEffect
    data object Vibrate : MainEffect
}

sealed class MainError {
    abstract val id: String
    abstract val message: String
    
    data class NetworkError(override val id: String = "network") : MainError() {
        override val message: String = "Network error. Please check your connection."
    }
    data class ValidationError(val field: String, override val id: String = "validation") : MainError() {
        override val message: String = "Invalid $field"
    }
    data class ApiError(val code: Int, override val id: String = "api") : MainError() {
        override val message: String = "Server error (code: $code)"
    }
    data class UnknownError(val throwable: Throwable, override val id: String = "unknown") : MainError() {
        override val message: String = throwable.message ?: "An unknown error occurred"
    }
}

enum class MainTab { SCAN, CRTSH, NETWORK }
```

```kotlin
// presentation/main/MainViewModel.kt
package com.hoshiyomix.injecttools.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoshiyomix.injecttools.common.result.AppResult
import com.hoshiyomix.injecttools.common.result.ErrorCode
import com.hoshiyomix.injecttools.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class MainViewModel @Inject constructor(
    private val scanDomainUseCase: ScanDomainUseCase,
    private val getCrtshDataUseCase: GetCrtshDataUseCase,
    private val getHistoryUseCase: GetHistoryUseCase,
    private val saveHistoryUseCase: SaveHistoryUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val deleteHistoryUseCase: DeleteHistoryUseCase,
    @Named("IO") private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {
    
    // UI State - Single source of truth
    private val _state = MutableStateFlow(MainState())
    val state: StateFlow<MainState> = _state.asStateFlow()
    
    // One-time events (navigation, toasts, etc.)
    private val _effects = MutableSharedFlow<MainEffect>()
    val effects: SharedFlow<MainEffect> = _effects.asSharedFlow()
    
    // Track active jobs for cancellation
    private var activeScanJob: Job? = null
    
    init {
        loadHistory()
    }
    
    fun onEvent(event: MainEvent) {
        when (event) {
            is MainEvent.SearchDomain -> handleSearchDomain(event.domain)
            is MainEvent.ScanDomain -> handleScanDomain()
            is MainEvent.QueryCrtsh -> handleQueryCrtsh(event.domain)
            is MainEvent.ClearResults -> handleClearResults()
            is MainEvent.SelectTab -> handleSelectTab(event.tab)
            is MainEvent.ToggleHistoryFavorite -> handleToggleFavorite(event.entryId)
            is MainEvent.DeleteHistoryEntry -> handleDeleteHistory(event.entryId)
            is MainEvent.ToggleHistoryPanel -> handleToggleHistory()
            is MainEvent.DismissError -> handleDismissError(event.errorId)
        }
    }
    
    private fun handleSearchDomain(domain: String) {
        _state.update { it.copy(searchQuery = domain) }
    }
    
    private fun handleScanDomain() {
        val domain = _state.value.searchQuery.trim()
        if (domain.isEmpty()) {
            showError(MainError.ValidationError("domain"))
            return
        }
        
        activeScanJob?.cancel()
        activeScanJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            
            scanDomainUseCase(domain)
                .catch { e ->
                    showError(MainError.UnknownError(e))
                }
                .collect { result ->
                    when (result) {
                        is AppResult.Loading -> {
                            _state.update { it.copy(isLoading = true) }
                        }
                        is AppResult.Success -> {
                            _state.update { 
                                it.copy(
                                    isLoading = false,
                                    scanResult = result.data,
                                    selectedTab = MainTab.SCAN
                                )
                            }
                            saveToHistory(result.data)
                            _effects.emit(MainEffect.Vibrate)
                        }
                        is AppResult.Error -> {
                            _state.update { it.copy(isLoading = false) }
                            handleError(result)
                        }
                    }
                }
        }
    }
    
    private fun handleQueryCrtsh(domain: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            
            getCrtshDataUseCase(domain)
                .catch { e ->
                    showError(MainError.UnknownError(e))
                }
                .collect { result ->
                    when (result) {
                        is AppResult.Loading -> continue
                        is AppResult.Success -> {
                            _state.update { 
                                it.copy(
                                    isLoading = false,
                                    crtshResult = result.data,
                                    selectedTab = MainTab.CRTSH
                                )
                            }
                        }
                        is AppResult.Error -> {
                            _state.update { it.copy(isLoading = false) }
                            handleError(result)
                        }
                    }
                }
        }
    }
    
    private fun handleClearResults() {
        _state.update { 
            it.copy(
                scanResult = null,
                crtshResult = null,
                searchQuery = "",
                error = null
            )
        }
    }
    
    private fun handleSelectTab(tab: MainTab) {
        _state.update { it.copy(selectedTab = tab) }
    }
    
    private fun handleToggleFavorite(entryId: Long) {
        viewModelScope.launch {
            toggleFavoriteUseCase(entryId)
                .onFailure { e ->
                    showError(MainError.UnknownError(e))
                }
        }
    }
    
    private fun handleDeleteHistory(entryId: Long) {
        viewModelScope.launch {
            deleteHistoryUseCase(entryId)
                .onFailure { e ->
                    showError(MainError.UnknownError(e))
                }
        }
    }
    
    private fun handleToggleHistory() {
        _state.update { it.copy(showHistory = !it.showHistory) }
    }
    
    private fun handleDismissError(errorId: String) {
        _state.update { it.copy(error = null) }
    }
    
    private fun loadHistory() {
        viewModelScope.launch {
            getHistoryUseCase()
                .catch { e ->
                    // Silently fail for history loading
                }
                .collect { history ->
                    _state.update { it.copy(history = history) }
                }
        }
    }
    
    private fun saveToHistory(scanResult: ScanResult) {
        viewModelScope.launch {
            saveHistoryUseCase(scanResult)
        }
    }
    
    private fun showError(error: MainError) {
        _state.update { it.copy(error = error) }
    }
    
    private fun handleError(result: AppResult.Error) {
        val error = when (result.errorCode) {
            ErrorCode.NETWORK_ERROR -> MainError.NetworkError()
            ErrorCode.VALIDATION_ERROR -> MainError.ValidationError("input")
            ErrorCode.SERVER_ERROR -> MainError.ApiError(500)
            else -> MainError.UnknownError(result.exception)
        }
        showError(error)
    }
    
    override fun onCleared() {
        super.onCleared()
        activeScanJob?.cancel()
    }
}
```

---

## 5. Hilt Dependency Injection Setup

### 5.1 Application Class

```kotlin
// InjectToolsApplication.kt
package com.hoshiyomix.injecttools

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class InjectToolsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Any app-level initialization
    }
}
```

### 5.2 Network Module

```kotlin
// di/NetworkModule.kt
package com.hoshiyomix.injecttools.di

import com.hoshiyomix.injecttools.data.remote.api.CrtshApiService
import com.hoshiyomix.injecttools.data.remote.api.ScannerApiService
import com.hoshiyomix.injecttools.common.util.Constants
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

// Qualifiers for different dispatchers
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) {
                        HttpLoggingInterceptor.Level.BODY
                    } else {
                        HttpLoggingInterceptor.Level.NONE
                    }
                }
            )
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("User-Agent", "InjectTools/1.0")
                    .addHeader("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
            .build()
    }
    
    @Provides
    @Singleton
    fun provideGsonConverterFactory(): GsonConverterFactory {
        return GsonConverterFactory.create()
    }
    
    @Provides
    @Singleton
    fun provideCrtshRetrofit(
        okHttpClient: OkHttpClient,
        gsonConverterFactory: GsonConverterFactory
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(Constants.CRTSH_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(gsonConverterFactory)
            .build()
    }
    
    @Provides
    @Singleton
    fun provideScannerRetrofit(
        okHttpClient: OkHttpClient,
        gsonConverterFactory: GsonConverterFactory
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(Constants.SCANNER_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(gsonConverterFactory)
            .build()
    }
    
    @Provides
    @Singleton
    fun provideCrtshApiService(retrofit: Retrofit): CrtshApiService {
        return retrofit.create(CrtshApiService::class.java)
    }
    
    @Provides
    @Singleton
    fun provideScannerApiService(retrofit: Retrofit): ScannerApiService {
        return retrofit.create(ScannerApiService::class.java)
    }
    
    // Coroutine Dispatchers
    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
    
    @Provides
    @MainDispatcher
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main
    
    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
```

### 5.3 Database Module

```kotlin
// di/DatabaseModule.kt
package com.hoshiyomix.injecttools.di

import android.content.Context
import androidx.room.Room
import com.hoshiyomix.injecttools.data.local.database.AppDatabase
import com.hoshiyomix.injecttools.data.local.database.dao.HistoryDao
import com.hoshiyomix.injecttools.data.local.database.dao.ScanResultDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "inject_tools_db"
        )
            .fallbackToDestructiveMigration() // For development; use proper migrations in production
            .build()
    }
    
    @Provides
    fun provideHistoryDao(database: AppDatabase): HistoryDao {
        return database.historyDao()
    }
    
    @Provides
    fun provideScanResultDao(database: AppDatabase): ScanResultDao {
        return database.scanResultDao()
    }
}
```

### 5.4 Repository Module

```kotlin
// di/RepositoryModule.kt
package com.hoshiyomix.injecttools.di

import com.hoshiyomix.injecttools.data.local.datasource.LocalDataSource
import com.hoshiyomix.injecttools.data.local.datasource.LocalDataSourceImpl
import com.hoshiyomix.injecttools.data.remote.datasource.RemoteDataSource
import com.hoshiyomix.injecttools.data.remote.datasource.RemoteDataSourceImpl
import com.hoshiyomix.injecttools.data.repository.CrtshRepositoryImpl
import com.hoshiyomix.injecttools.data.repository.ScannerRepositoryImpl
import com.hoshiyomix.injecttools.domain.repository.CrtshRepository
import com.hoshiyomix.injecttools.domain.repository.ScannerRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    
    // Repository bindings
    @Binds
    @Singleton
    abstract fun bindScannerRepository(
        impl: ScannerRepositoryImpl
    ): ScannerRepository
    
    @Binds
    @Singleton
    abstract fun bindCrtshRepository(
        impl: CrtshRepositoryImpl
    ): CrtshRepository
    
    // Data source bindings
    @Binds
    @Singleton
    abstract fun bindRemoteDataSource(
        impl: RemoteDataSourceImpl
    ): RemoteDataSource
    
    @Binds
    @Singleton
    abstract fun bindLocalDataSource(
        impl: LocalDataSourceImpl
    ): LocalDataSource
}
```

### 5.5 App Module

```kotlin
// di/AppModule.kt
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
        return context.getSharedPreferences(
            "inject_tools_prefs",
            Context.MODE_PRIVATE
        )
    }
}
```

---

## 6. Data Layer Implementation

### 6.1 API Services

```kotlin
// data/remote/api/CrtshApiService.kt
package com.hoshiyomix.injecttools.data.remote.api

import com.hoshiyomix.injecttools.data.remote.dto.CrtshResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

interface CrtshApiService {
    @GET("/")
    suspend fun queryCertificates(
        @Query("q") domain: String,
        @Query("output") format: String = "json"
    ): List<CrtshResponseDto>
}
```

```kotlin
// data/remote/api/ScannerApiService.kt
package com.hoshiyomix.injecttools.data.remote.api

import com.hoshiyomix.injecttools.data.remote.dto.ScanRequestDto
import com.hoshiyomix.injecttools.data.remote.dto.ScanResultDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ScannerApiService {
    @POST("scan")
    suspend fun initiateScan(@Body request: ScanRequestDto): ScanResultDto
    
    @GET("scan/{id}")
    suspend fun getScanResult(@Path("id") scanId: String): ScanResultDto
    
    @GET("scan/{id}/status")
    suspend fun getScanStatus(@Path("id") scanId: String): ScanStatusDto
}
```

### 6.2 DTOs (Data Transfer Objects)

```kotlin
// data/remote/dto/CrtshResponseDto.kt
package com.hoshiyomix.injecttools.data.remote.dto

import com.google.gson.annotations.SerializedName

data class CrtshResponseDto(
    @SerializedName("issuer_ca_id") val issuerCaId: Long?,
    @SerializedName("issuer_name") val issuerName: String?,
    @SerializedName("name_value") val nameValue: String?,
    @SerializedName("min_cert_id") val minCertId: Long?,
    @SerializedName("min_entry_timestamp") val minEntryTimestamp: String?,
    @SerializedName("not_before") val notBefore: String?,
    @SerializedName("not_after") val notAfter: String?
)
```

```kotlin
// data/remote/dto/ScanResultDto.kt
package com.hoshiyomix.injecttools.data.remote.dto

import com.google.gson.annotations.SerializedName

data class ScanResultDto(
    @SerializedName("id") val id: String,
    @SerializedName("domain") val domain: String,
    @SerializedName("ip") val ipAddress: String?,
    @SerializedName("ports") val ports: List<PortInfoDto>,
    @SerializedName("technologies") val technologies: List<TechnologyDto>,
    @SerializedName("headers") val headers: Map<String, String>?,
    @SerializedName("ssl") val sslInfo: SslInfoDto?,
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("status") val status: String
)

data class PortInfoDto(
    @SerializedName("port") val port: Int,
    @SerializedName("service") val service: String?,
    @SerializedName("state") val state: String?,
    @SerializedName("banner") val banner: String?
)

data class TechnologyDto(
    @SerializedName("name") val name: String,
    @SerializedName("version") val version: String?,
    @SerializedName("category") val category: String?
)

data class SslInfoDto(
    @SerializedName("issuer") val issuer: String?,
    @SerializedName("valid_from") val validFrom: Long?,
    @SerializedName("valid_to") val validTo: Long?,
    @SerializedName("protocol") val protocol: String?
)

data class ScanRequestDto(
    @SerializedName("domain") val domain: String,
    @SerializedName("options") val options: ScanOptionsDto?
)

data class ScanOptionsDto(
    @SerializedName("deep_scan") val deepScan: Boolean = false,
    @SerializedName("port_scan") val portScan: Boolean = true,
    @SerializedName("tech_detection") val techDetection: Boolean = true
)

data class ScanStatusDto(
    @SerializedName("id") val id: String,
    @SerializedName("status") val status: String,
    @SerializedName("progress") val progress: Int
)
```

### 6.3 Mappers

```kotlin
// data/mapper/ScanResultMapper.kt
package com.hoshiyomix.injecttools.data.mapper

import com.hoshiyomix.injecttools.data.local.database.entities.ScanResultEntity
import com.hoshiyomix.injecttools.data.remote.dto.ScanResultDto
import com.hoshiyomix.injecttools.domain.model.*

// DTO to Domain
fun ScanResultDto.toDomain(): ScanResult = ScanResult(
    id = id,
    domain = domain,
    ipAddress = ipAddress,
    ports = ports.map { it.toDomain() },
    technologies = technologies.map { it.toDomain() },
    headers = headers ?: emptyMap(),
    sslInfo = sslInfo?.toDomain(),
    scannedAt = timestamp,
    status = status.toScanStatus()
)

fun PortInfoDto.toDomain(): PortInfo = PortInfo(
    port = port,
    service = service ?: "unknown",
    state = state?.toPortState() ?: PortState.CLOSED,
    banner = banner
)

fun TechnologyDto.toDomain(): Technology = Technology(
    name = name,
    version = version,
    category = category ?: "unknown"
)

fun SslInfoDto.toDomain(): SslInfo = SslInfo(
    issuer = issuer ?: "Unknown",
    validFrom = validFrom ?: 0L,
    validTo = validTo ?: 0L,
    protocol = protocol ?: "Unknown"
)

// String to Enum mappings
fun String.toScanStatus(): ScanStatus = when (this.uppercase()) {
    "PENDING" -> ScanStatus.PENDING
    "IN_PROGRESS" -> ScanStatus.IN_PROGRESS
    "COMPLETED" -> ScanStatus.COMPLETED
    "FAILED" -> ScanStatus.FAILED
    else -> ScanStatus.PENDING
}

fun String.toPortState(): PortState = when (this.uppercase()) {
    "OPEN" -> PortState.OPEN
    "FILTERED" -> PortState.FILTERED
    else -> PortState.CLOSED
}

// Domain to Entity
fun ScanResult.toEntity(): ScanResultEntity = ScanResultEntity(
    id = id,
    domain = domain,
    ipAddress = ipAddress,
    portsJson = ports.toJson(), // Use TypeConverter or separate table
    technologiesJson = technologies.toJson(),
    headersJson = headers.toJson(),
    sslInfoJson = sslInfo?.toJson(),
    scannedAt = scannedAt,
    status = status.name
)

// Entity to Domain
fun ScanResultEntity.toDomain(): ScanResult = ScanResult(
    id = id,
    domain = domain,
    ipAddress = ipAddress,
    ports = portsJson.fromJson(),
    technologies = technologiesJson.fromJson(),
    headers = headersJson.fromJson(),
    sslInfo = sslInfoJson?.fromJson(),
    scannedAt = scannedAt,
    status = ScanStatus.valueOf(status)
)
```

### 6.4 Repository Implementation

```kotlin
// data/repository/ScannerRepositoryImpl.kt
package com.hoshiyomix.injecttools.data.repository

import com.hoshiyomix.injecttools.common.result.AppResult
import com.hoshiyomix.injecttools.common.result.safeApiCall
import com.hoshiyomix.injecttools.data.local.datasource.LocalDataSource
import com.hoshiyomix.injecttools.data.mapper.toDomain
import com.hoshiyomix.injecttools.data.mapper.toEntity
import com.hoshiyomix.injecttools.data.remote.datasource.RemoteDataSource
import com.hoshiyomix.injecttools.data.remote.dto.ScanRequestDto
import com.hoshiyomix.injecttools.domain.model.ScanResult
import com.hoshiyomix.injecttools.domain.repository.ScannerRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Named

class ScannerRepositoryImpl @Inject constructor(
    private val remoteDataSource: RemoteDataSource,
    private val localDataSource: LocalDataSource,
    @Named("IO") private val ioDispatcher: CoroutineDispatcher
) : ScannerRepository {
    
    override suspend fun scanDomain(domain: String): AppResult<ScanResult> {
        return safeApiCall {
            val dto = remoteDataSource.initiateScan(
                ScanRequestDto(
                    domain = domain,
                    options = null
                )
            )
            val result = dto.toDomain()
            // Cache the result
            localDataSource.saveScanResult(result.toEntity())
            result
        }
    }
    
    override fun getScanHistory(): Flow<List<ScanResult>> {
        return localDataSource
            .getScanHistory()
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(ioDispatcher)
    }
    
    override suspend fun getScanById(id: String): AppResult<ScanResult> {
        return safeApiCall {
            localDataSource.getScanById(id)?.toDomain()
                ?: throw NoSuchElementException("Scan result not found")
        }
    }
    
    override suspend fun cancelScan(id: String): AppResult<Unit> {
        return safeApiCall {
            remoteDataSource.cancelScan(id)
        }
    }
}
```

---

## 7. Presentation Layer (Compose UI)

### 7.1 Main Screen

```kotlin
// presentation/main/MainScreen.kt
package com.hoshiyomix.injecttools.presentation.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoshiyomix.injecttools.presentation.components.*
import com.hoshiyomix.injecttools.presentation.main.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel(),
    onNavigateToResult: (String) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val effects = viewModel.effects.collectAsState(initial = null)
    
    // Handle one-time effects
    LaunchedEffect(effects.value) {
        effects.value?.let { effect ->
            when (effect) {
                is MainEffect.NavigateToResult -> onNavigateToResult(effect.resultId)
                is MainEffect.ShowSnackbar -> {
                    // Show snackbar
                }
                is MainEffect.Vibrate -> {
                    // Trigger haptic feedback
                }
                is MainEffect.ShareResult -> {
                    // Launch share intent
                }
            }
        }
    }
    
    Scaffold(
        topBar = {
            MainTopBar(
                title = "InjectTools",
                onHistoryClick = { viewModel.onEvent(MainEvent.ToggleHistoryPanel) }
            )
        },
        snackbarHost = {
            // Snackbar host
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search Bar
            SearchBar(
                query = state.searchQuery,
                onQueryChange = { viewModel.onEvent(MainEvent.SearchDomain(it)) },
                onSearch = { viewModel.onEvent(MainEvent.ScanDomain) },
                isLoading = state.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
            
            // Tab Row
            MainTabRow(
                selectedTab = state.selectedTab,
                onTabSelected = { viewModel.onEvent(MainEvent.SelectTab(it)) }
            )
            
            // Content
            Box(modifier = Modifier.weight(1f)) {
                when (state.selectedTab) {
                    MainTab.SCAN -> ScanResultContent(
                        result = state.scanResult,
                        isLoading = state.isLoading
                    )
                    MainTab.CRTSH -> CrtshResultContent(
                        result = state.crtshResult,
                        isLoading = state.isLoading
                    )
                    MainTab.NETWORK -> NetworkScanContent()
                }
                
                // Loading overlay
                if (state.isLoading) {
                    LoadingOverlay()
                }
                
                // Error display
                state.error?.let { error ->
                    ErrorDialog(
                        error = error,
                        onDismiss = { viewModel.onEvent(MainEvent.DismissError(error.id)) }
                    )
                }
            }
        }
    }
    
    // History panel (bottom sheet or side panel)
    if (state.showHistory) {
        HistoryBottomSheet(
            history = state.history,
            onToggleFavorite = { viewModel.onEvent(MainEvent.ToggleHistoryFavorite(it)) },
            onDelete = { viewModel.onEvent(MainEvent.DeleteHistoryEntry(it)) },
            onDismiss = { viewModel.onEvent(MainEvent.ToggleHistoryPanel) }
        )
    }
}
```

### 7.2 Minimal Activity

```kotlin
// MainActivity.kt
package com.hoshiyomix.injecttools

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hoshiyomix.injecttools.presentation.main.MainScreen
import com.hoshiyomix.injecttools.presentation.theme.InjectToolsTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            InjectToolsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    
                    NavHost(
                        navController = navController,
                        startDestination = "main"
                    ) {
                        composable("main") {
                            MainScreen(
                                onNavigateToResult = { resultId ->
                                    navController.navigate("result/$resultId")
                                }
                            )
                        }
                        
                        composable("result/{resultId}") { backStackEntry ->
                            val resultId = backStackEntry.arguments?.getString("resultId")
                            // ResultDetailScreen(resultId)
                        }
                    }
                }
            }
        }
    }
}
```

---

## 8. Migration Strategy

### 8.1 Incremental Migration Phases

```
┌─────────────────────────────────────────────────────────────────────┐
│                        PHASE 1: FOUNDATION                          │
│  Duration: 1-2 days                                                  │
│  - Add Hilt dependencies to build.gradle                            │
│  - Create InjectToolsApplication class                              │
│  - Set up basic DI modules (NetworkModule, AppModule)               │
│  - Create package structure                                         │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                        PHASE 2: DATA LAYER                          │
│  Duration: 3-4 days                                                  │
│  - Extract DTOs from existing code                                  │
│  - Create API Service interfaces                                    │
│  - Implement Room database                                          │
│  - Create Repository implementations                                │
│  - Add mappers between DTO/Entity/Domain                           │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                        PHASE 3: DOMAIN LAYER                        │
│  Duration: 2-3 days                                                  │
│  - Define Domain Models                                             │
│  - Create Repository interfaces                                     │
│  - Implement Use Cases                                              │
│  - Add Result wrapper sealed class                                  │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                     PHASE 4: PRESENTATION LAYER                     │
│  Duration: 3-4 days                                                  │
│  - Create ViewModel classes                                         │
│  - Define UI State/Event contracts                                  │
│  - Extract Compose UI from MainActivity                             │
│  - Implement StateFlow for UI updates                               │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                        PHASE 5: TESTING                             │
│  Duration: 2-3 days                                                  │
│  - Write unit tests for Use Cases                                   │
│  - Write unit tests for ViewModels                                  │
│  - Add integration tests for Repositories                           │
│  - Set up test doubles (fake repositories)                          │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                        PHASE 6: CLEANUP                             │
│  Duration: 1-2 days                                                  │
│  - Remove old code                                                  │
│  - Update documentation                                             │
│  - Code review and refactor                                         │
│  - Performance optimization                                         │
└─────────────────────────────────────────────────────────────────────┘
```

### 8.2 Detailed Migration Steps

#### Phase 1: Foundation Setup

```kotlin
// build.gradle.kts (app level) - Add these dependencies
dependencies {
    // Hilt
    implementation("com.google.dagger:hilt-android:2.51")
    ksp("com.google.dagger:hilt-compiler:2.51")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    
    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    
    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    
    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    testImplementation("app.cash.turbine:turbine:1.1.0")
    testImplementation("io.mockk:mockk:1.13.10")
    testImplementation("com.google.dagger:hilt-android-testing:2.51")
    kspTest("com.google.dagger:hilt-compiler:2.51")
    androidTestImplementation("androidx.arch.core:core-testing:2.2.0")
}
```

#### Phase 2-4: File-by-File Migration

**Step 1: Extract from Scanner.kt**
```
Scanner.kt (original)
    ├── → data/remote/api/ScannerApiService.kt
    ├── → data/remote/dto/*Dto.kt
    ├── → data/mapper/ScanResultMapper.kt
    ├── → domain/model/ScanResult.kt
    └── → domain/usecase/ScanDomainUseCase.kt
```

**Step 2: Extract from Crtsh.kt**
```
Crtsh.kt (original)
    ├── → data/remote/api/CrtshApiService.kt
    ├── → data/remote/dto/CrtshResponseDto.kt
    ├── → data/mapper/CrtshMapper.kt
    ├── → domain/model/CrtshResult.kt
    └── → domain/usecase/GetCrtshDataUseCase.kt
```

**Step 3: Extract from HistoryStorage.kt**
```
HistoryStorage.kt (original)
    ├── → data/local/database/AppDatabase.kt
    ├── → data/local/database/dao/HistoryDao.kt
    ├── → data/local/database/entities/HistoryEntity.kt
    ├── → data/local/datasource/LocalDataSource.kt
    └── → domain/model/HistoryEntry.kt
```

**Step 4: Extract from NetworkUtils.kt**
```
NetworkUtils.kt (original)
    ├── → common/util/NetworkUtil.kt
    └── → di/NetworkModule.kt
```

**Step 5: Refactor MainActivity.kt**
```
MainActivity.kt (1000+ lines)
    ├── → presentation/main/MainScreen.kt (UI)
    ├── → presentation/main/MainViewModel.kt (Logic)
    ├── → presentation/main/MainContract.kt (State/Events)
    ├── → presentation/main/components/*.kt (UI Components)
    ├── → presentation/theme/*.kt (Theming)
    └── → MainActivity.kt (Minimal entry point)
```

---

## 9. Testing Strategy

### 9.1 Test Pyramid

```
                    ┌─────────────┐
                    │    E2E      │  (UI Automator tests)
                    │   Tests     │  ~5% of tests
                   ─┴─────────────┴─
                  ┌─────────────────┐
                  │  Integration    │  (Repository tests with real DB/API)
                  │     Tests       │  ~15% of tests
                 ─┴─────────────────┴─
                ┌─────────────────────┐
                │     Unit Tests      │  (ViewModel, UseCase, Mapper tests)
                │                     │  ~80% of tests
               ─┴─────────────────────┴─
```

### 9.2 Unit Tests

#### Use Case Tests

```kotlin
// domain/usecase/ScanDomainUseCaseTest.kt
package com.hoshiyomix.injecttools.domain.usecase

import com.hoshiyomix.injecttools.common.result.AppResult
import com.hoshiyomix.injecttools.domain.model.ScanResult
import com.hoshiyomix.injecttools.domain.repository.ScannerRepository
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ScanDomainUseCaseTest {
    
    private lateinit var useCase: ScanDomainUseCase
    private val mockRepository: ScannerRepository = mockk()
    private val testDispatcher = StandardTestDispatcher()
    
    @Before
    fun setup() {
        useCase = ScanDomainUseCase(mockRepository, testDispatcher)
    }
    
    @Test
    fun `invoke with valid domain returns Success`() = runTest {
        // Given
        val domain = "example.com"
        val expectedResult = ScanResult(
            id = "123",
            domain = domain,
            ipAddress = "1.2.3.4",
            ports = emptyList(),
            technologies = emptyList(),
            headers = emptyMap(),
            sslInfo = null,
            scannedAt = System.currentTimeMillis(),
            status = com.hoshiyomix.injecttools.domain.model.ScanStatus.COMPLETED
        )
        coEvery { mockRepository.scanDomain(domain) } returns AppResult.Success(expectedResult)
        
        // When
        val results = useCase(domain).toList()
        
        // Then
        assertEquals(2, results.size)
        assertTrue(results[0] is AppResult.Loading)
        assertTrue(results[1] is AppResult.Success)
        assertEquals(expectedResult, (results[1] as AppResult.Success).data)
    }
    
    @Test
    fun `invoke with invalid domain returns Error`() = runTest {
        // Given
        val invalidDomain = "invalid..domain"
        
        // When
        val results = useCase(invalidDomain).toList()
        
        // Then
        assertTrue(results.any { it is AppResult.Error })
    }
    
    @Test
    fun `invoke trims and lowercases domain`() = runTest {
        // Given
        val domain = "  EXAMPLE.COM  "
        coEvery { mockRepository.scanDomain("example.com") } returns 
            AppResult.Success(mockk())
        
        // When
        useCase(domain).first()
        
        // Then
        coVerify { mockRepository.scanDomain("example.com") }
    }
}
```

#### ViewModel Tests

```kotlin
// presentation/main/MainViewModelTest.kt
package com.hoshiyomix.injecttools.presentation.main

import app.cash.turbine.test
import com.hoshiyomix.injecttools.common.result.AppResult
import com.hoshiyomix.injecttools.domain.model.ScanResult
import com.hoshiyomix.injecttools.domain.usecase.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    
    @get:Rule
    val dispatcherRule = StandardTestDispatcher()
    
    private lateinit var viewModel: MainViewModel
    private val scanDomainUseCase: ScanDomainUseCase = mockk()
    private val getCrtshDataUseCase: GetCrtshDataUseCase = mockk()
    private val getHistoryUseCase: GetHistoryUseCase = mockk()
    private val saveHistoryUseCase: SaveHistoryUseCase = mockk()
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase = mockk()
    private val deleteHistoryUseCase: DeleteHistoryUseCase = mockk()
    
    @Before
    fun setup() {
        MockKAnnotations.init(this)
        
        coEvery { getHistoryUseCase(any(), any()) } returns flowOf(emptyList())
        
        viewModel = MainViewModel(
            scanDomainUseCase = scanDomainUseCase,
            getCrtshDataUseCase = getCrtshDataUseCase,
            getHistoryUseCase = getHistoryUseCase,
            saveHistoryUseCase = saveHistoryUseCase,
            toggleFavoriteUseCase = toggleFavoriteUseCase,
            deleteHistoryUseCase = deleteHistoryUseCase,
            ioDispatcher = StandardTestDispatcher()
        )
    }
    
    @Test
    fun `initial state is correct`() = runTest {
        // When
        val state = viewModel.state.value
        
        // Then
        assertFalse(state.isLoading)
        assertEquals(null, state.scanResult)
        assertEquals("", state.searchQuery)
        assertEquals(MainTab.SCAN, state.selectedTab)
    }
    
    @Test
    fun `SearchDomain event updates searchQuery`() = runTest {
        // Given
        val query = "example.com"
        
        // When
        viewModel.onEvent(MainEvent.SearchDomain(query))
        
        // Then
        assertEquals(query, viewModel.state.value.searchQuery)
    }
    
    @Test
    fun `ScanDomain event with empty query shows error`() = runTest {
        viewModel.state.test {
            // When
            viewModel.onEvent(MainEvent.ScanDomain)
            
            // Then
            val state = awaitItem()
            assertTrue(state.error is MainError.ValidationError)
        }
    }
    
    @Test
    fun `ScanDomain event with valid domain updates state`() = runTest {
        // Given
        val domain = "example.com"
        val scanResult = mockk<ScanResult>()
        
        coEvery { scanDomainUseCase(domain) } returns flow {
            emit(AppResult.Loading)
            emit(AppResult.Success(scanResult))
        }
        coEvery { saveHistoryUseCase(any()) } returns Result.success(Unit)
        
        viewModel.state.test {
            // Set search query
            viewModel.onEvent(MainEvent.SearchDomain(domain))
            skipItems(1) // Skip the query update
            
            // When
            viewModel.onEvent(MainEvent.ScanDomain)
            
            // Then - Loading
            assertTrue(awaitItem().isLoading)
            
            // Then - Success
            val finalState = awaitItem()
            assertFalse(finalState.isLoading)
            assertEquals(scanResult, finalState.scanResult)
        }
    }
    
    @Test
    fun `SelectTab event updates selectedTab`() = runTest {
        // When
        viewModel.onEvent(MainEvent.SelectTab(MainTab.CRTSH))
        
        // Then
        assertEquals(MainTab.CRTSH, viewModel.state.value.selectedTab)
    }
    
    @Test
    fun `ClearResults event resets state`() = runTest {
        // Given - set some state
        viewModel.onEvent(MainEvent.SearchDomain("test.com"))
        viewModel.onEvent(MainEvent.SelectTab(MainTab.CRTSH))
        
        // When
        viewModel.onEvent(MainEvent.ClearResults)
        
        // Then
        val state = viewModel.state.value
        assertEquals("", state.searchQuery)
        assertEquals(null, state.scanResult)
        assertEquals(null, state.crtshResult)
        assertEquals(MainTab.SCAN, state.selectedTab)
    }
}
```

### 9.3 Repository Integration Tests

```kotlin
// data/repository/ScannerRepositoryImplTest.kt
package com.hoshiyomix.injecttools.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hoshiyomix.injecttools.common.result.AppResult
import com.hoshiyomix.injecttools.data.local.database.AppDatabase
import com.hoshiyomix.injecttools.data.local.datasource.LocalDataSourceImpl
import com.hoshiyomix.injecttools.data.remote.datasource.RemoteDataSourceImpl
import io.mockk.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertTrue

class ScannerRepositoryImplIntegrationTest {
    
    private lateinit var database: AppDatabase
    private lateinit var repository: ScannerRepositoryImpl
    
    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        
        // Use real local data source with in-memory database
        val localDataSource = LocalDataSourceImpl(database)
        val remoteDataSource = mockk<RemoteDataSourceImpl>()
        
        repository = ScannerRepositoryImpl(
            remoteDataSource = remoteDataSource,
            localDataSource = localDataSource,
            ioDispatcher = StandardTestDispatcher()
        )
    }
    
    @After
    fun tearDown() {
        database.close()
    }
    
    @Test
    fun `scanDomain saves result to local database`() = runTest {
        // Test that scanned results are persisted
        // ...
    }
    
    @Test
    fun `getScanHistory returns cached results`() = runTest {
        // Test that history is loaded from local database
        // ...
    }
}
```

### 9.4 Test Doubles (Fakes)

```kotlin
// test/java/.../testdoubles/FakeScannerRepository.kt
package com.hoshiyomix.injecttools.testdoubles

import com.hoshiyomix.injecttools.common.result.AppResult
import com.hoshiyomix.injecttools.domain.model.ScanResult
import com.hoshiyomix.injecttools.domain.repository.ScannerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeScannerRepository : ScannerRepository {
    
    var scanResult: AppResult<ScanResult> = AppResult.Success(mockk())
    var shouldThrow: Exception? = null
    
    override suspend fun scanDomain(domain: String): AppResult<ScanResult> {
        shouldThrow?.let { throw it }
        return scanResult
    }
    
    override fun getScanHistory(): Flow<List<ScanResult>> {
        return flowOf(emptyList())
    }
    
    override suspend fun getScanById(id: String): AppResult<ScanResult> {
        return scanResult
    }
    
    override suspend fun cancelScan(id: String): AppResult<Unit> {
        return AppResult.Success(Unit)
    }
}
```

---

## 10. Build Configuration (build.gradle.kts)

```kotlin
// app/build.gradle.kts
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("kotlinx-serialization")
}

android {
    namespace = "com.hoshiyomix.injecttools"
    compileSdk = 34
    
    defaultConfig {
        applicationId = "com.hoshiyomix.injecttools"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }
    
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
    }
    
    buildFeatures {
        compose = true
        buildConfig = true
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11"
    }
    
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    
    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    
    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")
    
    // Hilt
    implementation("com.google.dagger:hilt-android:2.51")
    ksp("com.google.dagger:hilt-compiler:2.51")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    
    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    
    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    
    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    
    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    
    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    
    // Unit Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    testImplementation("app.cash.turbine:turbine:1.1.0")
    testImplementation("io.mockk:mockk:1.13.10")
    testImplementation("io.mockk:mockk-android:1.13.10")
    testImplementation("com.google.dagger:hilt-android-testing:2.51")
    kspTest("com.google.dagger:hilt-compiler:2.51")
    
    // Android Testing
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.arch.core:core-testing:2.2.0")
}
```

---

## 11. Summary & Next Steps

### 11.1 Architecture Benefits

| Benefit | Description |
|---------|-------------|
| **Testability** | Each layer can be tested independently with mocked dependencies |
| **Maintainability** | Clear separation of concerns makes code easier to understand and modify |
| **Scalability** | New features can be added without touching existing code |
| **Flexibility** | Data sources can be swapped without affecting business logic |
| **Debugging** | Issues can be isolated to specific layers |

### 11.2 Implementation Checklist

- [ ] **Phase 1**: Add Hilt dependencies and create basic DI setup
- [ ] **Phase 2**: Extract data layer (DTOs, API services, mappers)
- [ ] **Phase 3**: Implement domain layer (models, repositories, use cases)
- [ ] **Phase 4**: Create ViewModels and extract UI from MainActivity
- [ ] **Phase 5**: Write unit tests for core functionality
- [ ] **Phase 6**: Remove old code and clean up

### 11.3 Key Files to Create (Priority Order)

1. `InjectToolsApplication.kt` - Hilt entry point
2. `di/NetworkModule.kt` - Network dependencies
3. `common/result/AppResult.kt` - Error handling
4. `domain/model/*.kt` - Domain models
5. `domain/repository/*.kt` - Repository interfaces
6. `data/remote/api/*.kt` - API services
7. `data/remote/dto/*.kt` - DTOs
8. `data/mapper/*.kt` - Mappers
9. `data/repository/*.kt` - Repository implementations
10. `domain/usecase/*.kt` - Use cases
11. `presentation/main/MainContract.kt` - UI state/events
12. `presentation/main/MainViewModel.kt` - ViewModel
13. `presentation/main/MainScreen.kt` - Compose UI

### 11.4 Risk Mitigation

| Risk | Mitigation |
|------|------------|
| Breaking existing functionality | Incremental migration, keep old code until new is tested |
| Learning curve for Hilt | Start with simple modules, add complexity gradually |
| Testing gaps | Write tests as you migrate, not after |
| Performance regression | Profile before/after migration |

---

**Document Version**: 1.0  
**Last Updated**: 2024  
**Target Kotlin Version**: 2.0+  
**Target Android SDK**: 34
