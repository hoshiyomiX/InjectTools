package com.hoshiyomi.injecttools.core

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket

/**
 * Pure Kotlin implementation of InjectTools scanner
 * 100% logic parity with Rust scanner.rs
 * No JNI/native library dependency required
 */
object InjectToolsKotlin {
    private const val TAG = "InjectToolsKotlin"
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    @Serializable
    data class ScanResult(
        val subdomain: String,
        val ip: String,
        val isCloudflare: Boolean,
        val isWorking: Boolean,
        val isRestricted: Boolean,
        val statusCode: Int?,
        val cfRay: String?,
        val errorMsg: String?,
        val errorSource: String?
    )

    // =============================================================================
    // HELPER FUNCTIONS (Logic from scanner.rs V17)
    // =============================================================================

    private fun isPrivateIP(ip: String): Boolean {
        // Regex equivalent: ^10\.|^192\.168\.|^172\.(1[6-9]|2[0-9]|3[0-1])\.|^127\.
        if (ip.startsWith("10.") || 
            ip.startsWith("192.168.") || 
            ip.startsWith("127.")) {
            return true
        }
        
        if (ip.startsWith("172.")) {
            val parts = ip.split(".")
            if (parts.size > 1) {
                val secondOctet = parts[1].toIntOrNull() ?: 0
                if (secondOctet in 16..31) {
                    return true
                }
            }
        }
        return false
    }

    private fun isFakeDnsIP(ip: String): Boolean {
        // Regex: ^198\.(18|19)\.
        return ip.startsWith("198.18.") || ip.startsWith("198.19.")
    }

    fun isCloudflareIP(ip: String): Boolean {
        // V17 Explicit List
        if (ip.startsWith("104.")) return true
        if (ip.startsWith("162.158.") || ip.startsWith("162.159.")) return true
        if (ip.startsWith("188.114.")) return true
        if (ip.startsWith("198.41.")) return true
        if (ip.startsWith("197.234.")) return true
        if (ip.startsWith("190.93.")) return true
        
        // 172.64.0.0/13 -> 172.64.0.0 - 172.71.255.255
        // Regex: ^172\.(6[4-9]|7[0-1])\.
        if (ip.startsWith("172.")) {
            val parts = ip.split(".")
            if (parts.size > 1) {
                val second = parts[1].toIntOrNull() ?: 0
                if (second in 64..71) {
                    return true
                }
            }
        }
        return false
    }

    // =============================================================================
    // DNS RESOLUTION
    // =============================================================================

    suspend fun resolveDomain(domain: String): String? = withContext(Dispatchers.IO) {
        try {
            val addresses = InetAddress.getAllByName(domain)
            addresses.firstOrNull()?.hostAddress
        } catch (e: Exception) {
            Log.e(TAG, "DNS resolution failed for $domain: ${e.message}")
            null
        }
    }

    // =============================================================================
    // TLS HANDSHAKE CHECK
    // =============================================================================

    private suspend fun checkTLSHandshake(ip: String, sni: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val socket = Socket()
            socket.connect(InetSocketAddress(ip, 443), 5000)
            
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, null, null)
            
            val sslSocket = sslContext.socketFactory.createSocket(
                socket, sni, 443, true
            ) as SSLSocket
            
            // Set SNI
            val sslParams = sslSocket.sslParameters
            sslParams.serverNames = listOf(javax.net.ssl.SNIHostName(sni))
            sslSocket.sslParameters = sslParams
            
            sslSocket.startHandshake()
            sslSocket.close()
            socket.close()
            
            true
        } catch (e: Exception) {
            Log.d(TAG, "TLS handshake failed for $ip ($sni): ${e.message}")
            false
        }
    }

    // =============================================================================
    // HTTP REQUEST WITH CUSTOM IP
    // =============================================================================

    private suspend fun headWithIP(
        url: String, 
        host: String, 
        ip: String
    ): Pair<Int, Boolean> = withContext(Dispatchers.IO) {
        try {
            // Custom DNS: Force specific IP for host
            val customClient = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .followRedirects(false)
                .dns { hostname ->
                    if (hostname == host) {
                        listOf(InetAddress.getByName(ip))
                    } else {
                        InetAddress.getAllByName(hostname).toList()
                    }
                }
                .build()

            val request = Request.Builder()
                .url(url)
                .head()
                .header("Host", host)
                .build()

            val response = customClient.newCall(request).execute()
            val statusCode = response.code
            val hasCfRay = response.header("cf-ray") != null
            
            response.close()
            
            Pair(statusCode, hasCfRay)
        } catch (e: Exception) {
            Log.e(TAG, "HTTP request failed: ${e.message}")
            Pair(0, false)
        }
    }

    // =============================================================================
    // TARGET STATUS CHECK
    // =============================================================================

    suspend fun checkTargetOnline(target: String): Boolean {
        return try {
            val ip = resolveDomain(target) ?: return false
            
            // Check TLS handshake
            if (checkTLSHandshake(ip, target)) {
                return true
            }
            
            // Fallback: HTTP check
            val request = Request.Builder()
                .url("http://$target/")
                .head()
                .build()
            
            val response = httpClient.newCall(request).execute()
            val code = response.code
            response.close()
            
            code != 0
        } catch (e: Exception) {
            Log.e(TAG, "Target check failed: ${e.message}")
            false
        }
    }

    // =============================================================================
    // SINGLE SUBDOMAIN TEST (Main Logic from scanner.rs)
    // =============================================================================

    suspend fun testSubdomain(
        target: String,
        subdomain: String,
        timeout: Int = 5
    ): ScanResult = withContext(Dispatchers.IO) {
        var result = ScanResult(
            subdomain = subdomain,
            ip = "",
            isCloudflare = false,
            isWorking = false,
            isRestricted = false,
            statusCode = null,
            cfRay = null,
            errorMsg = null,
            errorSource = null
        )

        try {
            // 1. Resolve Subdomain
            val subIP = resolveDomain(subdomain)
            if (subIP == null) {
                return@withContext result.copy(errorMsg = "DNS resolution failed")
            }
            result = result.copy(ip = subIP)

            // 2. Environment Checks
            if (isFakeDnsIP(subIP)) {
                return@withContext result.copy(errorMsg = "Fake DNS IP detected (VPN)")
            }
            
            if (isPrivateIP(subIP)) {
                return@withContext result.copy(errorMsg = "Private IP address")
            }
            
            if (!isCloudflareIP(subIP)) {
                return@withContext result.copy(errorMsg = "Not a Cloudflare IP")
            }

            result = result.copy(isCloudflare = true)

            // 3. TCP Latency Check (Port 443)
            val startTime = System.currentTimeMillis()
            val socket = Socket()
            try {
                socket.connect(InetSocketAddress(subIP, 443), 2000)
                val latency = System.currentTimeMillis() - startTime
                socket.close()
                
                if (latency < 5) {
                    return@withContext result.copy(
                        errorMsg = "VPN detected (latency <5ms)"
                    )
                }
            } catch (e: Exception) {
                return@withContext result.copy(
                    errorMsg = "TCP port 443 blocked"
                )
            }

            // 4. TLS Handshake
            if (!checkTLSHandshake(subIP, target)) {
                return@withContext result.copy(
                    errorMsg = "TLS handshake failed"
                )
            }

            // 5. End-to-End HTTP Check
            val url = "https://$target/"
            val (statusCode, hasCfRay) = headWithIP(url, target, subIP)
            
            result = result.copy(statusCode = statusCode)
            
            if (hasCfRay) {
                result = result.copy(
                    cfRay = "Yes",
                    isWorking = true
                )
            } else {
                result = result.copy(
                    errorMsg = "No CF-Ray header found"
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "Scan error for $subdomain: ${e.message}")
            result = result.copy(
                errorMsg = "Scan failed: ${e.message}"
            )
        }

        result
    }

    // =============================================================================
    // BATCH TEST
    // =============================================================================

    suspend fun batchTest(
        target: String,
        subdomains: List<String>,
        timeout: Int = 5
    ): List<ScanResult> = withContext(Dispatchers.IO) {
        subdomains.map { subdomain ->
            testSubdomain(target, subdomain, timeout)
        }
    }

    // =============================================================================
    // CRT.SH SUBDOMAIN DISCOVERY
    // =============================================================================

    suspend fun discoverSubdomains(
        domain: String,
        limit: Int = 100
    ): List<String> = withContext(Dispatchers.IO) {
        try {
            val url = "https://crt.sh/?q=%25.$domain&output=json"
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: "[]"
            response.close()

            // Simple JSON parsing (assuming format: [{"name_value": "sub.domain.com"}, ...])
            val subdomains = mutableSetOf<String>()
            val regex = "\"name_value\"\\s*:\\s*\"([^\"]+)\"".toRegex()
            
            regex.findAll(body).forEach { match ->
                val nameValue = match.groupValues[1]
                // Handle wildcard and multiple names
                nameValue.split("\n").forEach { name ->
                    val cleanName = name.trim().removePrefix("*.")
                    if (cleanName.isNotEmpty() && 
                        cleanName.endsWith(".$domain") &&
                        !cleanName.contains("*")) {
                        subdomains.add(cleanName)
                    }
                }
            }

            subdomains.take(limit).toList()
        } catch (e: Exception) {
            Log.e(TAG, "crt.sh discovery failed: ${e.message}")
            emptyList()
        }
    }
}
