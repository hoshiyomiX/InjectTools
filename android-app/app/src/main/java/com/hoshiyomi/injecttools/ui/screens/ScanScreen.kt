package com.hoshiyomi.injecttools.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hoshiyomi.injecttools.core.ScanResult
import com.hoshiyomi.injecttools.ui.viewmodel.ScanState
import com.hoshiyomi.injecttools.ui.viewmodel.ScanViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    onNavigateBack: () -> Unit,
    viewModel: ScanViewModel = viewModel()
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan Subdomains") },
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
        ) {
            // Input Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .weight(1f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Mode Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Batch Mode", style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = viewModel.isBatchMode,
                        onCheckedChange = { 
                            viewModel.isBatchMode = it
                            viewModel.reset()
                        }
                    )
                }
                
                // Target Input
                OutlinedTextField(
                    value = viewModel.target,
                    onValueChange = { 
                        viewModel.target = it
                        viewModel.targetOnline = null
                    },
                    label = { Text("Target Host") },
                    placeholder = { Text("example.com") },
                    leadingIcon = { Icon(Icons.Default.Star, null) },
                    trailingIcon = {
                        if (viewModel.target.isNotBlank()) {
                            IconButton(onClick = { viewModel.checkTarget() }) {
                                Icon(Icons.Default.Refresh, "Check")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                // Target Status
                viewModel.targetOnline?.let { online ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (online) 
                                MaterialTheme.colorScheme.tertiaryContainer
                            else
                                MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (online) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null
                            )
                            Text(
                                if (online) "Target is online" else "Target unreachable",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                
                if (!viewModel.isBatchMode) {
                    // Single Subdomain
                    OutlinedTextField(
                        value = viewModel.subdomain,
                        onValueChange = { viewModel.subdomain = it },
                        label = { Text("Subdomain") },
                        placeholder = { Text("sub.cloudflare.net") },
                        leadingIcon = { Icon(Icons.Default.LocationOn, null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                } else {
                    // Batch Subdomains
                    OutlinedTextField(
                        value = viewModel.subdomainList,
                        onValueChange = { viewModel.subdomainList = it },
                        label = { Text("Subdomains (one per line)") },
                        placeholder = { Text("sub1.cloudflare.net\nsub2.cloudflare.net") },
                        leadingIcon = { Icon(Icons.Default.List, null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        maxLines = 10
                    )
                }
                
                // Timeout
                OutlinedTextField(
                    value = viewModel.timeout.toString(),
                    onValueChange = { 
                        viewModel.timeout = it.toIntOrNull() ?: 10
                    },
                    label = { Text("Timeout (seconds)") },
                    leadingIcon = { Icon(Icons.Default.DateRange, null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                // Scan Button
                Button(
                    onClick = {
                        if (viewModel.isBatchMode) viewModel.scanBatch()
                        else viewModel.scanSingle()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = viewModel.state !is ScanState.Scanning
                ) {
                    Icon(Icons.Default.Search, null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (viewModel.state is ScanState.Scanning) "Scanning..."
                        else "Start Scan"
                    )
                }
            }
            
            // Results Section
            when (val currentState = viewModel.state) {
                is ScanState.Scanning -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.5f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is ScanState.Success -> {
                    ResultsList(
                        results = currentState.results,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
                is ScanState.Error -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.Warning, null)
                            Text(currentState.message)
                        }
                    }
                }
                else -> {}
            }
        }
    }
}

@Composable
fun ResultsList(
    results: List<ScanResult>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                "Results (${results.count { it.isWorking }} working)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
        }
        
        items(results) { result ->
            ResultCard(result)
        }
    }
}

@Composable
fun ResultCard(result: ScanResult) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Subdomain
            Text(
                result.subdomain,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            
            // IP
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    result.ip.ifEmpty { "N/A" },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
            
            // Status Badge
            AssistChip(
                onClick = {},
                label = { Text(result.displayStatus) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = when {
                        result.isWorking -> MaterialTheme.colorScheme.tertiaryContainer
                        result.errorMsg != null -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            )
            
            // Additional Info
            result.statusCode?.let {
                Text(
                    "HTTP $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
