package com.hoshiyomi.injecttools.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoshiyomi.injecttools.core.InjectToolsKotlin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DiscoverViewModel : ViewModel() {
    
    private val _uiState = MutableStateFlow<DiscoverUiState>(DiscoverUiState.Idle)
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()
    
    private val _subdomains = MutableStateFlow<List<String>>(emptyList())
    val subdomains: StateFlow<List<String>> = _subdomains.asStateFlow()
    
    sealed class DiscoverUiState {
        object Idle : DiscoverUiState()
        object Discovering : DiscoverUiState()
        data class Success(val count: Int) : DiscoverUiState()
        data class Error(val message: String) : DiscoverUiState()
    }
    
    fun discoverSubdomains(domain: String, limit: Int = 100) {
        viewModelScope.launch {
            try {
                _uiState.value = DiscoverUiState.Discovering
                
                // Validate input
                if (domain.isBlank()) {
                    _uiState.value = DiscoverUiState.Error("Domain cannot be empty")
                    return@launch
                }
                
                // Remove protocol if present
                val cleanDomain = domain
                    .removePrefix("https://")
                    .removePrefix("http://")
                    .split("/")[0]
                
                // Discover via crt.sh
                val discovered = InjectToolsKotlin.discoverSubdomains(cleanDomain, limit)
                
                if (discovered.isEmpty()) {
                    _uiState.value = DiscoverUiState.Error("No subdomains found")
                } else {
                    _subdomains.value = discovered
                    _uiState.value = DiscoverUiState.Success(discovered.size)
                }
                
            } catch (e: Exception) {
                _uiState.value = DiscoverUiState.Error(e.message ?: "Discovery failed")
            }
        }
    }
    
    fun reset() {
        _uiState.value = DiscoverUiState.Idle
        _subdomains.value = emptyList()
    }
    
    fun getSubdomainsList(): List<String> = _subdomains.value
}
