package com.hoshiyomi.injecttools.core

import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

/**
 * JNI Bridge to Rust Native Library
 * 
 * This object provides type-safe wrappers around native Rust functions.
 * All native methods return JSON strings which are deserialized to Kotlin data classes.
 */
object InjectToolsNative {
    private const val TAG = "InjectToolsNative"
    private const val LIB_NAME = "injecttools"
    
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    
    init {
        try {
            System.loadLibrary(LIB_NAME)
            Log.i(TAG, "Native library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library: ${e.message}")
            throw RuntimeException("Native library not found. Did you build the Rust .so files?", e)
        }
    }
    
    // =========================================================================
    // NATIVE DECLARATIONS
    // =========================================================================
    
    /**
     * Check if target host is online and reachable
     * @param target Target domain (e.g., "www.bca.co.id")
     * @return true if online, false otherwise
     */
    @JvmStatic
    external fun checkTargetOnline(target: String): Boolean
    
    /**
     * Test single subdomain against target
     * @param target Target domain
     * @param subdomain Subdomain to test
     * @param timeout Timeout in seconds
     * @return JSON string with ScanResult
     */
    @JvmStatic
    private external fun testSubdomain(target: String, subdomain: String, timeout: Int): String
    
    /**
     * Batch test multiple subdomains
     * @param target Target domain
     * @param subdomains Array of subdomains
     * @param timeout Timeout in seconds
     * @return JSON array string with List<ScanResult>
     */
    @JvmStatic
    private external fun batchTest(target: String, subdomains: Array<String>, timeout: Int): String
    
    /**
     * Discover subdomains from crt.sh
     * @param domain Domain to discover
     * @param limit Maximum results (0 = unlimited)
     * @return JSON array of subdomain strings
     */
    @JvmStatic
    private external fun discoverSubdomains(domain: String, limit: Int): String
    
    /**
     * Resolve domain to IP address
     * @param domain Domain name
     * @return IP address or empty string on error
     */
    @JvmStatic
    external fun resolveDomain(domain: String): String
    
    /**
     * Check if IP is Cloudflare
     * @param ip IP address
     * @return true if Cloudflare IP
     */
    @JvmStatic
    external fun isCloudflareIP(ip: String): Boolean
    
    // =========================================================================
    // TYPE-SAFE KOTLIN WRAPPERS
    // =========================================================================
    
    /**
     * Test single subdomain with type-safe result
     */
    fun testSubdomainSafe(target: String, subdomain: String, timeout: Int = 5): Result<ScanResult> {
        return try {
            val jsonResult = testSubdomain(target, subdomain, timeout)
            val result = json.decodeFromString<ScanResult>(jsonResult)
            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "testSubdomain failed", e)
            Result.failure(e)
        }
    }
    
    /**
     * Batch test with type-safe results
     */
    fun batchTestSafe(target: String, subdomains: List<String>, timeout: Int = 5): Result<List<ScanResult>> {
        return try {
            val jsonResults = batchTest(target, subdomains.toTypedArray(), timeout)
            val results = json.decodeFromString<List<ScanResult>>(jsonResults)
            Result.success(results)
        } catch (e: Exception) {
            Log.e(TAG, "batchTest failed", e)
            Result.failure(e)
        }
    }
    
    /**
     * Discover subdomains with type-safe result
     */
    fun discoverSubdomainsSafe(domain: String, limit: Int = 0): Result<List<String>> {
        return try {
            val jsonArray = discoverSubdomains(domain, limit)
            val subdomains = json.decodeFromString<List<String>>(jsonArray)
            Result.success(subdomains)
        } catch (e: Exception) {
            Log.e(TAG, "discoverSubdomains failed", e)
            Result.failure(e)
        }
    }
}

// =============================================================================
// DATA CLASSES
// =============================================================================

/**
 * Result of a subdomain scan
 * Matches Rust struct in scanner.rs
 */
@Serializable
data class ScanResult(
    val subdomain: String,
    val ip: String,
    val isCloudflare: Boolean,
    val isWorking: Boolean,
    val isRestricted: Boolean,
    val statusCode: Int? = null,
    val cfRay: String? = null,
    val errorMsg: String? = null,
    val errorSource: String? = null
) {
    /**
     * Display-friendly status
     */
    val displayStatus: String
        get() = when {
            isWorking -> "✓ WORKING"
            errorMsg != null -> "✗ ${errorMsg}"
            else -> "✗ FAILED"
        }
    
    /**
     * Is this result an error?
     */
    val isError: Boolean
        get() = !isWorking
}
