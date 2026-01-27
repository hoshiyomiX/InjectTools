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

class DiscoverViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<DiscoverUiState>(DiscoverUiState.Idle)
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

    private val _subdomains = MutableStateFlow<List<String>>(emptyList())
    val subdomains: StateFlow<List<String>> = _subdomains.asStateFlow()

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        LogManager.e("DiscoverViewModel", "Coroutine exception caught!", throwable)
        _uiState.value = DiscoverUiState.Error("Crash: ${throwable.message}")
    }

    init {
        LogManager.i("DiscoverViewModel", "ViewModel initialized")
    }

    fun discoverSubdomains(domain: String, limit: Int = 100) {
        if (domain.isBlank()) {
            LogManager.w("DiscoverViewModel", "Domain is blank")
            _uiState.value = DiscoverUiState.Error("Domain tidak boleh kosong")
            return
        }

        LogManager.i("DiscoverViewModel", "Starting discovery for: $domain (limit=$limit)")

        viewModelScope.launch(exceptionHandler) {
            try {
                _uiState.value = DiscoverUiState.Discovering
                _subdomains.value = emptyList()
                
                LogManager.i("DiscoverViewModel", "Calling InjectToolsKotlin.discoverSubdomains...")
                
                val discovered = InjectToolsKotlin.discoverSubdomains(domain, limit)
                
                LogManager.i("DiscoverViewModel", "Discovery returned: ${discovered.size} subdomains")
                
                _subdomains.value = discovered
                
                if (discovered.isNotEmpty()) {
                    LogManager.i("DiscoverViewModel", "Discovery completed: ${discovered.size} subdomains found")
                    _uiState.value = DiscoverUiState.Success(discovered.size)
                } else {
                    LogManager.w("DiscoverViewModel", "No subdomains found for $domain")
                    _uiState.value = DiscoverUiState.Error("Tidak ada subdomain ditemukan")
                }
                
            } catch (e: Exception) {
                LogManager.e("DiscoverViewModel", "Discovery failed", e)
                _uiState.value = DiscoverUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun reset() {
        LogManager.i("DiscoverViewModel", "Resetting state")
        _uiState.value = DiscoverUiState.Idle
        _subdomains.value = emptyList()
    }
}

sealed class DiscoverUiState {
    object Idle : DiscoverUiState()
    object Discovering : DiscoverUiState()
    data class Success(val count: Int) : DiscoverUiState()
    data class Error(val message: String) : DiscoverUiState()
}
