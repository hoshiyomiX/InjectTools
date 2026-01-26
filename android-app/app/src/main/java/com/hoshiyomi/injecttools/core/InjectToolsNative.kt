package com.hoshiyomi.injecttools.core

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

/**
 * JNI Bridge to Rust native library (libinjecttools.so)
 * 
 * This object loads the native library and provides type-safe
 * Kotlin wrappers for all JNI methods.
 */
object InjectToolsNative {
    private const val TAG = "InjectToolsNative"
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    init {
        try {
            System.loadLibrary("injecttools")
            Log.i(TAG, "Native library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library", e)
            throw RuntimeException("Failed to load libinjecttools.so", e)
        }
    }
    
    // ═══════════════════════════════════════════════════════════════
    // JNI NATIVE METHOD DECLARATIONS
    // ═══════════════════════════════════════════════════════════════
    
    @JvmStatic
    private external fun checkTargetOnline(target: String): Boolean
    
    @JvmStatic
    private external fun testSubdomain(
        target: String,
        subdomain: String,
        timeout: Int
    ): String
    
    @JvmStatic
    private external fun batchTest(
        target: String,
        subdomains: Array<String>,
        timeout: Int
    ): String
    
    @JvmStatic
    private external fun discoverSubdomains(
        domain: String,
        limit: Int
    ): String
    
    @JvmStatic
    private external fun resolveDomain(domain: String): String
    
    @JvmStatic
    private external fun isCloudflareIP(ip: String): Boolean
    
    // ═══════════════════════════════════════════════════════════════
    // KOTLIN TYPE-SAFE WRAPPERS
    // ═══════════════════════════════════════════════════════════════
    
    /**
     * Check if target host is online and reachable
     */
    suspend fun isTargetOnline(target: String): Boolean = withContext(Dispatchers.IO) {
        try {
            checkTargetOnline(target)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking target: $target", e)
            false
        }
    }
    
    /**
     * Test single subdomain against target
     * 
     * @return ScanResult with validation details
     */
    suspend fun scanSubdomain(
        target: String,
        subdomain: String,
        timeout: Int = 10
    ): ScanResult = withContext(Dispatchers.IO) {
        try {
            val jsonResult = testSubdomain(target, subdomain, timeout)
            json.decodeFromString<ScanResult>(jsonResult)
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning subdomain: $subdomain", e)
            ScanResult(
                subdomain = subdomain,
                ip = "",
                isCloudflare = false,
                isWorking = false,
                isRestricted = false,
                errorMsg = e.message
            )
        }
    }
    
    /**
     * Batch test multiple subdomains
     * 
     * @return List of ScanResults
     */
    suspend fun scanBatch(
        target: String,
        subdomains: List<String>,
        timeout: Int = 10
    ): List<ScanResult> = withContext(Dispatchers.IO) {
        try {
            val jsonResult = batchTest(target, subdomains.toTypedArray(), timeout)
            json.decodeFromString<List<ScanResult>>(jsonResult)
        } catch (e: Exception) {
            Log.e(TAG, "Error in batch scan", e)
            emptyList()
        }
    }
    
    /**
     * Discover subdomains from crt.sh
     * 
     * @return List of discovered subdomain names
     */
    suspend fun discover(
        domain: String,
        limit: Int = 100
    ): List<String> = withContext(Dispatchers.IO) {
        try {
            val jsonResult = discoverSubdomains(domain, limit)
            json.decodeFromString<List<String>>(jsonResult)
        } catch (e: Exception) {
            Log.e(TAG, "Error discovering subdomains for: $domain", e)
            emptyList()
        }
    }
    
    /**
     * Resolve domain to IP address
     * 
     * @return IP address string or empty on error
     */
    suspend fun resolve(domain: String): String = withContext(Dispatchers.IO) {
        try {
            resolveDomain(domain)
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving domain: $domain", e)
            ""
        }
    }
    
    /**
     * Check if IP belongs to Cloudflare
     */
    suspend fun isCloudflare(ip: String): Boolean = withContext(Dispatchers.IO) {
        try {
            isCloudflareIP(ip)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Cloudflare IP: $ip", e)
            false
        }
    }
}

/**
 * Scan result from native scanner
 * Matches Rust ScanResult struct
 */
@Serializable
data class ScanResult(
    val subdomain: String,
    val ip: String,
    val isCloudflare: Boolean = false,
    val isWorking: Boolean = false,
    val isRestricted: Boolean = false,
    val statusCode: Int? = null,
    val cfRay: String? = null,
    val errorMsg: String? = null,
    val errorSource: String? = null
) {
    /**
     * Human-readable status
     */
    val status: String
        get() = when {
            isWorking -> "WORKING"
            errorMsg != null -> "ERROR"
            !isCloudflare -> "NOT_CF"
            else -> "SAFE"
        }
    
    /**
     * Status for UI display
     */
    val displayStatus: String
        get() = when {
            isWorking -> "✅ Vulnerable"
            errorMsg != null -> "❌ Error: $errorMsg"
            !isCloudflare -> "⚠️ Not Cloudflare"
            else -> "✅ Safe"
        }
}
