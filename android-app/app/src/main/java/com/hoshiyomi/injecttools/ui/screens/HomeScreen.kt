package com.hoshiyomi.injecttools.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hoshiyomi.injecttools.core.InjectToolsNative
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    var target by remember { mutableStateOf("www.bca.co.id") }
    var isCheckingTarget by remember { mutableStateOf(false) }
    var targetStatus by remember { mutableStateOf<TargetStatus>(TargetStatus.Unknown) }
    
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("InjectTools") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Target Input Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Target Configuration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    OutlinedTextField(
                        value = target,
                        onValueChange = { target = it },
                        label = { Text("Target Domain") },
                        placeholder = { Text("www.example.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Language, null) }
                    )
                    
                    Button(
                        onClick = {
                            scope.launch {
                                isCheckingTarget = true
                                targetStatus = checkTarget(target)
                                isCheckingTarget = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isCheckingTarget && target.isNotBlank()
                    ) {
                        if (isCheckingTarget) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (isCheckingTarget) "Checking..." else "Check Target")
                    }
                }
            }
            
            // Target Status Card
            if (targetStatus != TargetStatus.Unknown) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = when (targetStatus) {
                            TargetStatus.Online -> MaterialTheme.colorScheme.primaryContainer
                            TargetStatus.Offline -> MaterialTheme.colorScheme.errorContainer
                            TargetStatus.Unknown -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when (targetStatus) {
                                TargetStatus.Online -> Icons.Default.CheckCircle
                                TargetStatus.Offline -> Icons.Default.Cancel
                                TargetStatus.Unknown -> Icons.Default.Help
                            },
                            contentDescription = null,
                            tint = when (targetStatus) {
                                TargetStatus.Online -> MaterialTheme.colorScheme.primary
                                TargetStatus.Offline -> MaterialTheme.colorScheme.error
                                TargetStatus.Unknown -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(32.dp)
                        )
                        
                        Column {
                            Text(
                                text = "Target Status",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                text = targetStatus.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            
            // Quick Actions
            Text(
                text = "Quick Actions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            ActionCard(
                title = "Test Subdomain",
                description = "Test individual Cloudflare subdomain",
                icon = Icons.Default.BugReport,
                onClick = { /* TODO: Navigate to scan screen */ }
            )
            
            ActionCard(
                title = "Discover Subdomains",
                description = "Auto-discover from crt.sh & test all",
                icon = Icons.Default.Search,
                onClick = { /* TODO: Navigate to discover screen */ }
            )
            
            ActionCard(
                title = "View Results",
                description = "Browse previous scan results",
                icon = Icons.Default.History,
                onClick = { /* TODO: Navigate to results screen */ }
            )
        }
    }
}

@Composable
fun ActionCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

sealed class TargetStatus(val displayName: String) {
    object Online : TargetStatus("ONLINE")
    object Offline : TargetStatus("OFFLINE")
    object Unknown : TargetStatus("UNKNOWN")
}

suspend fun checkTarget(target: String): TargetStatus = withContext(Dispatchers.IO) {
    try {
        val isOnline = InjectToolsNative.checkTargetOnline(target)
        if (isOnline) TargetStatus.Online else TargetStatus.Offline
    } catch (e: Exception) {
        TargetStatus.Unknown
    }
}
