package com.hoshiyomi.injecttools.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoshiyomi.injecttools.core.InjectToolsKotlin
import com.hoshiyomi.injecttools.core.InjectToolsKotlin.ScanResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ScanViewModel : ViewModel() {
    
    private val _uiState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()
    
    private val _scanResults = MutableStateFlow<List<ScanResult>>(emptyList())
    val scanResults: StateFlow<List<ScanResult>> = _scanResults.asStateFlow()
    
    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()
    
    sealed class ScanUiState {
        object Idle : ScanUiState()
        object Scanning : ScanUiState()
        data class Success(val result: ScanResult) : ScanUiState()
        data class BatchSuccess(val results: List<ScanResult>) : ScanUiState()
        data class Error(val message: String) : ScanUiState()
    }
    
    fun testSingleSubdomain(target: String, subdomain: String) {
        viewModelScope.launch {
            try {
                _uiState.value = ScanUiState.Scanning
                
                // Validate input
                if (target.isBlank() || subdomain.isBlank()) {
                    _uiState.value = ScanUiState.Error("Target and subdomain cannot be empty")
                    return@launch
                }
                
                // Check target first
                val isTargetOnline = InjectToolsKotlin.checkTargetOnline(target)
                if (!isTargetOnline) {
                    _uiState.value = ScanUiState.Error("Target is unreachable: $target")
                    return@launch
                }
                
                // Test subdomain
                val result = InjectToolsKotlin.testSubdomain(target, subdomain, timeout = 5)
                
                _scanResults.value = listOf(result)
                _uiState.value = ScanUiState.Success(result)
                
            } catch (e: Exception) {
                _uiState.value = ScanUiState.Error(e.message ?: "Unknown error")
            }
        }
    }
    
    fun testBatchSubdomains(target: String, subdomains: List<String>) {
        viewModelScope.launch {
            try {
                _uiState.value = ScanUiState.Scanning
                _progress.value = 0f
                
                // Validate input
                if (target.isBlank()) {
                    _uiState.value = ScanUiState.Error("Target cannot be empty")
                    return@launch
                }
                
                if (subdomains.isEmpty()) {
                    _uiState.value = ScanUiState.Error("Subdomain list is empty")
                    return@launch
                }
                
                // Check target first
                val isTargetOnline = InjectToolsKotlin.checkTargetOnline(target)
                if (!isTargetOnline) {
                    _uiState.value = ScanUiState.Error("Target is unreachable: $target")
                    return@launch
                }
                
                // Batch test with progress tracking
                val results = mutableListOf<ScanResult>()
                val total = subdomains.size
                
                subdomains.forEachIndexed { index, subdomain ->
                    val result = InjectToolsKotlin.testSubdomain(target, subdomain, timeout = 5)
                    results.add(result)
                    
                    // Update progress
                    _progress.value = (index + 1).toFloat() / total
                    _scanResults.value = results.toList()
                }
                
                _uiState.value = ScanUiState.BatchSuccess(results)
                
            } catch (e: Exception) {
                _uiState.value = ScanUiState.Error(e.message ?: "Unknown error")
            }
        }
    }
    
    fun reset() {
        _uiState.value = ScanUiState.Idle
        _scanResults.value = emptyList()
        _progress.value = 0f
    }
}
