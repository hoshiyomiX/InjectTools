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
                output.fd.sync()
            }

            // Atomic rename (POSIX guarantees this is atomic)
            val success = tempFile.renameTo(file)

            if (!success) {
                // Fallback: copy content if rename fails
                tempFile.copyTo(file, overwrite = true)
                tempFile.delete()
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save history: ${e.message}")
            false
        }
    }

    /**
     * Load history from internal storage file.
     * Uses Array instead of List for ProGuard/R8 compatibility.
     */
    fun loadHistory(context: Context): List<Scanner.ScanResult> {
        return try {
            val file = File(context.filesDir, FILE_NAME)

            if (!file.exists()) return emptyList()

            val json = FileInputStream(file).use { it.readBytes().toString(Charsets.UTF_8) }

            if (json.isBlank()) return emptyList()

            // Use Array instead of List - more reliable with ProGuard/R8
            val result: Array<Scanner.ScanResult>? = gson.fromJson(json, Array<Scanner.ScanResult>::class.java)
            result?.toList() ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load history: ${e.message}")
            emptyList()
        }
    }

    /**
     * Clear history file.
     */
    fun clearHistory(context: Context): Boolean {
        return try {
            val file = File(context.filesDir, FILE_NAME)
            if (file.exists()) file.delete()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear history: ${e.message}")
            false
        }
    }
}
