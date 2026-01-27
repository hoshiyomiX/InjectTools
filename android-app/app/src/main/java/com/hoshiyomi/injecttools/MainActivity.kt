package com.hoshiyomi.injecttools

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.activity.ComponentActivity
// import androidx.activity.compose.setContent
// import androidx.compose.material3.Text
// import com.hoshiyomi.injecttools.ui.theme.InjectToolsTheme

/**
 * MINIMAL TEST VERSION - For crash diagnosis
 * 
 * This version progressively tests each component:
 * 1. Activity lifecycle
 * 2. Android logging
 * 3. Simple View inflation
 * 4. Compose UI (commented out)
 * 
 * Monitor with: adb logcat | grep MINIMAL_TEST
 */
class MainActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "MINIMAL_TEST"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.e(TAG, "============================================")
        Log.e(TAG, "=== MINIMAL TEST START ===")
        Log.e(TAG, "============================================")
        
        try {
            Log.e(TAG, "[1/6] Calling super.onCreate()...")
            super.onCreate(savedInstanceState)
            Log.e(TAG, "[1/6] ✅ super.onCreate() SUCCESS")
            
            Log.e(TAG, "[2/6] Testing basic Kotlin operations...")
            val testString = "Hello Kotlin"
            val testInt = 123
            val testList = listOf(1, 2, 3)
            Log.e(TAG, "[2/6] ✅ Kotlin works: $testString, $testInt, ${testList.size} items")
            
            Log.e(TAG, "[3/6] Testing Android logging infrastructure...")
            Log.v(TAG, "Verbose log works")
            Log.d(TAG, "Debug log works")
            Log.i(TAG, "Info log works")
            Log.w(TAG, "Warning log works")
            Log.e(TAG, "[3/6] ✅ All log levels work")
            
            Log.e(TAG, "[4/6] Testing View inflation...")
            val textView = TextView(this).apply {
                text = "InjectTools Minimal Test\n\nIf you see this, basic Activity works!\n\nCheck logcat for details."
                textSize = 16f
                setPadding(32, 32, 32, 32)
            }
            Log.e(TAG, "[4/6] ✅ TextView created")
            
            Log.e(TAG, "[5/6] Setting content view...")
            setContentView(textView)
            Log.e(TAG, "[5/6] ✅ setContentView SUCCESS")
            
            Log.e(TAG, "[6/6] Testing thread info...")
            Log.e(TAG, "  Thread: ${Thread.currentThread().name}")
            Log.e(TAG, "  Thread ID: ${Thread.currentThread().id}")
            Log.e(TAG, "  Is main thread: ${Thread.currentThread() == android.os.Looper.getMainLooper().thread}")
            Log.e(TAG, "[6/6] ✅ Thread info retrieved")
            
            Log.e(TAG, "")
            Log.e(TAG, "============================================")
            Log.e(TAG, "=== ✅✅✅ MINIMAL TEST SUCCESS ✅✅✅ ===")
            Log.e(TAG, "============================================")
            Log.e(TAG, "")
            Log.e(TAG, "Next steps:")
            Log.e(TAG, "1. If you see this, basic Activity works fine")
            Log.e(TAG, "2. Uncomment Compose code to test Jetpack Compose")
            Log.e(TAG, "3. Restore original MainActivity from .backup file")
            Log.e(TAG, "")
            
        } catch (e: Exception) {
            Log.e(TAG, "")
            Log.e(TAG, "============================================")
            Log.e(TAG, "=== ❌❌❌ CRASH DETECTED ❌❌❌ ===")
            Log.e(TAG, "============================================")
            Log.e(TAG, "Exception: ${e.javaClass.name}")
            Log.e(TAG, "Message: ${e.message}")
            Log.e(TAG, "Stack trace:")
            e.stackTrace.forEach { element ->
                Log.e(TAG, "  at $element")
            }
            Log.e(TAG, "============================================")
            throw e
        }
    }
    
    override fun onStart() {
        super.onStart()
        Log.e(TAG, "[LIFECYCLE] onStart() called")
    }
    
    override fun onResume() {
        super.onResume()
        Log.e(TAG, "[LIFECYCLE] onResume() called")
        Log.e(TAG, "[LIFECYCLE] Activity is now visible and interactive")
    }
    
    override fun onPause() {
        Log.e(TAG, "[LIFECYCLE] onPause() called")
        super.onPause()
    }
    
    override fun onStop() {
        Log.e(TAG, "[LIFECYCLE] onStop() called")
        super.onStop()
    }
    
    override fun onDestroy() {
        Log.e(TAG, "[LIFECYCLE] onDestroy() called")
        super.onDestroy()
    }
}

/* 
 * PHASE 2: Uncomment this to test Compose
 * 
 * Replace the setContentView(textView) above with:
 * 
 * setContent {
 *     InjectToolsTheme {
 *         Text("Compose Test")
 *     }
 * }
 */
