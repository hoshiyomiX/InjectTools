package com.hoshiyomi.injecttools

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.hoshiyomi.injecttools.ui.InjectToolsApp
import com.hoshiyomi.injecttools.ui.theme.InjectToolsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // NOTE: No JNI loading needed - using pure Kotlin implementation
        // Original: System.loadLibrary("injecttools")
        
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
    }
}
