package com.hoshiyomix.injecttools

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Reliable history storage using internal file storage.
 * More reliable than SharedPreferences for complex data structures.
 */
object HistoryStorage {
    private const val TAG = "HistoryStorage"
    private const val FILE_NAME = "scan_history.json"
    private val gson = Gson()

    /**
     * Save history to internal storage file.
     * Uses atomic write (write to temp, then rename) for reliability.
     */
    fun saveHistory(context: Context, history: List<Scanner.ScanResult>): Boolean {
        return try {
            val json = gson.toJson(history)
            val file = File(context.filesDir, FILE_NAME)
            val tempFile = File(context.filesDir, "$FILE_NAME.tmp")

            // Write to temp file first
            FileOutputStream(tempFile).use { output ->
                output.write(json.toByteArray(Charsets.UTF_8))
                output.flush()
                // Force sync to disk
                output.fd.sync()
            }

            // Atomic rename (POSIX guarantees this is atomic)
            val success = tempFile.renameTo(file)

            if (success) {
                Log.d(TAG, "Successfully saved ${history.size} items to ${file.absolutePath}")
                Log.d(TAG, "File size: ${file.length()} bytes")
            } else {
                // Fallback: copy content if rename fails
                tempFile.copyTo(file, overwrite = true)
                tempFile.delete()
                Log.d(TAG, "Saved via copy: ${history.size} items")
            }

            // Verify the save
            val verifyJson = FileInputStream(file).use { it.readBytes().toString(Charsets.UTF_8) }
            Log.d(TAG, "Verification: file contains ${verifyJson.length} chars")

            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save history: ${e.message}", e)
            false
        }
    }

    /**
     * Load history from internal storage file with debug info.
     */
    fun loadHistoryDebug(context: Context): Pair<List<Scanner.ScanResult>, String> {
        return try {
            val file = File(context.filesDir, FILE_NAME)

            if (!file.exists()) {
                Log.d(TAG, "No history file found at ${file.absolutePath}")
                return Pair(emptyList(), "FILE NOT FOUND")
            }

            Log.d(TAG, "Loading from ${file.absolutePath}, size: ${file.length()} bytes")

            val json = FileInputStream(file).use { it.readBytes().toString(Charsets.UTF_8) }

            if (json.isBlank()) {
                Log.d(TAG, "History file is empty")
                return Pair(emptyList(), "FILE EMPTY")
            }

            Log.d(TAG, "=== JSON CONTENT ===")
            Log.d(TAG, json)
            Log.d(TAG, "=== END JSON ===")

            // Use Array instead of List - more reliable with ProGuard/R8
            val result: Array<Scanner.ScanResult> = gson.fromJson(json, Array<Scanner.ScanResult>::class.java)
                ?: return Pair(emptyList(), "NULL RESULT")

            Log.d(TAG, "Parsed ${result.size} items successfully")
            Pair(result.toList(), "OK: ${result.size} items")
        } catch (e: Exception) {
            Log.e(TAG, "=== PARSE ERROR ===")
            Log.e(TAG, "Error: ${e.message}")
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            e.printStackTrace()
            Pair(emptyList(), "ERROR: ${e.message}")
        }
    }

    /**
     * Load history from internal storage file.
     */
    fun loadHistory(context: Context): List<Scanner.ScanResult> {
        return loadHistoryDebug(context).first
    }

    /**
     * Clear history file.
     */
    fun clearHistory(context: Context): Boolean {
        return try {
            val file = File(context.filesDir, FILE_NAME)
            if (file.exists()) {
                file.delete()
            }
            Log.d(TAG, "History cleared")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear history: ${e.message}")
            false
        }
    }

    /**
     * Debug: Get file info
     */
    fun getFileInfo(context: Context): String {
        val file = File(context.filesDir, FILE_NAME)
        return if (file.exists()) {
            "EXISTS: ${file.length()} bytes"
        } else {
            "NO FILE"
        }
    }
    
    /**
     * Debug: Get raw JSON content (for debugging)
     */
    fun getRawJson(context: Context): String {
        return try {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) return "NO FILE"
            FileInputStream(file).use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }
    
    /**
     * Debug: Get full file path
     */
    fun getFilePath(context: Context): String {
        val file = File(context.filesDir, FILE_NAME)
        return file.absolutePath
    }
}
