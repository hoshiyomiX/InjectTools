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

    fun startScan(target: String, subdomains: List<String>) {
        if (target.isBlank() || subdomains.isEmpty()) {
            _uiState.value = ScanUiState.Error("Target dan subdomain tidak boleh kosong")
            return
        }

        viewModelScope.launch {
            try {
                _uiState.value = ScanUiState.Scanning
                _results.value = emptyList()
                
                LogManager.i("ScanViewModel", "Starting scan for target: $target")
                LogManager.i("ScanViewModel", "Subdomains to test: ${subdomains.size}")
                
                // Langsung scan, no target check!
                // Target validity akan terlihat dari TLS handshake success
                val scanResults = InjectToolsKotlin.batchTest(target, subdomains)
                
                _results.value = scanResults
                
                val workingCount = scanResults.count { it.isWorking }
                if (workingCount > 0) {
                    LogManager.i("ScanViewModel", "Scan completed: $workingCount working subdomains found")
                    _uiState.value = ScanUiState.Success(workingCount)
                } else {
                    LogManager.w("ScanViewModel", "Scan completed: No working subdomains found")
                    _uiState.value = ScanUiState.NoResults("Target might be invalid or all subdomains failed")
                }
                
            } catch (e: Exception) {
                LogManager.e("ScanViewModel", "Scan failed", e)
                _uiState.value = ScanUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun resetScan() {
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
