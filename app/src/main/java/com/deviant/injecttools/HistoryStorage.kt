package com.deviant.injecttools

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Scan session containing results from a single mass scan operation.
 */
data class ScanSession(
    @SerializedName("id") val id: String,
    @SerializedName("domain") val domain: String,
    @SerializedName("targetHost") val targetHost: String = "",
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("results") val results: List<Scanner.ScanResult>
) {
    val formattedDate: String
        get() {
            val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))
            return sdf.format(Date(timestamp))
        }
    
    val title: String
        get() = "$domain • $formattedDate"
    
    val workingCount: Int
        get() = results.count { it.isWorking }
    
    val avgLatency: Long
        get() {
            val workingResults = results.filter { it.isWorking && it.latency > 0 }
            return if (workingResults.isNotEmpty()) workingResults.map { it.latency }.average().toLong() else 0L
        }
}

/**
 * History storage using internal file storage with session-based grouping.
 */
object HistoryStorage {
    private const val TAG = "HistoryStorage"
    private const val FILE_NAME = "scan_sessions.json"
    private const val MAX_SESSIONS = 20
    private val gson = Gson()

    /**
     * Save sessions to internal storage.
     */
    fun saveSessions(context: Context, sessions: List<ScanSession>): Boolean {
        return try {
            // Limit to max sessions, keep newest first
            val limitedSessions = sessions.take(MAX_SESSIONS)
            val json = gson.toJson(limitedSessions)
            val file = File(context.filesDir, FILE_NAME)
            val tempFile = File(context.filesDir, "$FILE_NAME.tmp")

            FileOutputStream(tempFile).use { output ->
                output.write(json.toByteArray(Charsets.UTF_8))
                output.flush()
                output.fd.sync()
            }

            val success = tempFile.renameTo(file)
            if (!success) {
                tempFile.copyTo(file, overwrite = true)
                tempFile.delete()
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save sessions: ${e.message}")
            false
        }
    }

    /**
     * Load sessions from internal storage.
     */
    fun loadSessions(context: Context): List<ScanSession> {
        return try {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) return emptyList()

            val json = FileInputStream(file).use { it.readBytes().toString(Charsets.UTF_8) }
            if (json.isBlank()) return emptyList()

            val result: Array<ScanSession>? = gson.fromJson(json, Array<ScanSession>::class.java)
            result?.toList()?.sortedByDescending { it.timestamp } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load sessions: ${e.message}")
            emptyList()
        }
    }

    /**
     * Add a new session to history.
     */
    fun addSession(context: Context, session: ScanSession): List<ScanSession> {
        val existing = loadSessions(context)
        val updated = listOf(session) + existing
        saveSessions(context, updated)
        return updated
    }

    /**
     * Add results to existing session or create new one.
     */
    fun addResultsToSession(
        context: Context,
        sessionId: String,
        newResults: List<Scanner.ScanResult>
    ): List<ScanSession> {
        val sessions = loadSessions(context).toMutableList()
        val sessionIndex = sessions.indexOfFirst { it.id == sessionId }
        
        if (sessionIndex >= 0) {
            // Update existing session
            val existing = sessions[sessionIndex]
            val updatedSession = existing.copy(
                results = existing.results + newResults
            )
            sessions[sessionIndex] = updatedSession
        }
        
        saveSessions(context, sessions)
        return sessions.sortedByDescending { it.timestamp }
    }

    /**
     * Delete a session by ID.
     */
    fun deleteSession(context: Context, sessionId: String): List<ScanSession> {
        val sessions = loadSessions(context).filter { it.id != sessionId }
        saveSessions(context, sessions)
        return sessions
    }

    /**
     * Clear all sessions.
     */
    fun clearAll(context: Context): Boolean {
        return try {
            val file = File(context.filesDir, FILE_NAME)
            if (file.exists()) file.delete()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear sessions: ${e.message}")
            false
        }
    }

    // Legacy migration - convert old history format to sessions
    fun migrateOldHistory(context: Context): List<ScanSession> {
        val oldFile = File(context.filesDir, "scan_history.json")
        if (!oldFile.exists()) return emptyList()
        
        return try {
            val json = FileInputStream(oldFile).use { it.readBytes().toString(Charsets.UTF_8) }
            val oldResults: Array<Scanner.ScanResult>? = gson.fromJson(json, Array<Scanner.ScanResult>::class.java)
            
            if (!oldResults.isNullOrEmpty()) {
                // Group by domain and create sessions
                val now = System.currentTimeMillis()
                val session = ScanSession(
                    id = "legacy_${now}",
                    domain = "Imported History",
                    timestamp = now,
                    results = oldResults.toList()
                )
                
                // Save as new format
                saveSessions(context, listOf(session))
                
                // Delete old file
                oldFile.delete()
                
                listOf(session)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to migrate old history: ${e.message}")
            emptyList()
        }
    }
}
