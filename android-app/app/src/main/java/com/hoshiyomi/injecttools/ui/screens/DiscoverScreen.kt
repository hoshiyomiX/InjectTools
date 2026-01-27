package com.hoshiyomi.injecttools.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hoshiyomi.injecttools.ui.viewmodels.DiscoverViewModel
import com.hoshiyomi.injecttools.ui.viewmodels.DiscoverUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    onNavigateBack: () -> Unit,
    viewModel: DiscoverViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val subdomains by viewModel.subdomains.collectAsState()
    
    var domain by remember { mutableStateOf("") }
    var limit by remember { mutableStateOf("100") }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Discover Subdomains") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Instructions
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Subdomain Discovery",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Find subdomains using crt.sh certificate transparency logs",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            
            // Domain input
            OutlinedTextField(
                value = domain,
                onValueChange = { domain = it },
                label = { Text("Domain") },
                placeholder = { Text("example.com") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            // Limit input
            OutlinedTextField(
                value = limit,
                onValueChange = { limit = it.filter { c -> c.isDigit() } },
                label = { Text("Results Limit") },
                placeholder = { Text("100") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            // Discover button
            Button(
                onClick = {
                    viewModel.discoverSubdomains(domain, limit.toIntOrNull() ?: 100)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState !is DiscoverUiState.Discovering
            ) {
                Icon(Icons.Default.Search, null)
                Spacer(Modifier.width(8.dp))
                Text("Discover Subdomains")
            }
            
            // Status messages
            when (val state = uiState) {
                is DiscoverUiState.Discovering -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Text("Discovering subdomains...")
                        }
                    }
                }
                is DiscoverUiState.Success -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Text(
                            text = "✅ Found ${state.count} subdomain(s)!",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                is DiscoverUiState.Error -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "❌ Discovery Error",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
                else -> {}
            }
            
            // Results list
            if (subdomains.isNotEmpty()) {
                Text(
                    text = "Discovered Subdomains (${subdomains.size})",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(subdomains) { subdomain ->
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = subdomain,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}
