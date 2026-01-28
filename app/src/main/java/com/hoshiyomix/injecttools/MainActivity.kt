package com.hoshiyomix.injecttools

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hoshiyomix.injecttools.network.CrtShClient
import com.hoshiyomix.injecttools.utils.ScanEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var targetHost by remember { mutableStateOf("") }
    var singleSubdomain by remember { mutableStateOf("") }
    var domainInput by remember { mutableStateOf("") }
    var logs by remember { mutableStateOf("Ready to scan...\n") }
    var isScanning by remember { mutableStateOf(false) }
    var targetStatus by remember { mutableStateOf("Unknown") }

    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    fun appendLog(msg: String) {
        logs += "$msg\n"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        Text("InjectTools Android", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        // Target Host Section
        OutlinedTextField(
            value = targetHost,
            onValueChange = { targetHost = it },
            label = { Text("Target Host (e.g. tunnel.com)") },
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        isScanning = true
                        appendLog("Checking target: $targetHost...")
                        val isUp = ScanEngine.checkTarget(targetHost, 10)
                        targetStatus = if (isUp) "ONLINE" else "OFFLINE"
                        appendLog("Target Status: $targetStatus")
                        isScanning = false
                    }
                },
                enabled = !isScanning && targetHost.isNotEmpty(),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("Check Target Status")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = targetStatus, color = if (targetStatus == "ONLINE") Color.Green else Color.Red)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Divider()
        Spacer(modifier = Modifier.height(16.dp))

        // Single Scan Section
        Text("Single Subdomain Test", fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = singleSubdomain,
            onValueChange = { singleSubdomain = it },
            label = { Text("Subdomain (e.g. cdn.cloudflare.com)") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                scope.launch(Dispatchers.IO) {
                    isScanning = true
                    appendLog("Testing: $singleSubdomain -> $targetHost")
                    val working = ScanEngine.testSubdomain(targetHost, singleSubdomain, 10)
                    appendLog("Result: ${if (working) "WORKING ✅" else "DEAD ❌"}")
                    isScanning = false
                }
            },
            enabled = !isScanning && targetHost.isNotEmpty() && singleSubdomain.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Text("Test Single Subdomain")
        }

        Spacer(modifier = Modifier.height(16.dp))
        Divider()
        Spacer(modifier = Modifier.height(16.dp))

        // Crt.sh Scan Section
        Text("Crt.sh Discovery Scan", fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = domainInput,
            onValueChange = { domainInput = it },
            label = { Text("Domain to search (e.g. cloudflare.com)") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                scope.launch(Dispatchers.IO) {
                    isScanning = true
                    appendLog("Fetching subdomains from crt.sh for $domainInput...")
                    try {
                        val results = CrtShClient.api.search("%.$domainInput")
                        val subdomains = results.map { it.name_value }
                            .flatMap { it.split("\n") }
                            .distinct()
                            .filter { !it.contains("*") }
                        
                        appendLog("Found ${subdomains.size} unique subdomains.")
                        
                        var workingCount = 0
                        subdomains.forEachIndexed { index, sub ->
                            if (index % 10 == 0) appendLog("Progress: $index/${subdomains.size}")
                            if (ScanEngine.testSubdomain(targetHost, sub, 5)) {
                                appendLog("✅ WORKING: $sub")
                                workingCount++
                            }
                        }
                        appendLog("Scan Complete. Found $workingCount working bugs.")
                    } catch (e: Exception) {
                        appendLog("Error: ${e.message}")
                    }
                    isScanning = false
                }
            },
            enabled = !isScanning && targetHost.isNotEmpty() && domainInput.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Text("Fetch & Scan from Crt.sh")
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        // Logs
        Text("Logs:", fontWeight = FontWeight.Bold)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(Color.Black)
                .padding(8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(text = logs, color = Color.Green, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        }
    }
}
