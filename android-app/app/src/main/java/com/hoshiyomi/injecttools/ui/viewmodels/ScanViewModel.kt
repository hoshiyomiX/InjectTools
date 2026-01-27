package com.hoshiyomi.injecttools.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoshiyomi.injecttools.core.InjectToolsKotlin
import com.hoshiyomi.injecttools.core.LogManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ScanViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private val _results = MutableStateFlow<List<InjectToolsKotlin.ScanResult>>(emptyList())
    val results: StateFlow<List<InjectToolsKotlin.ScanResult>> = _results.asStateFlow()

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        LogManager.e("ScanViewModel", "Coroutine exception caught!", throwable)
        _uiState.value = ScanUiState.Error("Coroutine error: ${throwable.message}")
    }

    init {
        try {
            LogManager.i("ScanViewModel", "=== ScanViewModel INIT ===")
            LogManager.i("ScanViewModel", "Thread: ${Thread.currentThread().name}")
            LogManager.i("ScanViewModel", "ViewModel hashCode: ${this.hashCode()}")
        } catch (e: Exception) {
            LogManager.e("ScanViewModel", "Init failed", e)
        }
    }

    fun startScan(target: String, subdomains: List<String>) {
        try {
            LogManager.i("ScanViewModel", "\n========================================")
            LogManager.i("ScanViewModel", "startScan CALLED")
            LogManager.i("ScanViewModel", "Thread: ${Thread.currentThread().name}")
            LogManager.d("ScanViewModel", "Target: '$target'")
            LogManager.d("ScanViewModel", "Subdomains: ${subdomains.size} items")
            subdomains.forEachIndexed { i, sub ->
                LogManager.d("ScanViewModel", "  [$i] $sub")
            }
            
            if (target.isBlank()) {
                LogManager.w("ScanViewModel", "Target is BLANK")
                _uiState.value = ScanUiState.Error("Target tidak boleh kosong")
                return
            }
            
            if (subdomains.isEmpty()) {
                LogManager.w("ScanViewModel", "Subdomains list is EMPTY")
                _uiState.value = ScanUiState.Error("Subdomain list kosong")
                return
            }

            LogManager.i("ScanViewModel", "Validation PASSED")
            LogManager.i("ScanViewModel", "Launching coroutine...")
            
            viewModelScope.launch(exceptionHandler) {
                try {
                    LogManager.i("ScanViewModel", "Inside coroutine")
                    LogManager.i("ScanViewModel", "Coroutine thread: ${Thread.currentThread().name}")
                    
                    LogManager.i("ScanViewModel", "Setting state to Scanning")
                    _uiState.value = ScanUiState.Scanning
                    
                    LogManager.i("ScanViewModel", "Clearing previous results")
                    _results.value = emptyList()
                    
                    LogManager.i("ScanViewModel", "Calling InjectToolsKotlin.batchTest...")
                    LogManager.i("ScanViewModel", "  target='$target'")
                    LogManager.i("ScanViewModel", "  subdomains.size=${subdomains.size}")
                    
                    val scanResults = InjectToolsKotlin.batchTest(target, subdomains)
                    
                    LogManager.i("ScanViewModel", "batchTest RETURNED")
                    LogManager.i("ScanViewModel", "Results count: ${scanResults.size}")
                    
                    _results.value = scanResults
                    LogManager.i("ScanViewModel", "Results saved to StateFlow")
                    
                    val workingCount = scanResults.count { it.isWorking }
                    LogManager.i("ScanViewModel", "Working count: $workingCount")
                    
                    if (workingCount > 0) {
                        LogManager.i("ScanViewModel", "Setting state to SUCCESS")
                        _uiState.value = ScanUiState.Success(workingCount)
                    } else {
                        LogManager.w("ScanViewModel", "No working subdomains, setting state to NoResults")
                        _uiState.value = ScanUiState.NoResults("Tidak ada subdomain yang working")
                    }
                    
                    LogManager.i("ScanViewModel", "Scan completed successfully")
                    
                } catch (e: Exception) {
                    LogManager.e("ScanViewModel", "EXCEPTION in coroutine", e)
                    LogManager.e("ScanViewModel", "Exception class: ${e.javaClass.name}")
                    LogManager.e("ScanViewModel", "Exception message: ${e.message}")
                    LogManager.e("ScanViewModel", "Stack trace:")
                    e.stackTrace.take(10).forEach { element ->
                        LogManager.e("ScanViewModel", "  at $element")
                    }
                    _uiState.value = ScanUiState.Error(e.message ?: "Unknown error")
                }
            }
            
            LogManager.i("ScanViewModel", "Coroutine launched successfully")
            LogManager.i("ScanViewModel", "startScan method COMPLETED")
            LogManager.i("ScanViewModel", "========================================\n")
            
        } catch (e: Exception) {
            LogManager.e("ScanViewModel", "EXCEPTION in startScan (before coroutine)", e)
            _uiState.value = ScanUiState.Error("Pre-coroutine error: ${e.message}")
        }
    }

    fun resetScan() {
        try {
            LogManager.i("ScanViewModel", "resetScan called")
            _uiState.value = ScanUiState.Idle
            _results.value = emptyList()
        } catch (e: Exception) {
            LogManager.e("ScanViewModel", "resetScan failed", e)
        }
    }
}

sealed class ScanUiState {
    object Idle : ScanUiState()
    object Scanning : ScanUiState()
    data class Success(val workingCount: Int) : ScanUiState()
    data class NoResults(val reason: String) : ScanUiState()
    data class Error(val message: String) : ScanUiState()
}
