package com.hoshiyomi.injecttools.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hoshiyomi.injecttools.ui.viewmodel.DiscoverState
import com.hoshiyomi.injecttools.ui.viewmodel.DiscoverViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    onNavigateBack: () -> Unit,
    viewModel: DiscoverViewModel = viewModel()
) {
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
        ) {
            // Input Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Info Card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Info, null)
                        Text(
                            "Discover subdomains using crt.sh certificate transparency logs",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                
                // Domain Input
                OutlinedTextField(
                    value = viewModel.domain,
                    onValueChange = { viewModel.domain = it },
                    label = { Text("Domain") },
                    placeholder = { Text("cloudflare.net") },
                    leadingIcon = { Icon(Icons.Default.Star, null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                // Limit Slider
                Column {
                    Text(
                        "Results Limit: ${viewModel.limit}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = viewModel.limit.toFloat(),
                        onValueChange = { viewModel.limit = it.toInt() },
                        valueRange = 10f..500f,
                        steps = 48
                    )
                }
                
                // Discover Button
                Button(
                    onClick = { viewModel.discover() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = viewModel.state !is DiscoverState.Discovering
                ) {
                    Icon(Icons.Default.Search, null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (viewModel.state is DiscoverState.Discovering) "Discovering..."
                        else "Start Discovery"
                    )
                }
            }
            
            Divider()
            
            // Results Section
            when (val currentState = viewModel.state) {
                is DiscoverState.Discovering -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(
                                "Querying crt.sh...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                is DiscoverState.Success -> {
                    SubdomainList(
                        subdomains = currentState.subdomains,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                is DiscoverState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
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
                }
                else -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Enter a domain to start discovery",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SubdomainList(
    subdomains: List<String>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item {
            Text(
                "Found ${subdomains.size} subdomains",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
        }
        
        items(subdomains) { subdomain ->
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        subdomain,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
