package com.hoshiyomi.injecttools.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoshiyomi.injecttools.core.InjectToolsNative
import com.hoshiyomi.injecttools.core.ScanResult
import kotlinx.coroutines.launch

sealed class ScanState {
    object Idle : ScanState()
    object Scanning : ScanState()
    data class Success(val results: List<ScanResult>) : ScanState()
    data class Error(val message: String) : ScanState()
}

class ScanViewModel : ViewModel() {
    var state by mutableStateOf<ScanState>(ScanState.Idle)
        private set
    
    var target by mutableStateOf("")
    var subdomain by mutableStateOf("")
    var subdomainList by mutableStateOf("")
    var timeout by mutableStateOf(10)
    
    var isBatchMode by mutableStateOf(false)
    var targetOnline by mutableStateOf<Boolean?>(null)
    
    fun checkTarget() {
        if (target.isBlank()) return
        
        viewModelScope.launch {
            try {
                targetOnline = InjectToolsNative.isTargetOnline(target)
            } catch (e: Exception) {
                targetOnline = false
            }
        }
    }
    
    fun scanSingle() {
        if (target.isBlank() || subdomain.isBlank()) {
            state = ScanState.Error("Target and subdomain are required")
            return
        }
        
        state = ScanState.Scanning
        
        viewModelScope.launch {
            try {
                val result = InjectToolsNative.scanSubdomain(
                    target = target,
                    subdomain = subdomain,
                    timeout = timeout
                )
                state = ScanState.Success(listOf(result))
            } catch (e: Exception) {
                state = ScanState.Error(e.message ?: "Unknown error")
            }
        }
    }
    
    fun scanBatch() {
        if (target.isBlank() || subdomainList.isBlank()) {
            state = ScanState.Error("Target and subdomain list are required")
            return
        }
        
        val subdomains = subdomainList
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
        
        if (subdomains.isEmpty()) {
            state = ScanState.Error("No valid subdomains provided")
            return
        }
        
        state = ScanState.Scanning
        
        viewModelScope.launch {
            try {
                val results = InjectToolsNative.scanBatch(
                    target = target,
                    subdomains = subdomains,
                    timeout = timeout
                )
                state = ScanState.Success(results)
            } catch (e: Exception) {
                state = ScanState.Error(e.message ?: "Unknown error")
            }
        }
    }
    
    fun reset() {
        state = ScanState.Idle
        targetOnline = null
    }
}
