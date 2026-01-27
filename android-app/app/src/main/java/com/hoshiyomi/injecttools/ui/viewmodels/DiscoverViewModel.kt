package com.hoshiyomi.injecttools.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoshiyomi.injecttools.core.InjectToolsKotlin
import com.hoshiyomi.injecttools.core.LogManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DiscoverViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<DiscoverUiState>(DiscoverUiState.Idle)
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

    private val _subdomains = MutableStateFlow<List<String>>(emptyList())
    val subdomains: StateFlow<List<String>> = _subdomains.asStateFlow()

    fun discoverSubdomains(domain: String, limit: Int = 100) {
        if (domain.isBlank()) {
            _uiState.value = DiscoverUiState.Error("Domain tidak boleh kosong")
            return
        }

        viewModelScope.launch {
            try {
                _uiState.value = DiscoverUiState.Discovering
                _subdomains.value = emptyList()
                
                LogManager.i("DiscoverViewModel", "Discovering subdomains for: $domain")
                
                val discovered = InjectToolsKotlin.discoverSubdomains(domain, limit)
                
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
