package com.deviant.injecttools

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import com.deviant.injecttools.Scanner.ScanResult

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

// Red Rose fallback colors for SDK < Android 12 (S)
private val RedRosePrimary = Color(0xFFE91E63)      // Material Pink 500
private val RedRosePrimaryDark = Color(0xFFC2185B)  // Pink 700
private val RedRosePrimaryContainer = Color(0xFFFCE4EC)  // Pink 50
private val RedRoseOnPrimaryContainer = Color(0xFF880E4F) // Pink 900
private val RedRoseSecondary = Color(0xFFF06292)    // Pink 300
private val RedRoseSecondaryContainer = Color(0xFFFFF0F5) // Lavender Blush
private val RedRoseTertiary = Color(0xFFFF4081)     // Pink A200
private val RedRoseTertiaryContainer = Color(0xFFF8BBD0) // Pink 100

@Composable
fun dynamicColorScheme(): ColorScheme {
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        // Use system accent (Material You) on Android 12+
        if (isSystemInDarkTheme()) {
            dynamicDarkColorScheme(LocalContext.current)
        } else {
            dynamicLightColorScheme(LocalContext.current)
        }
    } else {
        // Fallback to Red Rose color scheme for older Android versions
        if (isSystemInDarkTheme()) {
            darkColorScheme(
                primary = RedRosePrimaryDark,
                primaryContainer = RedRoseOnPrimaryContainer,
                onPrimaryContainer = RedRosePrimaryContainer,
                secondary = RedRoseSecondary,
                secondaryContainer = RedRoseOnPrimaryContainer,
                tertiary = RedRoseTertiary,
                tertiaryContainer = RedRoseTertiaryContainer
            )
        } else {
            lightColorScheme(
                primary = RedRosePrimary,
                primaryContainer = RedRosePrimaryContainer,
                onPrimaryContainer = RedRoseOnPrimaryContainer,
                secondary = RedRoseSecondary,
                secondaryContainer = RedRoseSecondaryContainer,
                tertiary = RedRoseTertiary,
                tertiaryContainer = RedRoseTertiaryContainer
            )
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

// Material 3 Shape constants
val ShapeExtraLarge = RoundedCornerShape(28.dp)
val ShapeLarge = RoundedCornerShape(24.dp)
val ShapeMedium = RoundedCornerShape(16.dp)
val ShapeSmall = RoundedCornerShape(12.dp)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun MainApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("InjectToolsPrefs", Context.MODE_PRIVATE) }

    var currentScreen by remember { mutableStateOf(Screen.MENU) }
    var targetHost by remember { mutableStateOf(prefs.getString("target_host", "") ?: "") }
    
    // Session-based history: each mass scan creates a new session
    var scanSessions by remember { mutableStateOf(HistoryStorage.loadSessions(context)) }
    
    // Track current mass scan session
    var currentSessionId by remember { mutableStateOf<String?>(null) }
    var currentSessionDomain by remember { mutableStateOf("") }

    var showFirstRunDialog by remember { mutableStateOf(prefs.getBoolean("first_run", true)) }
    var showNetworkWarningDialog by remember { mutableStateOf(false) }
    var networkWarningMessage by remember { mutableStateOf("") }

    fun saveTargetHost(host: String) {
        targetHost = host
        prefs.edit().putString("target_host", host).apply()
    }

    // Start a new session for mass scan
    fun startNewSession(domain: String): String {
        val sessionId = "session_${System.currentTimeMillis()}"
        currentSessionId = sessionId
        currentSessionDomain = domain
        return sessionId
    }

    // Add results to current session (for mass scan) or as single result
    fun addResultsToSession(newResults: List<ScanResult>) {
        val successResults = newResults.filter { it.isWorking }
        if (successResults.isEmpty()) return
        
        val sessionId = currentSessionId
        if (sessionId != null) {
            // Add to existing session
            val sessions = scanSessions.toMutableList()
            val sessionIndex = sessions.indexOfFirst { it.id == sessionId }
            
            if (sessionIndex >= 0) {
                val existing = sessions[sessionIndex]
                sessions[sessionIndex] = existing.copy(
                    results = existing.results + successResults
                )
            }
            
            scanSessions = sessions.sortedByDescending { it.timestamp }
            HistoryStorage.saveSessions(context, scanSessions)
        }
    }
    
    // Create final session after mass scan completes
    fun finalizeSession(results: List<ScanResult>) {
        val successResults = results.filter { it.isWorking }
        if (successResults.isEmpty()) {
            currentSessionId = null
            return
        }
        
        val sessionId = currentSessionId ?: return
        val session = ScanSession(
            id = sessionId,
            domain = currentSessionDomain,
            targetHost = targetHost,
            timestamp = System.currentTimeMillis(),
            results = successResults
        )
        
        scanSessions = HistoryStorage.addSession(context, session)
        currentSessionId = null
    }

    // Delete a session
    fun deleteSession(sessionId: String) {
        scanSessions = HistoryStorage.deleteSession(context, sessionId)
    }

    fun navigateTo(screen: Screen) {
        currentScreen = screen
    }

    fun navigateBack() {
        if (currentScreen != Screen.MENU) {
            currentScreen = Screen.MENU
        }
    }

    if (showFirstRunDialog) {
        FirstRunDialog(
            onConfirm = { host ->
                saveTargetHost(host)
                prefs.edit().putBoolean("first_run", false).apply()
                showFirstRunDialog = false
            }
        )
    }

    if (showNetworkWarningDialog) {
        ModernAlertDialog(
            icon = Icons.Default.Warning,
            iconTint = MaterialTheme.colorScheme.error,
            title = "Peringatan",
            message = networkWarningMessage,
            confirmText = "Oke",
            onConfirm = {
                showNetworkWarningDialog = false
                networkWarningMessage = ""
            },
            onDismiss = {
                showNetworkWarningDialog = false
                networkWarningMessage = ""
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (currentScreen != Screen.MENU) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = when (currentScreen) {
                                Screen.SINGLE_TEST -> "Test Bug"
                                Screen.CRTSH_TEST -> "Scan & Test Bug"
                                Screen.RESULTS -> "History"
                                else -> ""
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    navigationIcon = {
                        FilledTonalIconButton(onClick = { navigateBack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (currentScreen) {
                Screen.MENU -> MenuScreen(
                    targetHost = targetHost,
                    onUpdateHost = { saveTargetHost(it) },
                    onNavigate = { s ->
                        if ((s == Screen.SINGLE_TEST || s == Screen.CRTSH_TEST) && targetHost.isBlank()) {
                            Toast.makeText(context, "Set host dulu bray!", Toast.LENGTH_SHORT).show()
                        } else {
                            navigateTo(s)
                        }
                    }
                )
                Screen.SINGLE_TEST -> ManualScanScreen(targetHost, onResult = { result ->
                    // Single test: simpan ke history sebagai session simple
                    if (result.isWorking) {
                        val session = ScanSession(
                            id = "single_${System.currentTimeMillis()}",
                            domain = result.subdomain,
                            targetHost = targetHost,
                            timestamp = System.currentTimeMillis(),
                            results = listOf(result)
                        )
                        scanSessions = HistoryStorage.addSession(context, session)
                    }
                }, onShowNetworkWarning = { message ->
                    networkWarningMessage = message
                    showNetworkWarningDialog = true
                })
                Screen.CRTSH_TEST -> CrtshScanScreen(
                    targetHost = targetHost,
                    onStartSession = { domain -> startNewSession(domain) },
                    onResults = { results -> finalizeSession(results) },
                    onShowNetworkWarning = { message ->
                    networkWarningMessage = message
                    showNetworkWarningDialog = true
                })
                Screen.RESULTS -> ResultHistoryScreen(scanSessions, onDeleteSession = { deleteSession(it) })
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

    Dialog(onDismissRequest = {}) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = ShapeExtraLarge,
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.tertiaryContainer
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.RocketLaunch,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    "Welcome to InjectTools",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "Set host target inject dulu baru mulai scanning",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = hostInput,
                    onValueChange = { hostInput = it },
                    label = { Text("Host Target") },
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
                    shape = ShapeMedium,
                    trailingIcon = {
                        if (hostInput.isNotEmpty()) {
                            IconButton(onClick = { hostInput = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (hostInput.isNotBlank()) {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            onConfirm(hostInput)
                        }
                    },
                    enabled = hostInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = ShapeLarge
                ) {
                    Icon(Icons.Default.ArrowForward, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Mulai Gas", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun MenuScreen(
    targetHost: String,
    onUpdateHost: (String) -> Unit,
    onNavigate: (Screen) -> Unit
) {
    val tiles = listOf(
        MenuTile(1, "Test Bug", "Test bug satu-satu", Icons.Outlined.TravelExplore, Pair(Color(0xFF6750A4), Color(0xFF9A82DB)), Screen.SINGLE_TEST),
        MenuTile(2, "Scan & Test Bug", "Cari bug dari DNS records", Icons.Outlined.Hub, Pair(Color(0xFFD81B60), Color(0xFFFF6F00)), Screen.CRTSH_TEST),
        MenuTile(3, "History", "Liat hasil scan", Icons.Outlined.History, Pair(Color(0xFF00695C), Color(0xFF4DB6AC)), Screen.RESULTS)
    )

    var showHostDialog by remember { mutableStateOf(false) }
    var tempHost by remember { mutableStateOf(targetHost) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = ShapeExtraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f)
                            )
                        )
                    )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Title
                    Text(
                        text = "InjectTools",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "v1.0",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Domain Host Card
                    Card(
                        onClick = {
                            tempHost = targetHost
                            showHostDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = ShapeMedium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Outlined.Dns,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Host",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = targetHost.ifBlank { "Tap buat set host" },
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (targetHost.isNotBlank()) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (targetHost.isNotBlank()) 
                                        MaterialTheme.colorScheme.onSurface 
                                    else 
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }

                            Icon(
                                Icons.Default.Edit, 
                                contentDescription = "Edit", 
                                modifier = Modifier.size(16.dp), 
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Menu",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(8.dp))
            Divider(
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Menu Tiles
        tiles.forEach { tile ->
            MenuTileCard(
                tile = tile,
                enabled = !showHostDialog
            ) {
                if (!showHostDialog) {
                    tile.screen?.let(onNavigate)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
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

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = ShapeExtraLarge,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Language,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "Set Host",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "Masukin host buat target, misal: indosat.com",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = currentHost,
                    onValueChange = onHostChange,
                    label = { Text("Domain") },
                    placeholder = { Text("sg.server.web.id") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        onConfirm()
                    }),
                    shape = ShapeMedium,
                    leadingIcon = {
                        Icon(Icons.Outlined.Dns, contentDescription = null)
                    },
                    trailingIcon = {
                        if (currentHost.isNotEmpty()) {
                            IconButton(onClick = { onHostChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = ShapeLarge
                    ) {
                        Text("Batal")
                    }
                    Button(
                        onClick = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            onConfirm()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = ShapeLarge
                    ) {
                        Text("Simpan")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuTileCard(
    tile: MenuTile, 
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp),
        shape = ShapeLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(ShapeMedium)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                tile.gradient.first.copy(alpha = if (enabled) 0.9f else 0.4f),
                                tile.gradient.second.copy(alpha = if (enabled) 0.9f else 0.4f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = tile.icon,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = Color.White.copy(alpha = if (enabled) 1f else 0.6f)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tile.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.5f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = tile.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.5f)
                )
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 0.6f else 0.3f)
            )
        }
    }
}

@Composable
fun ResultHistoryScreen(
    sessions: List<ScanSession>,
    onDeleteSession: (String) -> Unit
) {
    if (sessions.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Belum ada hasil",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Hasil scan muncul di sini",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Summary card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ShapeLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.List,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                "History Scan",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "${sessions.size} sesi • ${sessions.sumOf { it.workingCount }} bug konek",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Session items
            items(sessions.size) { index ->
                val session = sessions[index]
                SessionCard(
                    session = session,
                    onDelete = { onDeleteSession(session.id) }
                )
            }
        }
    }
}

@Composable
fun SessionCard(
    session: ScanSession,
    onDelete: () -> Unit
) {
    // Check if this is a single test result (no dropdown needed)
    val isSingleTest = session.results.size == 1 || session.id.startsWith("single_")
    var expanded by remember { mutableStateOf(isSingleTest) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = ShapeMedium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column {
            // Header - always visible
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isSingleTest) Modifier.padding(16.dp)
                        else Modifier.clickable { expanded = !expanded }.padding(16.dp)
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status dot - GREEN
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4CAF50))
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Info: Date, Domain, Target Host, IP, Latency
                Column(modifier = Modifier.weight(1f)) {
                    // Date
                    Text(
                        session.formattedDate,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    // Domain (show subdomain if mass scan only found 1 result)
                    Text(
                        if (session.results.size == 1 && !session.id.startsWith("single_")) session.results.first().subdomain else session.domain,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    // Target Host
                    if (session.targetHost.isNotBlank()) {
                        Text(
                            "Host: ${session.targetHost}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // IP and Latency (show first result for single test)
                    if (isSingleTest && session.results.isNotEmpty()) {
                        val res = session.results.first()
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "IP: ${res.ip}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (res.latency > 0) {
                                Text(
                                    "  •  ${res.latency}ms",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF4CAF50),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Working count badge - only show for mass scan
                if (!isSingleTest) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF4CAF50).copy(alpha = 0.15f)
                    ) {
                        Text(
                            "${session.workingCount} konek",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF4CAF50)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                // Expand/collapse icon - only for mass scan
                if (!isSingleTest) {
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Expanded content - show results (for mass scan)
            if (expanded && session.results.isNotEmpty() && !isSingleTest) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    session.results.forEach { result ->
                        SessionResultItem(result)
                    }
                }
            }
        }
    }
}

@Composable
fun SessionResultItem(result: Scanner.ScanResult) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status indicator - GREEN
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Color(0xFF4CAF50))
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                result.subdomain,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    result.ip,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (result.latency > 0) {
                    Text(
                        "  •  ${result.latency}ms",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Cloudflare badge
        if (result.isCloudflare) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFFFF6B35).copy(alpha = 0.15f)
            ) {
                Text(
                    "CF",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF6B35)
                )
            }
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
        NetworkUtils.NetworkStatus.INTERNET_NO_VPN -> showWarningDialog("Kuota reguler aktif! Matiin WiFi/Data atau pake config inject.")
        NetworkUtils.NetworkStatus.NO_INTERNET_VPN -> showWarningDialog("VPN nyala! Matiin dulu VPN nya.")
        NetworkUtils.NetworkStatus.INTERNET_VPN -> showWarningDialog("Kuota reguler + VPN aktif! Matiin keduanya.")
        NetworkUtils.NetworkStatus.DISCONNECTED -> showWarningDialog("Gak ada sinyal. Nyambungin WiFi/Data dulu.")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualScanScreen(targetHost: String, onResult: (ScanResult) -> Unit, onShowNetworkWarning: (String) -> Unit) {
    var subdomain by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }
    var recentResults by remember { mutableStateOf<List<ScanResult>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        // Input Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = ShapeLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    "Input Bug",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = subdomain,
                    onValueChange = { subdomain = it },
                    placeholder = { Text("bug.example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = ShapeMedium,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    leadingIcon = {
                        Icon(Icons.Outlined.Language, contentDescription = null)
                    },
                    trailingIcon = {
                        if (subdomain.isNotEmpty()) {
                            IconButton(onClick = { subdomain = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (subdomain.isNotBlank()) {
                            checkNetworkAndConfirm(context, {
                                isScanning = true
                                scope.launch {
                                    val result = Scanner.testSingle(targetHost, subdomain)
                                    recentResults = listOf(result) + recentResults.take(4)
                                    onResult(result)
                                    isScanning = false
                                }
                            }, onShowNetworkWarning)
                        }
                    },
                    enabled = !isScanning && subdomain.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = ShapeLarge
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scanning...")
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Test Bug")
                    }
                }
            }
        }

        if (recentResults.isNotEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "Hasil Test",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))
            recentResults.first().let { res ->
                ResultItem(res, onTap = { scanResult ->
                    // Tap to paste subdomain to input box
                    subdomain = scanResult.subdomain
                })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrtshScanScreen(
    targetHost: String,
    onStartSession: (String) -> String,
    onResults: (List<ScanResult>) -> Unit,
    onShowNetworkWarning: (String) -> Unit
) {
    var domain by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }
    var isFetching by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var statusText by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ScanResult>>(emptyList()) }
    var pendingSubdomains by remember { mutableStateOf<List<String>>(emptyList()) }
    var showTestConfirmDialog by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var currentScanDomain by remember { mutableStateOf("") }

    // Helper function to start testing phase
    fun startTesting(subdomains: List<String>, scanDomain: String) {
        // Start new session
        currentScanDomain = scanDomain
        onStartSession(scanDomain)
        
        isScanning = true
        progress = 0.1f // Start from 10% (after fetch phase)
        results = emptyList()
        statusText = "Ketemu ${subdomains.size} bug. Testing..."
        
        scope.launch {
            val scanResults = mutableListOf<ScanResult>()
            subdomains.forEachIndexed { index, sub ->
                val result = Scanner.testSingle(targetHost, sub)
                scanResults.add(result)
                // Real-time update: show results as they come in
                results = scanResults.toList()
                // Test phase: 10% - 100%
                progress = 0.1f + (0.9f * (index + 1) / subdomains.size)
                statusText = "Test ${index + 1}/${subdomains.size}: $sub (${scanResults.count { it.isWorking }} work)"
            }
            
            // Finalize session with all results
            onResults(scanResults.toList())
            
            statusText = "Done! ${scanResults.count { it.isWorking }} bug konek"
            progress = 1f
            isScanning = false
            // Clear pending after successful test
            pendingSubdomains = emptyList()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        // Input Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = ShapeLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    "Scan & Test Bug",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Ambil bug dari HackerTarget DNS",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = domain,
                    onValueChange = { domain = it },
                    placeholder = { Text("cloudflare.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = ShapeMedium,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    leadingIcon = {
                        Icon(Icons.Outlined.Public, contentDescription = null)
                    },
                    trailingIcon = {
                        if (domain.isNotEmpty()) {
                            IconButton(onClick = { domain = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (domain.isNotBlank()) {
                            // STEP 1: Check if we have internet for fetching
                            if (!NetworkUtils.hasInternetConnection(context)) {
                                onShowNetworkWarning("Gak ada sinyal. Sambungin WiFi/Data dulu.")
                                return@Button
                            }
                            
                            // STEP 2: Fetch subdomains from HackerTarget
                            isFetching = true
                            progress = 0f
                            results = emptyList()
                            statusText = "Connecting to HackerTarget..."
                            
                            scope.launch {
                                val fetchResult = SubdomainFetcher.fetchSubdomains(
                                    domain = domain,
                                    onProgress = { fetchProgress ->
                                        statusText = fetchProgress.phase
                                        progress = fetchProgress.progress * 0.1f
                                    }
                                )
                                isFetching = false
                                
                                if (fetchResult.subdomains.isEmpty()) {
                                    val errorMsg = fetchResult.error ?: "Kagak ketemu bug buat $domain"
                                    statusText = errorMsg
                                    progress = 1f
                                } else {
                                    // STEP 3: Store subdomains and show confirmation dialog
                                    pendingSubdomains = fetchResult.subdomains
                                    statusText = "Ketemu ${fetchResult.subdomains.size} bug"
                                    progress = 0.1f
                                    showTestConfirmDialog = true
                                }
                            }
                        }
                    },
                    enabled = !isFetching && !isScanning && domain.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = ShapeLarge
                ) {
                    if (isFetching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Fetching...")
                    } else if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Testing...")
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Scan")
                    }
                }
            }
        }

        // Progress
        if (isFetching || isScanning || progress > 0f) {
            Spacer(modifier = Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                statusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Pending Subdomains Card - Show retry option when subdomains are fetched but not tested yet
        if (pendingSubdomains.isNotEmpty() && !isScanning && !isFetching && results.isEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = ShapeLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.HourglassTop,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${pendingSubdomains.size} Bug Siap Dites",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                "No kuota reguler / VPN inject",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                // User explicitly cancels - clear pending
                                pendingSubdomains = emptyList()
                                statusText = ""
                                progress = 0f
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = ShapeMedium
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Batal")
                        }
                        Button(
                            onClick = {
                                // Check injection mode and start testing
                                checkNetworkAndConfirm(context, {
                                    startTesting(pendingSubdomains, domain)
                                }, onShowNetworkWarning)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = ShapeMedium
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Retry")
                        }
                    }
                }
            }
        }

        // Results
        if (results.isNotEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))
            val workingResults = results.filter { it.isWorking }
            val maxDisplay = 20
            val displayResults = workingResults.take(maxDisplay)
            val hasMore = workingResults.size > maxDisplay
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Hasil",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (hasMore) "${displayResults.size} dari ${workingResults.size} konek" 
                    else "${workingResults.size} konek",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                items(displayResults) { res ->
                    ResultItem(res, onTap = {})
                }
            }
        }
    }

    // Confirmation Dialog - shown after successful fetch
    if (showTestConfirmDialog) {
        TestConfirmationDialog(
            subdomainCount = pendingSubdomains.size,
            onConfirm = {
                showTestConfirmDialog = false
                // STEP 4: Check injection mode conditions before testing
                // Note: If network check fails, pendingSubdomains is preserved so user can retry
                checkNetworkAndConfirm(context, {
                    startTesting(pendingSubdomains, domain)
                }, onShowNetworkWarning)
            },
            onCancel = {
                // User explicitly cancels - clear everything
                showTestConfirmDialog = false
                pendingSubdomains = emptyList()
                statusText = ""
                progress = 0f
            },
            onDismiss = {
                // User taps outside dialog - just close, keep pendingSubdomains for retry
                showTestConfirmDialog = false
            }
        )
    }
}

/**
 * Dialog shown after fetching subdomains to confirm testing phase.
 * This is where we check injection mode conditions (no VPN, no regular internet).
 */
@Composable
fun TestConfirmationDialog(
    subdomainCount: Int,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = ShapeExtraLarge,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "Start Test?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "Ketemu $subdomainCount bug siap dites.\n\n⚠️ Pastiin gak lagi pake kuota reguler / VPN inject",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = ShapeLarge
                    ) {
                        Text("Batal")
                    }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = ShapeLarge
                    ) {
                        Text("Start")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultItem(res: ScanResult, onTap: (ScanResult) -> Unit) {
    Card(
        onClick = { onTap(res) },
        modifier = Modifier.fillMaxWidth(),
        shape = ShapeMedium,
        colors = CardDefaults.cardColors(
            containerColor = if (res.isWorking) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else 
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (res.isWorking) Color(0xFF4CAF50)
                        else MaterialTheme.colorScheme.outline
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (res.isWorking) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    res.subdomain,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                // Show target host if available
                if (res.targetHost.isNotEmpty() && res.isWorking) {
                    Text(
                        "→ ${res.targetHost}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (res.isWorking) "${res.ip} ${if (res.isCloudflare) "CF" else ""}"
                        else res.errorMsg ?: "Failed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (res.isWorking && res.latency > 0) {
                        Text(
                            "  •  ${res.latency}ms",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            
            if (res.isCloudflare && res.isWorking) {
                Surface(
                    shape = ShapeSmall,
                    color = Color(0xFFFF6B35).copy(alpha = 0.15f)
                ) {
                    Text(
                        "CF",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Color(0xFFFF6B35)
                    )
                }
            }
        }
    }
}

@Composable
fun ModernAlertDialog(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = ShapeExtraLarge,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = iconTint
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = ShapeLarge
                ) {
                    Text(confirmText)
                }
            }
        }
    }
}
