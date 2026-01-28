package com.hoshiyomix.injecttools

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.hoshiyomix.injecttools.Scanner.ScanResult
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Global log buffer for verbose mode
object Logger {
    private val _logs = mutableStateListOf<String>()
    val logs: List<String> get() = _logs

    fun log(msg: String) {
        _logs.add(msg)
        // Memory protection: keep only last 1000 logs
        if (_logs.size > 1000) {
            _logs.removeAt(0)
        }
    }

    fun clear() {
        _logs.clear()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Dynamic theme following system settings
            MaterialTheme(
                colorScheme = dynamicColorScheme()
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainApp(onExit = { finish() })
                }
            }
        }
    }
}

@Composable
fun dynamicColorScheme(): ColorScheme {
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        if (isSystemInDarkTheme()) {
            dynamicDarkColorScheme(LocalContext.current)
        } else {
            dynamicLightColorScheme(LocalContext.current)
        }
    } else {
        if (isSystemInDarkTheme()) {
            darkColorScheme()
        } else {
            lightColorScheme()
        }
    }
}

enum class Screen {
    MENU, SINGLE_TEST, CRTSH_TEST, RESULTS, VERBOSE_LOGS
}

data class MenuTile(
    val id: Int,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val gradient: Pair<Color, Color>,
    val screen: Screen?
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun MainApp(onExit: () -> Unit) {
    var currentScreen by remember { mutableStateOf(Screen.MENU) }
    var previousScreen by remember { mutableStateOf(Screen.MENU) } // Track previous screen
    var targetHost by remember { mutableStateOf("") }
    var scanHistory by remember { mutableStateOf(listOf<ScanResult>()) }
    var isVerbose by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Rust/System Integration: Capture logcat for "Rust" tag or general stdout
    LaunchedEffect(isVerbose) {
        if (isVerbose) {
            withContext(Dispatchers.IO) {
                try {
                    val process = Runtime.getRuntime().exec("logcat -d -v time")
                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        line?.let {
                            if (it.contains("System.out") || it.contains("Rust")) {
                                withContext(Dispatchers.Main) {
                                    Logger.log(it)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore logcat errors
                }
            }
        }
    }

    fun addResults(newResults: List<ScanResult>) {
        scanHistory = newResults + scanHistory
    }

    fun navigateTo(screen: Screen) {
        if (currentScreen != screen) {
            previousScreen = currentScreen
            currentScreen = screen
        }
    }

    fun navigateBack() {
        if (currentScreen == Screen.VERBOSE_LOGS) {
             currentScreen = previousScreen
             if (currentScreen == Screen.VERBOSE_LOGS) {
                 currentScreen = Screen.MENU
             }
        } else if (currentScreen != Screen.MENU) {
            currentScreen = Screen.MENU
        }
    }

    Scaffold(
        topBar = {
            if (currentScreen != Screen.MENU) {
                TopAppBar(
                    title = { 
                        Text(
                            text = when (currentScreen) {
                                Screen.SINGLE_TEST -> "Single Subdomain"
                                Screen.CRTSH_TEST -> "Crt.sh Discovery"
                                Screen.RESULTS -> "Scan Results"
                                Screen.VERBOSE_LOGS -> "Verbose Logs"
                                else -> ""
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navigateBack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (currentScreen != Screen.VERBOSE_LOGS) {
                            IconButton(onClick = { navigateTo(Screen.VERBOSE_LOGS) }) {
                                if (isVerbose) {
                                     Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                         Icon(Icons.Default.Terminal, contentDescription = "Logs")
                                     }
                                } else {
                                    Icon(Icons.Default.Terminal, contentDescription = "Logs")
                                }
                            }
                        }
                    }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (currentScreen) {
                Screen.MENU -> MenuScreen(
                    targetHost = targetHost,
                    isVerbose = isVerbose,
                    onUpdateHost = { targetHost = it },
                    onToggleVerbose = { isVerbose = it },
                    onNavigate = { screen ->
                        if ((screen == Screen.SINGLE_TEST || screen == Screen.CRTSH_TEST) && targetHost.isBlank()) {
                            Toast.makeText(context, "⚠️ Set target host in header first!", Toast.LENGTH_SHORT).show()
                        } else {
                            navigateTo(screen)
                        }
                    }
                )
                Screen.SINGLE_TEST -> ManualScanScreen(targetHost, isVerbose, onResult = { addResults(listOf(it)) })
                Screen.CRTSH_TEST -> CrtshScanScreen(targetHost, isVerbose, onResults = { addResults(it) })
                Screen.RESULTS -> ResultHistoryScreen(scanHistory)
                Screen.VERBOSE_LOGS -> VerboseLogScreen(onBack = { navigateBack() })
            }
        }
    }

    BackHandler(enabled = currentScreen != Screen.MENU) {
        navigateBack()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun MenuScreen(
    targetHost: String,
    isVerbose: Boolean,
    onUpdateHost: (String) -> Unit,
    onToggleVerbose: (Boolean) -> Unit,
    onNavigate: (Screen) -> Unit
) {
    val tiles = listOf(
        MenuTile(
            1, 
            "Single Test", 
            "Quick subdomain check",
            Icons.Default.Search,
            Pair(Color(0xFF667EEA), Color(0xFF764BA2)),
            Screen.SINGLE_TEST
        ),
        MenuTile(
            2, 
            "Crt.sh Scan", 
            "Auto-discover & test",
            Icons.Default.AccountTree,
            Pair(Color(0xFFF093FB), Color(0xFFF5576C)),
            Screen.CRTSH_TEST
        ),
        MenuTile(
            3, 
            "Results", 
            "View scan history",
            Icons.Default.List,
            Pair(Color(0xFF4FACFE), Color(0xFF00F2FE)),
            Screen.RESULTS
        )
    )
    
    var isEditingHost by remember { mutableStateOf(false) }
    var tempHost by remember { mutableStateOf(targetHost) }
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "InjectTools",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "v3.6.5",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "TARGET HOST (SNI)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (isEditingHost) {
                        OutlinedTextField(
                            value = tempHost,
                            onValueChange = { tempHost = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodyLarge,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                onUpdateHost(tempHost)
                                isEditingHost = false
                                keyboardController?.hide()
                            }),
                            trailingIcon = {
                                IconButton(onClick = {
                                    onUpdateHost(tempHost)
                                    isEditingHost = false
                                    keyboardController?.hide()
                                }) {
                                    Icon(Icons.Default.Check, contentDescription = "Save", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    tempHost = targetHost
                                    isEditingHost = true 
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = targetHost.ifBlank { "Tap to set host..." },
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (targetHost.isNotBlank()) FontWeight.Bold else FontWeight.Normal,
                                color = if (targetHost.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                Icons.Default.Edit, 
                                contentDescription = "Edit", 
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    onClick = { onToggleVerbose(!isVerbose) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Checkbox(
                            checked = isVerbose,
                            onCheckedChange = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Verbose Logs",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Show stdout/stderr debug info",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.height(400.dp)
        ) {
            items(tiles) { tile ->
                MenuTileCard(tile) {
                    if (tile.screen != null) {
                        onNavigate(tile.screen)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuTileCard(tile: MenuTile, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            tile.gradient.first.copy(alpha = 0.8f),
                            tile.gradient.second.copy(alpha = 0.8f)
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(
                    imageVector = tile.icon,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = Color.White
                )
                
                Column {
                    Text(
                        text = tile.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = tile.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }
        }
    }
}

@Composable
fun ResultHistoryScreen(history: List<ScanResult>) {
    if (history.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "No scan results yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.List,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Scan History", 
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${history.size} results in this session",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            items(history) { res ->
                ResultItem(res)
            }
        }
    }
}

// Logic to check network status and confirm scan
fun checkNetworkAndConfirm(
    context: android.content.Context, 
    onProceed: () -> Unit
) {
    val status = NetworkUtils.checkNetworkStatus(context)
    
    when (status) {
        NetworkUtils.NetworkStatus.NO_INTERNET_NO_VPN -> {
            // Only this condition allows scan to proceed
            onProceed()
        }
        NetworkUtils.NetworkStatus.INTERNET_NO_VPN -> {
            Toast.makeText(context, "❌ BLOCKED: Regular internet detected! Disable WiFi/Data or use injection mode.", Toast.LENGTH_LONG).show()
        }
        NetworkUtils.NetworkStatus.NO_INTERNET_VPN -> {
            Toast.makeText(context, "❌ BLOCKED: VPN is active! Please disable VPN before scanning.", Toast.LENGTH_LONG).show()
        }
        NetworkUtils.NetworkStatus.INTERNET_VPN -> {
            Toast.makeText(context, "❌ BLOCKED: VPN + Internet detected! Disable both VPN and regular connection.", Toast.LENGTH_LONG).show()
        }
        NetworkUtils.NetworkStatus.DISCONNECTED -> {
            Toast.makeText(context, "❌ No network connection. Connect to WiFi/Data first.", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun ManualScanScreen(targetHost: String, isVerbose: Boolean, onResult: (ScanResult) -> Unit) {
    var subdomain by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf<ScanResult?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Single Subdomain Test",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Test individual subdomain against target",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = subdomain,
            onValueChange = { subdomain = it },
            label = { Text("Subdomain") },
            placeholder = { Text("cdn.cloudflare.com") },
            leadingIcon = {
                Icon(Icons.Default.Language, contentDescription = null)
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = {
                if (isScanning || subdomain.isBlank()) return@Button
                
                checkNetworkAndConfirm(context) {
                    isScanning = true
                    if (isVerbose) {
                        Logger.clear()
                        Logger.log("--- Starting scan for ${subdomain.trim()} ---")
                    }
                    
                    scope.launch {
                        val res = Scanner.testSingle(targetHost, subdomain.trim(), isVerbose)
                        lastResult = res
                        onResult(res)
                        isScanning = false
                    }
                }
            },
            enabled = !isScanning && subdomain.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isScanning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scanning...")
            } else {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Test Connection", fontSize = 16.sp)
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        lastResult?.let {
            Text(
                "Last Result",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Icon(
                    Icons.Default.AccountTree,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Crt.sh Discovery",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Auto-discover and test subdomains",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = domain,
            onValueChange = { domain = it },
            label = { Text("Domain") },
            placeholder = { Text("cloudflare.com") },
            leadingIcon = {
                Icon(Icons.Default.Language, contentDescription = null)
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

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
                            if (isVerbose) Logger.log("Error: ${e.message}")
                        }
                        isFetching = false
                    }
                },
                enabled = !isFetching && !isScanning && domain.isNotBlank(),
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isFetching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Default.Download, contentDescription = null)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isFetching) "Fetching..." else "Fetch")
            }

            Button(
                onClick = {
                    if (isScanning || subdomains.isEmpty()) return@Button

                    checkNetworkAndConfirm(context) {
                        isScanning = true
                        scanResults = emptyList()
                        if (isVerbose) Logger.clear()
                        
                        scope.launch {
                            val tempResults = mutableListOf<ScanResult>()
                            val total = subdomains.size
                            subdomains.forEachIndexed { index, sub ->
                                if (isVerbose) Logger.log("[$index/$total]: $sub")
                                val res = Scanner.testSingle(targetHost, sub, false)
                                tempResults.add(res)
                                scanResults = tempResults.toList()
                                progress = (index + 1) / total.toFloat()
                            }
                            onResults(tempResults)
                            isScanning = false
                        }
                    }
                },
                enabled = !isScanning && subdomains.isNotEmpty(),
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isScanning) "Scanning" else "Scan All")
            }
        }
        
        if (isScanning) {
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "${(progress * 100).toInt()}% (${scanResults.size}/${subdomains.size})",
                modifier = Modifier.align(Alignment.End),
                style = MaterialTheme.typography.labelSmall
            )
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
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (res.isWorking) Icons.Default.CheckCircle else Icons.Default.Cancel,
                contentDescription = null,
                tint = if (res.isWorking) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = res.subdomain,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = res.ip.ifBlank { "No IP" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (res.errorMsg != null) {
                    Text(
                        text = res.errorMsg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            if (res.isCloudflare) {
                AssistChip(
                    onClick = {},
                    label = { Text("CF") },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                )
            }
        }
    }
}

@Composable
fun VerboseLogScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        "Verbose Logs",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text("${Logger.logs.size} entries")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        
        Card(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E1E1E)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            SelectionContainer {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    items(Logger.logs) { log ->
                        Text(
                            text = log,
                            color = Color(0xFF00FF00),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Button(
            onClick = { Logger.clear() },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Delete, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Clear Logs")
        }
    }
}
