package com.hoshiyomix.injecttools

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    var selectedTab by remember { mutableIntStateOf(0) }
    
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("Manual") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Search, contentDescription = null) },
                    label = { Text("Crt.sh") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> ManualScanScreen()
                1 -> CrtshScanScreen()
            }
        }
    }
}

@Composable
fun ManualScanScreen() {
    var target by remember { mutableStateOf("google.com") }
    var subdomainsInput by remember { mutableStateOf("dl.google.com\nmaps.google.com") }
    var results by remember { mutableStateOf(listOf<Scanner.ScanResult>()) }
    var isScanning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Menu 1: Manual Scan", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = target,
            onValueChange = { target = it },
            label = { Text("Target (SNI)") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedTextField(
            value = subdomainsInput,
            onValueChange = { subdomainsInput = it },
            label = { Text("Subdomains (one per line)") },
            modifier = Modifier.fillMaxWidth().height(120.dp),
            maxLines = 10
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = {
                if (isScanning) return@Button
                isScanning = true
                results = emptyList()
                scope.launch {
                    val subs = subdomainsInput.split("\n").filter { it.isNotBlank() }
                    val newResults = mutableListOf<Scanner.ScanResult>()
                    for (sub in subs) {
                        val res = Scanner.testSingle(target, sub.trim())
                        newResults.add(res)
                        results = newResults.toList()
                    }
                    isScanning = false
                }
            },
            enabled = !isScanning,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isScanning) "Scanning..." else "Start Scan")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        ResultList(results)
    }
}

@Composable
fun CrtshScanScreen() {
    var target by remember { mutableStateOf("google.com") }
    var domain by remember { mutableStateOf("cloudflare.com") }
    var results by remember { mutableStateOf(listOf<Scanner.ScanResult>()) }
    var subdomains by remember { mutableStateOf(listOf<String>()) }
    var isFetching by remember { mutableStateOf(false) }
    var isScanning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Menu 2: Crt.sh Discovery", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = target,
            onValueChange = { target = it },
            label = { Text("Target (SNI)") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedTextField(
            value = domain,
            onValueChange = { domain = it },
            label = { Text("Domain (e.g. cloudflare.com)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    isFetching = true
                    results = emptyList()
                    subdomains = emptyList()
                    scope.launch {
                        subdomains = Crtsh.fetchSubdomains(domain)
                        isFetching = false
                    }
                },
                enabled = !isFetching && !isScanning,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isFetching) "Fetching..." else "Fetch Subs")
            }

            Button(
                onClick = {
                    isScanning = true
                    results = emptyList()
                    scope.launch {
                        val newResults = mutableListOf<Scanner.ScanResult>()
                        for (sub in subdomains) {
                            val res = Scanner.testSingle(target, sub)
                            newResults.add(res)
                            results = newResults.toList()
                        }
                        isScanning = false
                    }
                },
                enabled = !isScanning && subdomains.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isScanning) "Scanning..." else "Scan All (${subdomains.size})")
            }
        }
        
        if (subdomains.isNotEmpty()) {
            Text(
                text = "Found ${subdomains.size} subdomains", 
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        ResultList(results)
    }
}

@Composable
fun ResultList(results: List<Scanner.ScanResult>) {
    LazyColumn {
        items(results) { res ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (res.isWorking) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(text = res.subdomain, style = MaterialTheme.typography.titleMedium)
                    Row {
                        Text(text = res.ip, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        if(res.isCloudflare) {
                            Text("[CF]", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (res.errorMsg != null) {
                        Text(text = res.errorMsg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    } else {
                        Text(text = "SSL Handshake: OK", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
