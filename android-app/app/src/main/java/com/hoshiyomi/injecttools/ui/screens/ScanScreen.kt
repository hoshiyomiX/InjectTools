package com.hoshiyomi.injecttools.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hoshiyomi.injecttools.viewmodel.ScanViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    onNavigateBack: () -> Unit,
    viewModel: ScanViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scanResults by viewModel.scanResults.collectAsState()
    val progress by viewModel.progress.collectAsState()
    
    var target by remember { mutableStateOf("") }
    var subdomain by remember { mutableStateOf("") }
    var subdomainList by remember { mutableStateOf("") }
    var isBatchMode by remember { mutableStateOf(false) }
    
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Target input
            OutlinedTextField(
                value = target,
                onValueChange = { target = it },
                label = { Text("Target Domain") },
                placeholder = { Text("example.com") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Mode switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Batch Mode")
                Switch(
                    checked = isBatchMode,
                    onCheckedChanged = { isBatchMode = it }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Input based on mode
            if (isBatchMode) {
                OutlinedTextField(
                    value = subdomainList,
                    onValueChange = { subdomainList = it },
                    label = { Text("Subdomains (one per line)") },
                    placeholder = { Text("sub1.example.com\nsub2.example.com") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    maxLines = 10
                )
            } else {
                OutlinedTextField(
                    value = subdomain,
                    onValueChange = { subdomain = it },
                    label = { Text("Subdomain") },
                    placeholder = { Text("sub.example.com") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Scan button
            Button(
                onClick = {
                    if (isBatchMode) {
                        val subs = subdomainList.lines().filter { it.isNotBlank() }
                        viewModel.testBatchSubdomains(target, subs)
                    } else {
                        viewModel.testSingleSubdomain(target, subdomain)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState !is ScanViewModel.ScanUiState.Scanning
            ) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text(if (isBatchMode) "Scan Batch" else "Scan Single")
            }
            
            // Progress indicator
            if (uiState is ScanViewModel.ScanUiState.Scanning && isBatchMode) {
                Spacer(modifier = Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Progress: ${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            // Results display
            if (scanResults.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Results",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                scanResults.forEach { result ->
                    ResultCard(result)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            
            // Error display
            when (val state = uiState) {
                is ScanViewModel.ScanUiState.Error -> {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = state.message,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                else -> {}
            }
        }
    }
}

@Composable
fun ResultCard(result: com.hoshiyomi.injecttools.core.InjectToolsKotlin.ScanResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (result.isWorking) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = result.subdomain,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "IP: ${result.ip}",
                style = MaterialTheme.typography.bodySmall
            )
            if (result.statusCode != null) {
                Text(
                    text = "Status: ${result.statusCode}",
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
