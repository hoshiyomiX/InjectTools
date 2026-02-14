package com.hoshiyomix.injecttools

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import com.hoshiyomix.injecttools.Scanner.ScanResult

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
    
    // Load history from file storage on app start (persists across app restarts)
    var scanHistory by remember { mutableStateOf(HistoryStorage.loadHistory(context)) }

    var showFirstRunDialog by remember { mutableStateOf(prefs.getBoolean("first_run", true)) }
    var showNetworkWarningDialog by remember { mutableStateOf(false) }
    var networkWarningMessage by remember { mutableStateOf("") }

    fun saveTargetHost(host: String) {
        targetHost = host
        prefs.edit().putString("target_host", host).apply()
    }

    fun addResults(newResults: List<ScanResult>) {
        val successResults = newResults.filter { it.isWorking }
        if (successResults.isEmpty()) return
        
        val combined = (successResults + scanHistory.filter { it.isWorking }).take(20)
        scanHistory = combined
        
        // Persist history to file storage
        HistoryStorage.saveHistory(context, combined)
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
            title = "Peringatan Koneksi",
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
                                Screen.CRTSH_TEST -> "Scan Massal"
                                Screen.RESULTS -> "Riwayat"
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
                            Toast.makeText(context, "Set host dulu bos!", Toast.LENGTH_SHORT).show()
                        } else {
                            navigateTo(s)
                        }
                    }
                )
                Screen.SINGLE_TEST -> ManualScanScreen(targetHost, onResult = { addResults(listOf(it)) }, onShowNetworkWarning = { message ->
                    networkWarningMessage = message
                    showNetworkWarningDialog = true
                })
                Screen.CRTSH_TEST -> CrtshScanScreen(targetHost, onResults = { addResults(it) }, onNavigateToHistory = { currentScreen = Screen.RESULTS }, onShowNetworkWarning = { message ->
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
                    "Selamat Datang di InjectTools",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "Set host target inject dulu baru mulai",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = hostInput,
                    onValueChange = { hostInput = it },
                    label = { Text("Host Target Inject") },
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
        MenuTile(2, "Scan Massal", "Cari & test bug dari crt.sh", Icons.Outlined.Hub, Pair(Color(0xFFD81B60), Color(0xFFFF6F00)), Screen.CRTSH_TEST),
        MenuTile(3, "Riwayat", "Liat hasil scan", Icons.Outlined.History, Pair(Color(0xFF00695C), Color(0xFF4DB6AC)), Screen.RESULTS)
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
                        text = "v1.2.6",
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
                        shape = ShapeLarge,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
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
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Outlined.Dns,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Host Target",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
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
                                modifier = Modifier.size(20.dp), 
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
                text = "Fitur",
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
                    "Set Host Target",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "Masukin host buat target inject",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = currentHost,
                    onValueChange = onHostChange,
                    label = { Text("Host Domain") },
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
fun ResultHistoryScreen(history: List<ScanResult>) {
    if (history.isEmpty()) {
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
                    "Belum ada hasil scan",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Hasil scan nanti muncul di sini",
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
                            .padding(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.List,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                "Hasil Terbaru",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "${history.size} bug work",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            items(history.take(20)) { res ->
                ResultItem(res, onTap = {})
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
        NetworkUtils.NetworkStatus.INTERNET_NO_VPN -> showWarningDialog("Ketemu internet biasa! Matiin WiFi/Data atau pake mode inject.")
        NetworkUtils.NetworkStatus.NO_INTERNET_VPN -> showWarningDialog("VPN nyala nih! Matiin dulu VPN baru scan.")
        NetworkUtils.NetworkStatus.INTERNET_VPN -> showWarningDialog("VPN + Internet ke-detek! Matiin keduanya.")
        NetworkUtils.NetworkStatus.DISCONNECTED -> showWarningDialog("Kagak ada koneksi. Sambungin WiFi/Data dulu.")
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
                    "Masukin Bug",
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
                        Text("Lagi scan...")
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
                "Hasil",
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
fun CrtshScanScreen(targetHost: String, onResults: (List<ScanResult>) -> Unit, onNavigateToHistory: () -> Unit, onShowNetworkWarning: (String) -> Unit) {
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

    // Helper function to start testing phase
    fun startTesting(subdomains: List<String>) {
        isScanning = true
        progress = 0.1f // Start from 10% (after fetch phase)
        results = emptyList()
        statusText = "Ketemu ${subdomains.size} bug. Lagi test..."
        
        scope.launch {
            val scanResults = mutableListOf<ScanResult>()
            subdomains.forEachIndexed { index, sub ->
                val result = Scanner.testSingle(targetHost, sub)
                scanResults.add(result)
                // Real-time update: show results as they come in
                results = scanResults.toList()
                onResults(scanResults.toList())
                // Test phase: 10% - 100%
                progress = 0.1f + (0.9f * (index + 1) / subdomains.size)
                statusText = "Test ${index + 1}/${subdomains.size}: $sub (${scanResults.count { it.isWorking }} work)"
            }
            
            statusText = "Selesai! ${scanResults.count { it.isWorking }} bug work ketemu"
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
                    "Scan Massal via crt.sh",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Ambil bug dari log sertifikat transparansi",
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
                            // STEP 1: Check if we have internet for fetching from crt.sh
                            if (!NetworkUtils.hasInternetConnection(context)) {
                                onShowNetworkWarning("Kagak ada internet. Sambungin WiFi/Data buat ambil bug dari crt.sh.")
                                return@Button
                            }
                            
                            // STEP 2: Fetch from crt.sh (requires internet)
                            isFetching = true
                            progress = 0f
                            results = emptyList()
                            statusText = "Nyambung ke crt.sh..."
                            
                            scope.launch {
                                val subdomains = Crtsh.fetchSubdomains(
                                    domain = domain,
                                    onProgress = { fetchProgress ->
                                        // Update UI with fetch progress
                                        statusText = fetchProgress.phase
                                        progress = fetchProgress.progress * 0.1f // Scale to 0-10% for fetch phase
                                    }
                                )
                                isFetching = false
                                
                                if (subdomains.isEmpty()) {
                                    statusText = "Kagak ketemu bug buat $domain"
                                    progress = 1f
                                } else {
                                    // STEP 3: Store subdomains and show confirmation dialog
                                    pendingSubdomains = subdomains
                                    statusText = "Ketemu ${subdomains.size} bug valid"
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
                        Text("Ngambil...")
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
                        Text("Mulai Scan Massal")
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
                                "Ganti ke mode inject, terus tap coba lagi",
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
                                    startTesting(pendingSubdomains)
                                }, onShowNetworkWarning)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = ShapeMedium
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Coba Lagi")
                        }
                    }
                }
            }
        }

        // Results
        if (results.isNotEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))
            val workingResults = results.filter { it.isWorking }
            val maxDisplay = 5
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
                    "${workingResults.size} work",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                items(workingResults.take(maxDisplay)) { res ->
                    ResultItem(res, onTap = {})
                }
                
                // Show "View All in History" button if more than maxDisplay
                if (hasMore) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onNavigateToHistory,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = ShapeMedium
                        ) {
                            Icon(Icons.Default.History, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Liat Semua ${workingResults.size} Hasil di Riwayat")
                        }
                    }
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
                    startTesting(pendingSubdomains)
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
                    "Mulai Test?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "Ketemu $subdomainCount bug siap dites.\n\nPastikan udah di mode inject:\n• Matiin WiFi/Data ATAU\n• Pake config inject",
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
                        Text("Mulai Test")
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
                        if (res.isWorking) MaterialTheme.colorScheme.primary
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
                Text(
                    if (res.isWorking) "${res.ip} ${if (res.isCloudflare) "CF" else ""}"
                    else res.errorMsg ?: "Failed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (res.isCloudflare && res.isWorking) {
                Surface(
                    shape = ShapeSmall,
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Text(
                        "CF",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onTertiaryContainer
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
