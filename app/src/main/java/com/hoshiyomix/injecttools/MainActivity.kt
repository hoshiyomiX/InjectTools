package com.hoshiyomix.injecttools

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
                ScannerScreen()
            }
        }
    }
}

@Composable
fun ScannerScreen() {
    var target by remember { mutableStateOf("google.com") }
    var subdomainsInput by remember { mutableStateOf("dl.google.com\nmaps.google.com") }
    var results by remember { mutableStateOf(listOf<Scanner.ScanResult>()) }
    var isScanning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.padding(16.dp)) {
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
            modifier = Modifier.fillMaxWidth().height(150.dp),
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
                        results = newResults.toList() // Update UI incrementally
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
        
        LazyColumn {
            items(results) { res ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (res.isWorking) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(text = "Sub: ${res.subdomain}", style = MaterialTheme.typography.bodyLarge)
                        Text(text = "IP: ${res.ip} ${if(res.isCloudflare) "[CF]" else ""}", style = MaterialTheme.typography.bodyMedium)
                        if (res.errorMsg != null) {
                            Text(text = "Error: ${res.errorMsg}", style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text(text = "Status: ${if (res.isWorking) "WORKING" else "FAILED"}", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}
