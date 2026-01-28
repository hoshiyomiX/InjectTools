package com.hoshiyomix.injecttools

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.hoshiyomix.injecttools.Scanner.ScanResult

// Global log buffer for verbose mode
object Logger {
    private val _logs = mutableStateListOf<String>()
    val logs: List<String> get() = _logs

    fun log(msg: String) {
        _logs.add(msg)
    }

    fun clear() {
        _logs.clear()
    }
}

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
    MENU, SINGLE_TEST, CRTSH_TEST, RESULTS, SETTINGS, VERBOSE_LOGS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(onExit: () -> Unit) {
    var currentScreen by remember { mutableStateOf(Screen.MENU) }
    var targetHost by remember { mutableStateOf("") } // Empty by default to force user to set it
    var scanHistory by remember { mutableStateOf(listOf<ScanResult>()) }
    var isVerbose by remember { mutableStateOf(false) } // Verbose Toggle State
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
                            Screen.MENU -> "InjectTools v3.6.1"
                            Screen.SINGLE_TEST -> "Single Subdomain"
                            Screen.CRTSH_TEST -> "Crt.sh Discovery"
                            Screen.RESULTS -> "Scan Results"
                            Screen.SETTINGS -> "Settings"
                            Screen.VERBOSE_LOGS -> "Verbose Logs"
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
                    // Verbose Log Icon Button
                    if (isVerbose) {
                         IconButton(onClick = { currentScreen = Screen.VERBOSE_LOGS }) {
                            Icon(Icons.Default.Info, contentDescription = "Logs", tint = MaterialTheme.colorScheme.primary)
                        }
                    }

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
                Screen.SINGLE_TEST -> ManualScanScreen(targetHost, isVerbose, onResult = { addResults(listOf(it)) })
                Screen.CRTSH_TEST -> CrtshScanScreen(targetHost, isVerbose, onResults = { addResults(it) })
                Screen.RESULTS -> ResultHistoryScreen(scanHistory)
                Screen.SETTINGS -> SettingsScreen(
                    currentHost = targetHost, 
                    isVerbose = isVerbose,
                    onSave = { host, verbose -> 
                        targetHost = host
                        isVerbose = verbose
                        currentScreen = Screen.MENU 
                    }
                )
                Screen.VERBOSE_LOGS -> VerboseLogScreen()
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
        MenuButton("4. ⚙️  Settings & Target Host") { onNavigate(Screen.SETTINGS) }
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
fun SettingsScreen(currentHost: String, isVerbose: Boolean, onSave: (String, Boolean) -> Unit) {
    var hostInput by remember { mutableStateOf(currentHost) }
    var verboseState by remember { mutableStateOf(isVerbose) }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Konfigurasi Target", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Target Host digunakan sebagai SNI saat handshake SSL.", style = MaterialTheme.typography.bodyMedium)
        
        Spacer(modifier = Modifier.height(24.dp))
        
        OutlinedTextField(
            value = hostInput,
            onValueChange = { hostInput = it },
            label = { Text("Target Host (e.g. tunnel.example.com)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Verbose Checkbox
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Checkbox(
                checked = verboseState,
                onCheckedChange = { verboseState = it }
            )
            Text("Enable Verbose Logging (Debug Panel)")
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = { onSave(hostInput.trim(), verboseState) },
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
fun ManualScanScreen(targetHost: String, isVerbose: Boolean, onResult: (ScanResult) -> Unit) {
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
                if (isVerbose) {
                    Logger.clear()
                    Logger.log("--- Starting Scan for ${subdomain.trim()} ---")
                }
                
                scope.launch {
                    val res = Scanner.testSingle(targetHost, subdomain.trim(), isVerbose)
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
fun CrtshScanScreen(targetHost: String, isVerbose: Boolean, onResults: (List<ScanResult>) -> Unit) {
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
                    if (isVerbose) Logger.log("Fetching subdomains for $domain from crt.sh...")
                    
                    scope.launch {
                        try {
                            subdomains = Crtsh.fetchSubdomains(domain.trim())
                            if (isVerbose) Logger.log("Found ${subdomains.size} subdomains")
                        } catch (e: Exception) {
                            subdomains = emptyList()
                            if (isVerbose) Logger.log("Error fetching: ${e.message}")
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
                    if (isVerbose) Logger.clear()
                    
                    scope.launch {
                        val tempResults = mutableListOf<ScanResult>()
                        val total = subdomains.size
                        subdomains.forEachIndexed { index, sub ->
                            if (isVerbose) Logger.log("Scanning [$index/$total]: $sub")
                            
                            // Note: We force verbose OFF for batch scan to avoid flooding memory/UI
                            // Unless critical error handling is needed.
                            val res = Scanner.testSingle(targetHost, sub, false) 
                            
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
                progress = progress, 
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

@Composable
fun VerboseLogScreen() {
    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)) {
        
        Text("Verbose Logs", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        
        Card(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            SelectionContainer {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    reverseLayout = true // Show latest at bottom logic handled by list order, actually standard is fine.
                ) {
                    items(Logger.logs) { log ->
                        Text(
                            text = log, 
                            color = Color(0xFF00FF00), // Hacker green
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                        Divider(color = Color.DarkGray, thickness = 0.5.dp)
                    }
                }
            }
        }
        
        Button(
            onClick = { Logger.clear() },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Text("Clear Logs")
        }
    }
}