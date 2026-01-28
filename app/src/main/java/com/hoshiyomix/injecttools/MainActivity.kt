package com.hoshiyomix.injecttools

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.hoshiyomix.injecttools.Scanner.ScanResult

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme() // Force dark theme to match CLI vibe
            ) {
                MainApp(onExit = { finish() })
            }
        }
    }
}

enum class Screen {
    MENU, SINGLE_TEST, CRTSH_TEST, RESULTS, SETTINGS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(onExit: () -> Unit) {
    var currentScreen by remember { mutableStateOf(Screen.MENU) }
    var targetHost by remember { mutableStateOf("") } // Empty by default to force user to set it
    var scanHistory by remember { mutableStateOf(listOf<ScanResult>()) }
    val context = LocalContext.current

    // Helper to add results to history
    fun addResults(newResults: List<ScanResult>) {
        scanHistory = newResults + scanHistory
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = when (currentScreen) {
                            Screen.MENU -> "InjectTools v3.6.0"
                            Screen.SINGLE_TEST -> "Single Subdomain"
                            Screen.CRTSH_TEST -> "Crt.sh Discovery"
                            Screen.RESULTS -> "Scan Results"
                            Screen.SETTINGS -> "Settings"
                        }
                    )
                },
                navigationIcon = {
                    if (currentScreen != Screen.MENU) {
                        IconButton(onClick = { currentScreen = Screen.MENU }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (currentScreen == Screen.MENU) {
                        IconButton(onClick = { currentScreen = Screen.SETTINGS }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (currentScreen) {
                Screen.MENU -> MenuScreen(
                    targetHost = targetHost,
                    onNavigate = { screen ->
                        if ((screen == Screen.SINGLE_TEST || screen == Screen.CRTSH_TEST) && targetHost.isBlank()) {
                            Toast.makeText(context, "⚠️ Set target host dulu (Menu 4)!", Toast.LENGTH_SHORT).show()
                        } else {
                            currentScreen = screen
                        }
                    },
                    onExit = onExit
                )
                Screen.SINGLE_TEST -> ManualScanScreen(targetHost, onResult = { addResults(listOf(it)) })
                Screen.CRTSH_TEST -> CrtshScanScreen(targetHost, onResults = { addResults(it) })
                Screen.RESULTS -> ResultHistoryScreen(scanHistory)
                Screen.SETTINGS -> SettingsScreen(targetHost, onSave = { targetHost = it; currentScreen = Screen.MENU })
            }
        }
    }

    BackHandler(enabled = currentScreen != Screen.MENU) {
        currentScreen = Screen.MENU
    }
}

@Composable
fun MenuScreen(
    targetHost: String,
    onNavigate: (Screen) -> Unit,
    onExit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Target Status Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Target Status", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Host: ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (targetHost.isNotBlank()) {
                        Text(targetHost, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    } else {
                        Text("Not Set", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        // Menu Buttons
        MenuButton("1. 🔍 Test Single Subdomain") { onNavigate(Screen.SINGLE_TEST) }
        Spacer(modifier = Modifier.height(12.dp))
        MenuButton("2. 🌐 Fetch & Test dari crt.sh") { onNavigate(Screen.CRTSH_TEST) }
        Spacer(modifier = Modifier.height(12.dp))
        MenuButton("3. 📊 View Exported Results") { onNavigate(Screen.RESULTS) }
        Spacer(modifier = Modifier.height(12.dp))
        MenuButton("4. ⚙️  Change Target Host") { onNavigate(Screen.SETTINGS) }
        Spacer(modifier = Modifier.height(12.dp))
        MenuButton("5. 🚺 Exit", isDestructive = true) { onExit() }
    }
}

@Composable
fun MenuButton(text: String, isDestructive: Boolean = false, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = if (isDestructive) 
            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) 
        else 
            ButtonDefaults.buttonColors()
    ) {
        Text(text, fontSize = 16.sp, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun SettingsScreen(currentHost: String, onSave: (String) -> Unit) {
    var hostInput by remember { mutableStateOf(currentHost) }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Konfigurasi Target", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Target Host digunakan sebagai SNI (Server Name Indication) saat melakukan handshake SSL ke IP subdomain.", style = MaterialTheme.typography.bodyMedium)
        
        Spacer(modifier = Modifier.height(24.dp))
        
        OutlinedTextField(
            value = hostInput,
            onValueChange = { hostInput = it },
            label = { Text("Target Host (e.g. tunnel.example.com)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = { onSave(hostInput.trim()) },
            modifier = Modifier.fillMaxWidth(),
            enabled = hostInput.isNotBlank()
        ) {
            Text("Simpan Konfigurasi")
        }
    }
}

@Composable
fun ResultHistoryScreen(history: List<ScanResult>) {
    if (history.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Belum ada hasil scan.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            item {
                Text("Riwayat Scan (Sesi Ini)", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
            }
            items(history) { res ->
                ResultItem(res)
            }
        }
    }
}

@Composable
fun ManualScanScreen(targetHost: String, onResult: (ScanResult) -> Unit) {
    var subdomain by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf<ScanResult?>(null) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.padding(16.dp)) {
        OutlinedTextField(
            value = subdomain,
            onValueChange = { subdomain = it },
            label = { Text("Subdomain to Test") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = {
                if (isScanning || subdomain.isBlank()) return@Button
                isScanning = true
                scope.launch {
                    val res = Scanner.testSingle(targetHost, subdomain.trim())
                    lastResult = res
                    onResult(res)
                    isScanning = false
                }
            },
            enabled = !isScanning && subdomain.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isScanning) "Scanning..." else "Test Connection")
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        lastResult?.let {
            Text("Last Result:", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(8.dp))
            ResultItem(it)
        }
    }
}

@Composable
fun CrtshScanScreen(targetHost: String, onResults: (List<ScanResult>) -> Unit) {
    var domain by remember { mutableStateOf("") }
    var subdomains by remember { mutableStateOf(listOf<String>()) }
    var scanResults by remember { mutableStateOf(listOf<ScanResult>()) }
    var isFetching by remember { mutableStateOf(false) }
    var isScanning by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.padding(16.dp)) {
        OutlinedTextField(
            value = domain,
            onValueChange = { domain = it },
            label = { Text("Domain (e.g. cloudflare.com)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    isFetching = true
                    scanResults = emptyList()
                    scope.launch {
                        try {
                            subdomains = Crtsh.fetchSubdomains(domain.trim())
                        } catch (e: Exception) {
                            subdomains = emptyList()
                        }
                        isFetching = false
                    }
                },
                enabled = !isFetching && !isScanning && domain.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isFetching) "Fetching..." else "1. Fetch List")
            }

            Button(
                onClick = {
                    isScanning = true
                    scanResults = emptyList()
                    scope.launch {
                        val tempResults = mutableListOf<ScanResult>()
                        val total = subdomains.size
                        subdomains.forEachIndexed { index, sub ->
                            val res = Scanner.testSingle(targetHost, sub)
                            tempResults.add(res)
                            scanResults = tempResults.toList() // Trigger recomposition
                            progress = (index + 1) / total.toFloat()
                        }
                        onResults(tempResults)
                        isScanning = false
                    }
                },
                enabled = !isScanning && subdomains.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isScanning) "Stop" else "2. Scan All")
            }
        }
        
        if (isScanning) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = progress, // FIX: remove lambda braces
                modifier = Modifier.fillMaxWidth(),
            )
            Text("${(progress * 100).toInt()}%", modifier = Modifier.align(Alignment.End))
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        LazyColumn {
            items(scanResults) { res ->
                ResultItem(res)
            }
        }
    }
}

@Composable
fun ResultItem(res: ScanResult) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (res.isWorking) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (res.isWorking) "✅ WORKING" else "❌ FAILED",
                    color = if (res.isWorking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(modifier = Modifier.weight(1f))
                if (res.isCloudflare) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text("Cloudflare") },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    )
                }
            }
            
            Text(text = res.subdomain, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(text = res.ip, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            
            if (res.errorMsg != null) {
                Text(text = "Error: ${res.errorMsg}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
