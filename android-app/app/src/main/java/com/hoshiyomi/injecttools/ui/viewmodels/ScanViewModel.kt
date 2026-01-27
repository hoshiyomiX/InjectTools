package com.hoshiyomi.injecttools.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoshiyomi.injecttools.core.InjectToolsKotlin
import com.hoshiyomi.injecttools.core.LogManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ScanViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private val _results = MutableStateFlow<List<InjectToolsKotlin.ScanResult>>(emptyList())
    val results: StateFlow<List<InjectToolsKotlin.ScanResult>> = _results.asStateFlow()

    init {
        LogManager.i("ScanViewModel", "ViewModel initialized")
    }

    fun startScan(target: String, subdomains: List<String>) {
        LogManager.i("ScanViewModel", "startScan called")
        LogManager.d("ScanViewModel", "Target: $target")
        LogManager.d("ScanViewModel", "Subdomains count: ${subdomains.size}")
        
        if (target.isBlank()) {
            LogManager.w("ScanViewModel", "Target is blank")
            _uiState.value = ScanUiState.Error("Target tidak boleh kosong")
            return
        }
        
        if (subdomains.isEmpty()) {
            LogManager.w("ScanViewModel", "Subdomains list is empty")
            _uiState.value = ScanUiState.Error("Subdomain list kosong")
            return
        }

        viewModelScope.launch {
            try {
                LogManager.i("ScanViewModel", "Launching coroutine")
                _uiState.value = ScanUiState.Scanning
                _results.value = emptyList()
                
                LogManager.i("ScanViewModel", "Calling batchTest...")
                val scanResults = InjectToolsKotlin.batchTest(target, subdomains)
                
                LogManager.i("ScanViewModel", "batchTest returned ${scanResults.size} results")
                _results.value = scanResults
                
                val workingCount = scanResults.count { it.isWorking }
                if (workingCount > 0) {
                    LogManager.i("ScanViewModel", "Scan SUCCESS: $workingCount working")
                    _uiState.value = ScanUiState.Success(workingCount)
                } else {
                    LogManager.w("ScanViewModel", "No working subdomains found")
                    _uiState.value = ScanUiState.NoResults("Tidak ada subdomain yang working")
                }
                
            } catch (e: Exception) {
                LogManager.e("ScanViewModel", "Scan FAILED with exception", e)
                _uiState.value = ScanUiState.Error(e.message ?: "Unknown error")
            }
        }
        
        LogManager.i("ScanViewModel", "startScan method completed")
    }

    fun resetScan() {
        LogManager.i("ScanViewModel", "resetScan called")
        _uiState.value = ScanUiState.Idle
        _results.value = emptyList()
    }
}

sealed class ScanUiState {
    object Idle : ScanUiState()
    object Scanning : ScanUiState()
    data class Success(val workingCount: Int) : ScanUiState()
    data class NoResults(val reason: String) : ScanUiState()
    data class Error(val message: String) : ScanUiState()
}
