package com.hoshiyomi.injecttools.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.Dns
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
    private const val TAG = "InjectToolsCore"
    
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
        return ip.startsWith("198.18.") || ip.startsWith("198.19.")
    }

    fun isCloudflareIP(ip: String): Boolean {
        if (ip.startsWith("104.")) return true
        if (ip.startsWith("162.158.") || ip.startsWith("162.159.")) return true
        if (ip.startsWith("188.114.")) return true
        if (ip.startsWith("198.41.")) return true
        if (ip.startsWith("197.234.")) return true
        if (ip.startsWith("190.93.")) return true
        
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
            LogManager.d(TAG, "Resolving domain: $domain")
            val addresses = InetAddress.getAllByName(domain)
            val ip = addresses.firstOrNull()?.hostAddress
            LogManager.i(TAG, "Resolved $domain -> $ip")
            ip
        } catch (e: Exception) {
            LogManager.e(TAG, "DNS resolution failed for $domain", e)
            null
        }
    }

    // =============================================================================
    // TLS HANDSHAKE CHECK
    // =============================================================================

    private suspend fun checkTLSHandshake(ip: String, sni: String): Boolean = withContext(Dispatchers.IO) {
        try {
            LogManager.d(TAG, "TLS handshake: $ip (SNI: $sni)")
            val socket = Socket()
            socket.connect(InetSocketAddress(ip, 443), 5000)
            
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, null, null)
            
            val sslSocket = sslContext.socketFactory.createSocket(
                socket, sni, 443, true
            ) as SSLSocket
            
            val sslParams = sslSocket.sslParameters
            sslParams.serverNames = listOf(javax.net.ssl.SNIHostName(sni))
            sslSocket.sslParameters = sslParams
            
            sslSocket.startHandshake()
            sslSocket.close()
            socket.close()
            
            LogManager.i(TAG, "TLS handshake SUCCESS: $ip")
            true
        } catch (e: Exception) {
            LogManager.w(TAG, "TLS handshake FAILED: $ip - ${e.message}")
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
            LogManager.d(TAG, "HTTP HEAD: $url (IP: $ip)")
            val customClient = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .followRedirects(false)
                .dns(object : Dns {
                    override fun lookup(hostname: String): List<InetAddress> {
                        return if (hostname == host) {
                            listOf(InetAddress.getByName(ip))
                        } else {
                            InetAddress.getAllByName(hostname).toList()
                        }
                    }
                })
                .build()

            val request = Request.Builder()
                .url(url)
                .head()
                .header("Host", host)
                .build()

            val response = customClient.newCall(request).execute()
            val statusCode = response.code
            val hasCfRay = response.header("cf-ray") != null
            
            LogManager.i(TAG, "HTTP response: $statusCode, CF-Ray: $hasCfRay")
            response.close()
            
            Pair(statusCode, hasCfRay)
        } catch (e: Exception) {
            LogManager.e(TAG, "HTTP request failed", e)
            Pair(0, false)
        }
    }

    // =============================================================================
    // TARGET STATUS CHECK
    // =============================================================================

    suspend fun checkTargetOnline(target: String): Boolean {
        return try {
            LogManager.i(TAG, "Checking if target is online: $target")
            val ip = resolveDomain(target)
            
            if (ip == null) {
                LogManager.w(TAG, "Target DNS resolution failed: $target")
                return false
            }
            
            LogManager.d(TAG, "Target IP: $ip")
            
            // Try TLS handshake first (most reliable)
            if (checkTLSHandshake(ip, target)) {
                LogManager.i(TAG, "Target is ONLINE (TLS handshake success)")
                return true
            }
            
            // Fallback: Try HTTP
            try {
                val request = Request.Builder()
                    .url("https://$target/")
                    .head()
                    .build()
                
                val response = httpClient.newCall(request).execute()
                val code = response.code
                response.close()
                
                val isOnline = code in 200..599  // Any response means online
                LogManager.i(TAG, "Target is ${if (isOnline) "ONLINE" else "OFFLINE"} (HTTP $code)")
                isOnline
            } catch (e: Exception) {
                LogManager.w(TAG, "HTTP fallback failed: ${e.message}")
                // Even if HTTP fails, if we got an IP, consider it potentially reachable
                true
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "Target check failed", e)
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
        LogManager.i(TAG, "Testing subdomain: $subdomain for target: $target")
        
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
                
                LogManager.d(TAG, "TCP latency: ${latency}ms")
                
                if (latency < 5) {
                    return@withContext result.copy(
                        errorMsg = "VPN detected (latency <5ms)"
                    )
                }
            } catch (e: Exception) {
                LogManager.w(TAG, "TCP connection failed", e)
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
                LogManager.i(TAG, "✅ Subdomain WORKING: $subdomain")
            } else {
                result = result.copy(
                    errorMsg = "No CF-Ray header found"
                )
                LogManager.w(TAG, "❌ Subdomain NOT working: $subdomain")
            }

        } catch (e: Exception) {
            LogManager.e(TAG, "Scan error for $subdomain", e)
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
        LogManager.i(TAG, "Starting batch test: ${subdomains.size} subdomains")
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
            LogManager.i(TAG, "Discovering subdomains for: $domain")
            val url = "https://crt.sh/?q=%25.$domain&output=json"
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: "[]"
            response.close()

            val subdomains = mutableSetOf<String>()
            val regex = "\"name_value\"\\s*:\\s*\"([^\"]+)\"".toRegex()
            
            regex.findAll(body).forEach { match ->
                val nameValue = match.groupValues[1]
                nameValue.split("\n").forEach { name ->
                    val cleanName = name.trim().removePrefix("*.")
                    if (cleanName.isNotEmpty() && 
                        cleanName.endsWith(".$domain") &&
                        !cleanName.contains("*")) {
                        subdomains.add(cleanName)
                    }
                }
            }

            val result = subdomains.take(limit).toList()
            LogManager.i(TAG, "Discovered ${result.size} subdomains")
            result
        } catch (e: Exception) {
            LogManager.e(TAG, "crt.sh discovery failed", e)
            emptyList()
        }
    }
}
