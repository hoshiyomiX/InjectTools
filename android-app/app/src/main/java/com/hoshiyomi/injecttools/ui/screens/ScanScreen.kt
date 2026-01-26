package com.hoshiyomi.injecttools.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hoshiyomi.injecttools.core.InjectToolsKotlin
import com.hoshiyomi.injecttools.viewmodel.ScanViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    viewModel: ScanViewModel = viewModel()
) {
    var target by remember { mutableStateOf("www.cloudflare.com") }
    var subdomain by remember { mutableStateOf("") }
    var subdomainList by remember { mutableStateOf("") }
    var scanMode by remember { mutableStateOf("single") } // "single" or "batch"
    
    val uiState by viewModel.uiState.collectAsState()
    val scanResults by viewModel.scanResults.collectAsState()
    val progress by viewModel.progress.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan Cloudflare Bug") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Target Input
            OutlinedTextField(
                value = target,
                onValueChange = { target = it },
                label = { Text("Target Domain") },
                placeholder = { Text("e.g., www.cloudflare.com") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            // Mode Selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = scanMode == "single",
                    onClick = { scanMode = "single" },
                    label = { Text("Single Scan") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = scanMode == "batch",
                    onClick = { scanMode = "batch" },
                    label = { Text("Batch Scan") },
                    modifier = Modifier.weight(1f)
                )
            }
            
            // Input based on mode
            if (scanMode == "single") {
                OutlinedTextField(
                    value = subdomain,
                    onValueChange = { subdomain = it },
                    label = { Text("Subdomain") },
                    placeholder = { Text("e.g., blog.cloudflare.com") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            } else {
                OutlinedTextField(
                    value = subdomainList,
                    onValueChange = { subdomainList = it },
                    label = { Text("Subdomain List (one per line)") },
                    placeholder = { Text("blog.cloudflare.com\napi.cloudflare.com\n...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    maxLines = 10
                )
            }
            
            // Scan Button
            Button(
                onClick = {
                    if (scanMode == "single") {
                        viewModel.testSingleSubdomain(target, subdomain)
                    } else {
                        val subdomains = subdomainList.lines()
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                        viewModel.testBatchSubdomains(target, subdomains)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState !is ScanViewModel.ScanUiState.Scanning
            ) {
                Text(if (uiState is ScanViewModel.ScanUiState.Scanning) "Scanning..." else "Start Scan")
            }
            
            // Progress Bar (for batch)
            if (uiState is ScanViewModel.ScanUiState.Scanning && scanMode == "batch") {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "Progress: ${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            // Results
            if (scanResults.isNotEmpty()) {
                Text(
                    text = "Results (${scanResults.count { it.isWorking }} working)",
                    style = MaterialTheme.typography.titleMedium
                )
                
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(scanResults) { result ->
                        ScanResultCard(result)
                    }
                }
            }
        }
    }
}

@Composable
fun ScanResultCard(result: InjectToolsKotlin.ScanResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (result.isWorking) 
                Color(0xFF1B5E20).copy(alpha = 0.1f)
            else 
                Color(0xFFB71C1C).copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.subdomain,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = result.ip,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                if (result.errorMsg != null) {
                    Text(
                        text = result.errorMsg,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Red
                    )
                }
                if (result.statusCode != null) {
                    Text(
                        text = "HTTP ${result.statusCode}${if (result.cfRay != null) " ✓ CF" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (result.isWorking) Color(0xFF2E7D32) else Color(0xFFC62828)
                    )
                }
            }
            
            Icon(
                imageVector = if (result.isWorking) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = if (result.isWorking) "Working" else "Not Working",
                tint = if (result.isWorking) Color(0xFF2E7D32) else Color(0xFFC62828),
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
