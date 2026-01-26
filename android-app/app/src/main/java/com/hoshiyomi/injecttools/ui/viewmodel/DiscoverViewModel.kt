package com.hoshiyomi.injecttools.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoshiyomi.injecttools.core.InjectToolsNative
import kotlinx.coroutines.launch

sealed class DiscoverState {
    object Idle : DiscoverState()
    object Discovering : DiscoverState()
    data class Success(val subdomains: List<String>) : DiscoverState()
    data class Error(val message: String) : DiscoverState()
}

class DiscoverViewModel : ViewModel() {
    var state by mutableStateOf<DiscoverState>(DiscoverState.Idle)
        private set
    
    var domain by mutableStateOf("")
    var limit by mutableStateOf(100)
    
    fun discover() {
        if (domain.isBlank()) {
            state = DiscoverState.Error("Domain is required")
            return
        }
        
        state = DiscoverState.Discovering
        
        viewModelScope.launch {
            try {
                val subdomains = InjectToolsNative.discover(
                    domain = domain,
                    limit = limit
                )
                
                if (subdomains.isEmpty()) {
                    state = DiscoverState.Error("No subdomains found")
                } else {
                    state = DiscoverState.Success(subdomains)
                }
            } catch (e: Exception) {
                state = DiscoverState.Error(e.message ?: "Discovery failed")
            }
        }
    }
    
    fun reset() {
        state = DiscoverState.Idle
    }
}
