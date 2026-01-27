package com.hoshiyomi.injecttools

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
}
