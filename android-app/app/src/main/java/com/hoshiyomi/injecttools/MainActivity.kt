package com.hoshiyomi.injecttools

import android.Manifest
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.hoshiyomi.injecttools.core.LogManager
import com.hoshiyomi.injecttools.ui.InjectToolsApp
import com.hoshiyomi.injecttools.ui.theme.InjectToolsTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        LogManager.i("MainActivity", "Storage permission granted: $isGranted")
        if (!isGranted) {
            LogManager.w("MainActivity", "Storage permission denied, using app-specific storage")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        LogManager.i("MainActivity", "onCreate called")
        
        // Install global crash handler FIRST
        setupGlobalCrashHandler()
        
        // Enable StrictMode in debug builds
        if (isDebugMode()) {
            setupStrictMode()
        }
        
        // Request storage permission for Android 10 and below
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                LogManager.i("MainActivity", "Requesting storage permission")
                permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                LogManager.i("MainActivity", "Storage permission already granted")
            }
        } else {
            LogManager.i("MainActivity", "Android 11+, no storage permission needed")
        }
        
        setContent {
            InjectToolsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    InjectToolsApp()
                }
            }
        }
        
        LogManager.i("MainActivity", "UI setup completed")
    }
    
    private fun isDebugMode(): Boolean {
        return (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
    
    private fun setupGlobalCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                LogManager.e("CRASH", "Uncaught exception on thread: ${thread.name}", throwable)
                
                // Save to file before app dies
                val crashDir = File("/sdcard/InjectTools")
                if (!crashDir.exists()) {
                    crashDir.mkdirs()
                }
                
                val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
                val crashFile = File(crashDir, "crash_$timestamp.log")
                
                val crashReport = buildString {
                    appendLine("=== InjectTools Crash Report ===")
                    appendLine("Time: $timestamp")
                    appendLine("Thread: ${thread.name}")
                    appendLine("Exception: ${throwable.javaClass.name}")
                    appendLine("Message: ${throwable.message}")
                    appendLine("\nStack Trace:")
                    appendLine(throwable.stackTraceToString())
                    appendLine("\nCaused by:")
                    var cause = throwable.cause
                    while (cause != null) {
                        appendLine("  ${cause.javaClass.name}: ${cause.message}")
                        cause.stackTrace.take(5).forEach { 
                            appendLine("    at $it")
                        }
                        cause = cause.cause
                    }
                }
                
                crashFile.writeText(crashReport)
                LogManager.i("CRASH", "Crash log saved to: ${crashFile.absolutePath}")
                
            } catch (e: Exception) {
                // If crash handler itself crashes, at least try to log it
                android.util.Log.e("CRASH_HANDLER", "Failed to save crash log", e)
            } finally {
                // Call original handler
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
        
        LogManager.i("MainActivity", "Global crash handler installed")
    }
    
    private fun setupStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build()
        )
        
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build()
        )
        
        LogManager.i("MainActivity", "StrictMode enabled (debug build)")
    }
}
