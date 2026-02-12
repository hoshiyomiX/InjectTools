package com.hoshiyomix.injecttools

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import com.hoshiyomix.injecttools.Scanner.ScanResult

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay


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

// Material 3 Shape constants
val ShapeExtraLarge = RoundedCornerShape(28.dp)
val ShapeLarge = RoundedCornerShape(24.dp)
val ShapeMedium = RoundedCornerShape(16.dp)
val ShapeSmall = RoundedCornerShape(12.dp)

// Animation specs
val smoothSpring = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
val fastSpring = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
val tweenSpec = tween<Float>(durationMillis = 300, easing = FastOutSlowInEasing)

// Pulsating animation for icons
@Composable
fun pulsatingAnimation(): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "pulsating")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    return scale
}

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
        ModernAlertDialog(
            icon = Icons.Default.Warning,
            iconTint = MaterialTheme.colorScheme.error,
            title = "Network Connection Warning",
            message = networkWarningMessage,
            confirmText = "OK",
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
            AnimatedVisibility(
                visible = currentScreen != Screen.MENU,
                enter = fadeIn(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(250))
            ) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = when (currentScreen) {
                                Screen.SINGLE_TEST -> "Test Subdomain"
                                Screen.CRTSH_TEST -> "Batch Scan"
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
            // Simple crossfade for screen transitions
            Crossfade(
                targetState = currentScreen,
                animationSpec = tween(350, easing = FastOutSlowInEasing),
                label = "ScreenTransition"
            ) { screen ->
                when (screen) {
                    Screen.MENU -> MenuScreen(
                        targetHost = targetHost,
                        onUpdateHost = { saveTargetHost(it) },
                        onNavigate = { s ->
                            if ((s == Screen.SINGLE_TEST || s == Screen.CRTSH_TEST) && targetHost.isBlank()) {
                                Toast.makeText(context, "⚠️ Set target host first!", Toast.LENGTH_SHORT).show()
                            } else {
                                navigateTo(s)
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
    val pulsateScale = pulsatingAnimation()

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
                    // Animated icon with pulsating effect
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .scale(pulsateScale)
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
                        "Set your injection target domain to get started",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

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
                        shape = ShapeMedium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    AnimatedButton(
                        onClick = {
                            if (hostInput.isNotBlank()) {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                onConfirm(hostInput)
                            }
                        },
                        enabled = hostInput.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Icon(Icons.Default.ArrowForward, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Get Started", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimatedButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessHigh),
        label = "buttonScale"
    )

    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.scale(scale),
        shape = ShapeLarge,
        interactionSource = interactionSource
    ) {
        content()
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
        MenuTile(1, "Test Subdomain", "Test single subdomain connection", Icons.Outlined.Search, Pair(Color(0xFF6750A4), Color(0xFF9A82DB)), Screen.SINGLE_TEST),
        MenuTile(2, "Batch Scan", "Discover & test from crt.sh", Icons.Outlined.Hub, Pair(Color(0xFFD81B60), Color(0xFFFF6F00)), Screen.CRTSH_TEST),
        MenuTile(3, "History", "View scan results", Icons.Outlined.History, Pair(Color(0xFF00695C), Color(0xFF4DB6AC)), Screen.RESULTS)
    )

    var showHostDialog by remember { mutableStateOf(false) }
    var tempHost by remember { mutableStateOf(targetHost) }
    
    // Staggered animation for menu items
    val visibleItems = remember { mutableStateListOf<Boolean>() }
    LaunchedEffect(Unit) {
        tiles.indices.forEach { index ->
            delay(80L * index)
            visibleItems.add(true)
        }
    }
    
    val pulsateScale = pulsatingAnimation()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Modern Header Card with animated gradient
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
                    // App Logo with pulsating animation
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .scale(pulsateScale)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Bolt,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "InjectTools",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "v3.7.0",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Domain Host Card with press animation
                    AnimatedCard(
                        onClick = {
                            tempHost = targetHost
                            showHostDialog = true
                        }
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
                                    text = "Target Host",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = targetHost.ifBlank { "Tap to set domain" },
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

        // Section Header with animation
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Features",
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

        // Menu Tiles with staggered animation
        tiles.forEachIndexed { index, tile ->
            AnimatedVisibility(
                visible = visibleItems.size > index,
                enter = fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing))
            ) {
                ModernMenuTileCard(
                    tile = tile, 
                    enabled = !showHostDialog,
                    index = index
                ) {
                    if (!showHostDialog) {
                        tile.screen?.let(onNavigate)
                    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimatedCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessHigh),
        label = "cardScale"
    )

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .scale(scale),
        shape = ShapeLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        interactionSource = interactionSource
    ) {
        content()
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
                    "Set Target Host",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "Enter the domain host for injection target",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

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
                        shape = ShapeMedium,
                        leadingIcon = {
                            Icon(Icons.Outlined.Dns, contentDescription = null)
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
                            Text("Cancel")
                        }
                        AnimatedButton(
                            onClick = { 
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                onConfirm() 
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save")
                        }
                    }
                }
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernMenuTileCard(
    tile: MenuTile, 
    enabled: Boolean = true,
    index: Int = 0,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessHigh),
        label = "tileScale"
    )
    
    val elevation by animateDpAsState(
        targetValue = if (isPressed) 2.dp else 0.dp,
        animationSpec = tween(200),
        label = "tileElevation"
    )

    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .scale(scale),
        shape = ShapeLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        interactionSource = interactionSource
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon Container with gradient
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
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100)
        visible = true
    }
    
    if (history.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(300))
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val pulsateScale = pulsatingAnimation()
                    
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .scale(pulsateScale)
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
                        "No scan results yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Results will appear here after scanning",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(animationSpec = tween(300))
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = ShapeLarge,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.List,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    "Recent Results",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "${history.size} successful scans",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }

            items(history.take(20)) { res ->
                AnimatedResultItem(res, onTap = {})
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
        NetworkUtils.NetworkStatus.INTERNET_NO_VPN -> showWarningDialog("❌ Regular internet detected! Disable WiFi/Data or use injection mode.")
        NetworkUtils.NetworkStatus.NO_INTERNET_VPN -> showWarningDialog("❌ VPN is active! Please disable VPN before scanning.")
        NetworkUtils.NetworkStatus.INTERNET_VPN -> showWarningDialog("❌ VPN + Internet detected! Disable both VPN and regular connection.")
        NetworkUtils.NetworkStatus.DISCONNECTED -> showWarningDialog("❌ No network connection. Connect to WiFi/Data first.")
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
    
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100)
        visible = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        // Header Card with animation
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(300))
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = ShapeLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            "Test Subdomain",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Test individual subdomain connection",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Input Section with animation
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(300, delayMillis = 100))
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = ShapeLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    OutlinedTextField(
                        value = subdomain,
                        onValueChange = { subdomain = it },
                        label = { Text("Subdomain") },
                        placeholder = { Text("api.instagram.com") },
                        leadingIcon = { 
                            Icon(Icons.Outlined.Language, contentDescription = null)
                        },
                        trailingIcon = {
                            if (subdomain.isNotBlank()) {
                                IconButton(onClick = { subdomain = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = ShapeMedium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    AnimatedButton(
                        onClick = {
                            if (isScanning || subdomain.isBlank()) return@AnimatedButton
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Scanning...")
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Test Connection")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Results Section with animation
        AnimatedVisibility(
            visible = recentResults.isNotEmpty(),
            enter = fadeIn(animationSpec = tween(300))
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Recent Results",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Divider(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                recentResults.take(10).forEach { result ->
                    AnimatedResultItem(result, onTap = { subdomain = result.subdomain })
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100)
        visible = true
    }
    
    // Animated progress
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "progressAnim"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        // Header Card with animation
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(300))
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = ShapeLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.Hub,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            "Batch Scan",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Discover & test subdomains from crt.sh",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Input Card with animation
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(300, delayMillis = 100))
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = ShapeLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    OutlinedTextField(
                        value = domain,
                        onValueChange = { domain = it },
                        label = { Text("Domain") },
                        placeholder = { Text("cloudflare.com") },
                        leadingIcon = { Icon(Icons.Outlined.Language, contentDescription = null) },
                        trailingIcon = {
                            if (domain.isNotBlank()) {
                                IconButton(onClick = { domain = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = ShapeMedium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AnimatedButton(
                            onClick = {
                                isFetching = true
                                scanResults = emptyList()
                                fetchSummary = ""
                                scope.launch {
                                    try {
                                        subdomains = Crtsh.fetchSubdomains(domain.trim())
                                        lastFetchedDomain = domain.trim()
                                        fetchSummary = "Found ${subdomains.size} subdomains from crt.sh"
                                    } catch (e: Exception) {
                                        subdomains = emptyList()
                                        fetchSummary = "Failed: ${e.message}"
                                    }
                                    isFetching = false
                                }
                            },
                            enabled = !isFetching && !isScanning && domain.isNotBlank() && !(subdomains.isNotEmpty() && lastFetchedDomain == domain.trim()),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                        ) {
                            if (isFetching) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Download, contentDescription = null)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isFetching) "Fetching..." else "Fetch")
                        }

                        AnimatedButton(
                            onClick = {
                                if (isScanning || subdomains.isEmpty()) return@AnimatedButton
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
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isScanning) "Scanning" else if (scanResults.isNotEmpty() && lastFetchedDomain == domain.trim()) "Re-scan" else "Scan All")
                        }
                    }
                }
            }
        }

        // Progress indicator with animation
        AnimatedVisibility(
            visible = isScanning,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(200))
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = ShapeMedium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Progress",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            "${(animatedProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = animatedProgress,
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        trackColor = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.2f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "${scanResults.size} / ${subdomains.size} subdomains",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Summary Card with animation
        AnimatedVisibility(
            visible = fetchSummary.isNotEmpty(),
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(200))
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = ShapeMedium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = fetchSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Results with animation
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(scanResults.take(20).filter { it.isWorking }) { res ->
                AnimatedResultItem(res, onTap = {})
            }
        }
    }

    // Back handler
    BackHandler(enabled = isFetching || isScanning) {
        showBackWarningDialog = true
    }

    if (showBackWarningDialog) {
        ModernAlertDialog(
            icon = Icons.Default.Warning,
            iconTint = MaterialTheme.colorScheme.error,
            title = "Operation in Progress",
            message = "Fetch or scan is still running. Going back will cancel the current operation. Continue?",
            confirmText = "Yes, Go Back",
            dismissText = "Cancel",
            onConfirm = {
                showBackWarningDialog = false
                isFetching = false
                isScanning = false
            },
            onDismiss = {
                showBackWarningDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimatedResultItem(res: ScanResult, onTap: (ScanResult) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessHigh),
        label = "resultScale"
    )
    
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(50)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(200))
    ) {
        Card(
            onClick = { onTap(res) },
            modifier = Modifier
                .fillMaxWidth()
                .scale(scale),
            shape = ShapeMedium,
            colors = CardDefaults.cardColors(
                containerColor = if (res.isWorking) 
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else 
                    MaterialTheme.colorScheme.surfaceVariant
            ),
            interactionSource = interactionSource
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Animated success/error icon
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (res.isWorking) 
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            else 
                                MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (res.isWorking) Icons.Default.CheckCircle else Icons.Default.Cancel,
                        contentDescription = null,
                        tint = if (res.isWorking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = res.subdomain,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = res.ip.ifBlank { "No IP resolved" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    res.errorMsg?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                if (res.isCloudflare) {
                    Surface(
                        shape = ShapeSmall,
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Text(
                            "CF",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
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
    confirmText: String = "OK",
    dismissText: String? = null,
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
                        .background(iconTint.copy(alpha = 0.1f)),
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
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    dismissText?.let {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            shape = ShapeLarge
                        ) {
                            Text(it)
                        }
                    }
                    AnimatedButton(
                        onClick = onConfirm,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) {
                        Text(confirmText)
                    }
                }
            }
        }
    }
}
