package com.hoshiyomi.injecttools.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hoshiyomi.injecttools.core.InjectToolsKotlin
import com.hoshiyomi.injecttools.ui.viewmodels.ScanViewModel
import com.hoshiyomi.injecttools.ui.viewmodels.ScanUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    onNavigateBack: () -> Unit,
    viewModel: ScanViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val results by viewModel.results.collectAsState()
    
    var target by remember { mutableStateOf("") }
    var subdomainList by remember { mutableStateOf("") }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan Subdomain") },
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
            // Target input
            OutlinedTextField(
                value = target,
                onValueChange = { target = it },
                label = { Text("Target Domain") },
                placeholder = { Text("example.com") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            // Subdomain list input
            OutlinedTextField(
                value = subdomainList,
                onValueChange = { subdomainList = it },
                label = { Text("Subdomains (one per line)") },
                placeholder = { Text("sub1.example.com\nsub2.example.com\nsub3.example.com") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                maxLines = 15
            )
            
            // Scan button
            Button(
                onClick = {
                    val subs = subdomainList.lines()
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                    viewModel.startScan(target, subs)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState !is ScanUiState.Scanning
            ) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text("Start Scan")
            }
            
            // Status messages
            when (val state = uiState) {
                is ScanUiState.Scanning -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Text("Scanning in progress...")
                        }
                    }
                }
                is ScanUiState.Success -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Text(
                            text = "✅ Found ${state.workingCount} working subdomain(s)!",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                is ScanUiState.NoResults -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "⚠️ No working subdomains found",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = state.reason,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                is ScanUiState.Error -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "❌ Scan Error",
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
            if (results.isNotEmpty()) {
                Text(
                    text = "Results (${results.size})",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(results) { result ->
                        ResultCard(result)
                    }
                }
            }
        }
    }
}

@Composable
fun ResultCard(result: InjectToolsKotlin.ScanResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (result.isWorking) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (result.isWorking) "✅ WORKING" else "❌ NOT WORKING",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (result.isWorking)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Text(
                text = result.subdomain,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            if (result.ip.isNotEmpty()) {
                Text(
                    text = "IP: ${result.ip}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            if (result.statusCode != null) {
                Text(
                    text = "HTTP: ${result.statusCode}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            if (result.cfRay != null) {
                Text(
                    text = "CF-Ray: ${result.cfRay}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            if (result.errorMsg != null) {
                Text(
                    text = "Error: ${result.errorMsg}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
