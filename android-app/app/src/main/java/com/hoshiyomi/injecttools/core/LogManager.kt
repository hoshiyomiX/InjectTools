package com.hoshiyomi.injecttools.core

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

object LogManager {
    private const val TAG = "InjectTools"
    private const val MAX_LOGS = 1000
    
    private val logs = ConcurrentLinkedQueue<LogEntry>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val fileDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    
    private var logFile: File? = null
    private var fileWriter: FileWriter? = null
    
    data class LogEntry(
        val timestamp: Long,
        val level: Level,
        val tag: String,
        val message: String,
        val throwable: Throwable? = null
    ) {
        fun format(): String {
            val time = dateFormat.format(Date(timestamp))
            val throwableStr = throwable?.let { "\n${it.stackTraceToString()}" } ?: ""
            return "[$time] ${level.name}/$tag: $message$throwableStr"
        }
        
        fun formatForFile(): String {
            val time = fileDateFormat.format(Date(timestamp))
            val throwableStr = throwable?.let { " | Exception: ${it.message}\n${it.stackTraceToString()}" } ?: ""
            return "$time | ${level.name} | $tag | $message$throwableStr"
        }
    }
    
    enum class Level {
        VERBOSE, DEBUG, INFO, WARN, ERROR
    }
    
    fun init(context: Context) {
        try {
            val logDir = File(Environment.getExternalStorageDirectory(), "InjectTools")
            if (!logDir.exists()) {
                logDir.mkdirs()
            }
            
            logFile = File(logDir, "debug.log")
            
            // Clear old log
            if (logFile!!.exists()) {
                logFile!!.delete()
            }
            
            fileWriter = FileWriter(logFile, true)
            fileWriter?.write("\n=== InjectTools Debug Session Started ===\n")
            fileWriter?.write("Time: ${fileDateFormat.format(Date())}\n")
            fileWriter?.write("Path: ${logFile!!.absolutePath}\n")
            fileWriter?.write("==========================================\n\n")
            fileWriter?.flush()
            
            Log.i(TAG, "File logging enabled: ${logFile!!.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init file logging: ${e.message}")
        }
    }
    
    fun v(tag: String, message: String) = log(Level.VERBOSE, tag, message)
    fun d(tag: String, message: String) = log(Level.DEBUG, tag, message)
    fun i(tag: String, message: String) = log(Level.INFO, tag, message)
    fun w(tag: String, message: String) = log(Level.WARN, tag, message)
    fun e(tag: String, message: String, throwable: Throwable? = null) = 
        log(Level.ERROR, tag, message, throwable)
    
    private fun log(level: Level, tag: String, message: String, throwable: Throwable? = null) {
        try {
            val entry = LogEntry(
                timestamp = System.currentTimeMillis(),
                level = level,
                tag = tag,
                message = message,
                throwable = throwable
            )
            
            // Add to memory queue
            logs.offer(entry)
            while (logs.size > MAX_LOGS) {
                logs.poll()
            }
            
            // Write to Android logcat
            when (level) {
                Level.VERBOSE -> Log.v(TAG, "$tag: $message", throwable)
                Level.DEBUG -> Log.d(TAG, "$tag: $message", throwable)
                Level.INFO -> Log.i(TAG, "$tag: $message", throwable)
                Level.WARN -> Log.w(TAG, "$tag: $message", throwable)
                Level.ERROR -> Log.e(TAG, "$tag: $message", throwable)
            }
            
            // Write to file immediately (live logging)
            try {
                fileWriter?.write(entry.formatForFile() + "\n")
                fileWriter?.flush()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write to file: ${e.message}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "LogManager failed: ${e.message}")
        }
    }
    
    fun getAllLogs(): String {
        return try {
            val sb = StringBuilder()
            sb.appendLine("=== InjectTools Debug Logs ===")
            sb.appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
            sb.appendLine("Total entries: ${logs.size}")
            if (logFile != null && logFile!!.exists()) {
                sb.appendLine("File: ${logFile!!.absolutePath}")
            }
            sb.appendLine("")
            
            logs.forEach { entry ->
                sb.appendLine(entry.format())
            }
            
            sb.toString()
        } catch (e: Exception) {
            "Failed to generate logs: ${e.message}"
        }
    }
    
    fun copyToClipboard(context: Context) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("InjectTools Logs", getAllLogs())
            clipboard.setPrimaryClip(clip)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy logs: ${e.message}")
        }
    }
    
    fun clear() {
        logs.clear()
        try {
            fileWriter?.write("\n=== LOGS CLEARED ===\n\n")
            fileWriter?.flush()
        } catch (e: Exception) {}
        i("LogManager", "Logs cleared")
    }
    
    fun getLogCount() = logs.size
    
    fun getLogFilePath(): String? = logFile?.absolutePath
}
