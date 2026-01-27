package com.hoshiyomi.injecttools

import android.app.Application
import com.hoshiyomi.injecttools.core.LogManager

class InjectToolsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize LogManager with file logging
        LogManager.init(this)
        LogManager.i("Application", "InjectTools started")
        LogManager.i("Application", "Version: 4.0.0-alpha")
        LogManager.i("Application", "Android SDK: ${android.os.Build.VERSION.SDK_INT}")
    }
}
