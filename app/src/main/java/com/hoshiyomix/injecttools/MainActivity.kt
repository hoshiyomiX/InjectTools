package com.hoshiyomix.injecttools

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import com.hoshiyomix.injecttools.Scanner.ScanResult

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext



class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = dynamicColorScheme()
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainApp()
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
    MENU, SINGLE_TEST, CRTSH_TEST, RESULTS
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
fun MainApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("InjectToolsPrefs", Context.MODE_PRIVATE) }
    
    var currentScreen by remember { mutableStateOf(Screen.MENU) }
    var previousScreen by remember { mutableStateOf(Screen.MENU) }
    var targetHost by remember { mutableStateOf(prefs.getString("target_host", "") ?: "") }
    var scanHistory by remember { mutableStateOf(listOf<ScanResult>()) }

    var showFirstRunDialog by remember { mutableStateOf(prefs.getBoolean("first_run", true)) }
    var showNetworkWarningDialog by remember { mutableStateOf(false) }
    var networkWarningMessage by remember { mutableStateOf("") }



    fun saveTargetHost(host: String) {
        targetHost = host
        prefs.edit().putString("target_host", host).apply()
    }

    fun addResults(newResults: List<ScanResult>) {
        // Keep only latest 20 results, newest on top, success only
        val successResults = newResults.filter { it.isWorking }
        val combined = (successResults + scanHistory.filter { it.isWorking }).take(20)
        scanHistory = combined
    }

    fun navigateTo(screen: Screen) {
        if (currentScreen != screen) {
            previousScreen = currentScreen
            currentScreen = screen
        }
    }

    fun navigateBack() {
        if (currentScreen != Screen.MENU) {
            currentScreen = Screen.MENU
        }
    }

    // First Run Dialog
    if (showFirstRunDialog) {
        FirstRunDialog(
            onConfirm = { host ->
                saveTargetHost(host)
                prefs.edit().putBoolean("first_run", false).apply()
                showFirstRunDialog = false
            }
        )
    }

    // Network Warning Dialog
    if (showNetworkWarningDialog) {
        AlertDialog(
            onDismissRequest = { 
                showNetworkWarningDialog = false
                networkWarningMessage = ""
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Network Connection Warning")
                }
            },
            text = {
                Text(networkWarningMessage, style = MaterialTheme.typography.bodyMedium)
            },
            confirmButton = {
                Button(
                    onClick = { 
                        showNetworkWarningDialog = false
                        networkWarningMessage = ""
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("OK")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    Scaffold(
        topBar = {
            if (currentScreen != Screen.MENU) {
                TopAppBar(
                    title = {
                        Text(
                            text = when (currentScreen) {
                                Screen.SINGLE_TEST -> "Test Single Subdomain"
                                Screen.CRTSH_TEST -> "Scan & Batch Test Subdomain"
                            Screen.RESULTS -> "History Logs"
                                else -> ""
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navigateBack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                    onUpdateHost = { saveTargetHost(it) },
                    onNavigate = { screen ->
                        if ((screen == Screen.SINGLE_TEST || screen == Screen.CRTSH_TEST) && targetHost.isBlank()) {
                            Toast.makeText(context, "⚠️ Set target host in header first!", Toast.LENGTH_SHORT).show()
                        } else {
                            navigateTo(screen)
                        }
                    }
                )
                Screen.SINGLE_TEST -> ManualScanScreen(targetHost, onResult = { addResults(listOf(it)) }, onShowNetworkWarning = { message ->
                    networkWarningMessage = message
                    showNetworkWarningDialog = true
                })
                Screen.CRTSH_TEST -> CrtshScanScreen(targetHost, onResults = { addResults(it) }, onShowNetworkWarning = { message ->
                    networkWarningMessage = message
                    showNetworkWarningDialog = true
                })
                Screen.RESULTS -> ResultHistoryScreen(scanHistory)
            }
        }
    }

    BackHandler(enabled = currentScreen != Screen.MENU) {
        navigateBack()
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun FirstRunDialog(onConfirm: (String) -> Unit) {
    var hostInput by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    AlertDialog(
        onDismissRequest = {},
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Welcome to InjectTools")
            }
        },
        text = {
            Column {
                Text("Set your injection target domain to get started.", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = hostInput,
                    onValueChange = { hostInput = it },
                    label = { Text("Target Domain Host") },
                    placeholder = { Text("sg.server.web.id") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (hostInput.isNotBlank()) {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            onConfirm(hostInput)
                        }
                    }),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (hostInput.isNotBlank()) {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        onConfirm(hostInput)
                    }
                },
                enabled = hostInput.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Continue")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun MenuScreen(
    targetHost: String,
    onUpdateHost: (String) -> Unit,
    onNavigate: (Screen) -> Unit
) {
    val tiles = listOf(
        MenuTile(1, "Test Single Subdomain", "Test single bug subdomain", Icons.Default.Search, Pair(Color(0xFF667EEA), Color(0xFF764BA2)), Screen.SINGLE_TEST),
        MenuTile(2, "Scan & Batch Test Subdomain", "Discover subdomains from crt.sh & test", Icons.Default.AccountTree, Pair(Color(0xFFF093FB), Color(0xFFF5576C)), Screen.CRTSH_TEST),
        MenuTile(3, "History Logs", "View scan history", Icons.Default.List, Pair(Color(0xFF4FACFE), Color(0xFF00F2FE)), Screen.RESULTS)
    )

    var showHostDialog by remember { mutableStateOf(false) }
    var tempHost by remember { mutableStateOf(targetHost) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
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
                        text = "v3.7.0",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                            .clickable { 
                                tempHost = targetHost
                                showHostDialog = true 
                            }
                            .padding(16.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "DOMAIN HOST",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = targetHost.ifBlank { "contoh: sg.server.web.id" },
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (targetHost.isNotBlank()) FontWeight.Bold else FontWeight.Normal,
                                color = if (targetHost.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }


                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier.height(480.dp)
            ) {
                items(tiles) { tile ->
                    MenuTileCard(tile, enabled = !showHostDialog) {
                        if (!showHostDialog) {
                            tile.screen?.let(onNavigate)
                        }
                    }
                }
            }
        }

        if (showHostDialog) {
            HostEditDialog(
                currentHost = tempHost,
                onHostChange = { tempHost = it },
                onDismiss = { showHostDialog = false },
                onConfirm = {
                    onUpdateHost(tempHost)
                    showHostDialog = false
                }
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HostEditDialog(
    currentHost: String,
    onHostChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Set Target Host")
            }
        },
        text = {
            Column {
                Text("Enter the domain host to use as injection target.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = currentHost,
                    onValueChange = onHostChange,
                    label = { Text("Domain Host") },
                    placeholder = { Text("sg.server.web.id") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        onConfirm()
                    }),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { keyboardController?.hide(); focusManager.clearFocus(); onConfirm() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuTileCard(tile: MenuTile, enabled: Boolean = true, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(180.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(colors = listOf(tile.gradient.first.copy(alpha = if (enabled) 0.8f else 0.4f), tile.gradient.second.copy(alpha = if (enabled) 0.8f else 0.4f))))
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Icon(imageVector = tile.icon, contentDescription = null, modifier = Modifier.size(32.dp), tint = Color.White.copy(alpha = if (enabled) 1f else 0.5f))
                Column {
                    Text(text = tile.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = if (enabled) 1f else 0.5f))
                    Text(text = tile.subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = if (enabled) 0.9f else 0.4f))
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
                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(16.dp))
                Text("No scan results yet", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(16.dp)) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Recent Result (Latest 20)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("${history.size} successful results", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            items(history.take(20)) { res -> ResultItem(res, onTap = {}) }
        }
    }
}

fun checkNetworkAndConfirm(
    context: android.content.Context, 
    onProceed: () -> Unit,
    showWarningDialog: (String) -> Unit
) {
    val status = NetworkUtils.checkNetworkStatus(context)
    when (status) {
        NetworkUtils.NetworkStatus.NO_INTERNET_NO_VPN -> onProceed()
        NetworkUtils.NetworkStatus.INTERNET_NO_VPN -> showWarningDialog("❌ BLOCKED: Regular internet detected! Disable WiFi/Data or use injection mode.")
        NetworkUtils.NetworkStatus.NO_INTERNET_VPN -> showWarningDialog("❌ BLOCKED: VPN is active! Please disable VPN before scanning.")
        NetworkUtils.NetworkStatus.INTERNET_VPN -> showWarningDialog("❌ BLOCKED: VPN + Internet detected! Disable both VPN and regular connection.")
        NetworkUtils.NetworkStatus.DISCONNECTED -> showWarningDialog("❌ No network connection. Connect to WiFi/Data first.")
    }
}

@Composable
fun ManualScanScreen(targetHost: String, onResult: (ScanResult) -> Unit, onShowNetworkWarning: (String) -> Unit) {
    var subdomain by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }
    var recentResults by remember { mutableStateOf<List<ScanResult>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Test Single Subdomain", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Test individual subdomain against target", style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = subdomain,
            onValueChange = { subdomain = it },
            label = { Text("Subdomain") },
            placeholder = { Text("Contoh: api.instagram.com") },
            leadingIcon = { Icon(Icons.Default.Language, contentDescription = null) },
            trailingIcon = {
                if (subdomain.isNotBlank()) {
                    IconButton(onClick = { subdomain = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (isScanning || subdomain.isBlank()) return@Button
                checkNetworkAndConfirm(context, {
                    isScanning = true
                    scope.launch {
                        val res = Scanner.testSingle(targetHost, subdomain.trim())
                        recentResults = (listOf(res) + recentResults).take(10)
                        onResult(res)
                        isScanning = false
                    }
                }, onShowNetworkWarning)
            },
            enabled = !isScanning && subdomain.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isScanning) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scanning...")
            } else {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Test Connection", fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (recentResults.isNotEmpty()) {
            Text("Recent Result", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            recentResults.take(10).forEach { result ->
                ResultItem(result, onTap = { subdomain = result.subdomain })
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun CrtshScanScreen(targetHost: String, onResults: (List<ScanResult>) -> Unit, onShowNetworkWarning: (String) -> Unit) {
    var domain by remember { mutableStateOf("") }
    var subdomains by remember { mutableStateOf(listOf<String>()) }
    var scanResults by remember { mutableStateOf(listOf<ScanResult>()) }
    var isFetching by remember { mutableStateOf(false) }
    var isScanning by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var lastFetchedDomain by remember { mutableStateOf("") }
    var fetchSummary by remember { mutableStateOf("") }
    var showBackWarningDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Icon(Icons.Default.AccountTree, contentDescription = null, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Scan & Batch Test Subdomain", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Auto-discover and test subdomains", style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = domain,
            onValueChange = { domain = it },
            label = { Text("Domain") },
            placeholder = { Text("cloudflare.com") },
            leadingIcon = { Icon(Icons.Default.Language, contentDescription = null) },
            trailingIcon = {
                if (domain.isNotBlank()) {
                    IconButton(onClick = { domain = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
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
                    fetchSummary = ""
                    scope.launch {
                        try {
                            subdomains = Crtsh.fetchSubdomains(domain.trim())
                            lastFetchedDomain = domain.trim()
                            fetchSummary = "Found ${subdomains.size} subdomains from crt.sh for $domain"
                        } catch (e: Exception) {
                            subdomains = emptyList()
                            fetchSummary = "Failed to fetch subdomains: ${e.message}"
                        }
                        isFetching = false
                    }
                },
                enabled = !isFetching && !isScanning && domain.isNotBlank() && !(subdomains.isNotEmpty() && lastFetchedDomain == domain.trim()),
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isFetching) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Default.Download, contentDescription = null)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isFetching) "Fetching..." else "Fetch")
            }

            Button(
                onClick = {
                    if (isScanning || subdomains.isEmpty()) return@Button
                    checkNetworkAndConfirm(context, {
                        isScanning = true
                        scanResults = emptyList()
                        scope.launch {
                            val tempResults = mutableListOf<ScanResult>()
                            val total = subdomains.size
                            subdomains.forEachIndexed { index, sub ->
                                val res = Scanner.testSingle(targetHost, sub)
                                tempResults.add(res)
                                scanResults = tempResults.toList()
                                progress = (index + 1) / total.toFloat()
                            }
                            onResults(tempResults)
                            isScanning = false
                        }
                    }, onShowNetworkWarning)
                },
                enabled = !isScanning && subdomains.isNotEmpty(),
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isScanning) "Scanning" else if (scanResults.isNotEmpty() && lastFetchedDomain == domain.trim()) "Re-scan" else "Scan All")
            }
        }

        if (isScanning) {
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
            Text("${(progress * 100).toInt()}% (${scanResults.size}/${subdomains.size})", modifier = Modifier.align(Alignment.End), style = MaterialTheme.typography.labelSmall)
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (fetchSummary.isNotEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), shape = RoundedCornerShape(12.dp)) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = fetchSummary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        LazyColumn {
            items(scanResults.take(20).filter { it.isWorking }) { res -> ResultItem(res, onTap = {}) }
        }
    }

    // Back handler with warning dialog
    BackHandler(enabled = isFetching || isScanning) {
        showBackWarningDialog = true
    }

    // Warning dialog for back navigation during operations
    if (showBackWarningDialog) {
        AlertDialog(
            onDismissRequest = { showBackWarningDialog = false },
            title = { Text("Operation in Progress") },
            text = { Text("Fetch or scan is still running. Going back will cancel the current operation. Continue?") },
            confirmButton = {
                Button(
                    onClick = {
                        showBackWarningDialog = false
                        isFetching = false
                        isScanning = false
                    }
                ) {
                    Text("Yes, Go Back")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackWarningDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultItem(res: ScanResult, onTap: (ScanResult) -> Unit) {
    Card(
        onClick = { onTap(res) },
        colors = CardDefaults.cardColors(containerColor = if (res.isWorking) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (res.isWorking) Icons.Default.CheckCircle else Icons.Default.Cancel,
                contentDescription = null,
                tint = if (res.isWorking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = res.subdomain, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(text = res.ip.ifBlank { "No IP" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                res.errorMsg?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            if (res.isCloudflare) {
                AssistChip(onClick = {}, label = { Text("CF") }, colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer))
            }
        }
    }
}


