package com.hoshiyomi.injecttools.core

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

object LogManager {
    private const val TAG = "InjectTools"
    private const val MAX_LOGS = 500
    
    private val logs = ConcurrentLinkedQueue<LogEntry>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    
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
    }
    
    enum class Level {
        VERBOSE, DEBUG, INFO, WARN, ERROR
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
            
            logs.offer(entry)
            
            while (logs.size > MAX_LOGS) {
                logs.poll()
            }
            
            when (level) {
                Level.VERBOSE -> Log.v(TAG, "$tag: $message", throwable)
                Level.DEBUG -> Log.d(TAG, "$tag: $message", throwable)
                Level.INFO -> Log.i(TAG, "$tag: $message", throwable)
                Level.WARN -> Log.w(TAG, "$tag: $message", throwable)
                Level.ERROR -> Log.e(TAG, "$tag: $message", throwable)
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
        i("LogManager", "Logs cleared")
    }
    
    fun getLogCount() = logs.size
}
