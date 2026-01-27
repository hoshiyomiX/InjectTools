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
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

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

    private fun isPrivateIP(ip: String): Boolean {
        if (ip.startsWith("10.") || ip.startsWith("192.168.") || ip.startsWith("127.")) return true
        if (ip.startsWith("172.")) {
            val parts = ip.split(".")
            if (parts.size > 1) {
                val secondOctet = parts[1].toIntOrNull() ?: 0
                if (secondOctet in 16..31) return true
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
                if (second in 64..71) return true
            }
        }
        return false
    }

    suspend fun resolveDomain(domain: String): String? = withContext(Dispatchers.IO) {
        try {
            LogManager.d(TAG, "Resolving: $domain")
            val addresses = InetAddress.getAllByName(domain)
            val ip = addresses.firstOrNull()?.hostAddress
            if (ip != null) {
                LogManager.i(TAG, "Resolved $domain -> $ip")
            }
            ip
        } catch (e: Exception) {
            LogManager.e(TAG, "DNS failed for $domain: ${e.message}")
            null
        }
    }

    private suspend fun checkTLSHandshake(ip: String, sni: String): Boolean = withContext(Dispatchers.IO) {
        var socket: Socket? = null
        var sslSocket: SSLSocket? = null
        try {
            LogManager.d(TAG, "TLS check: $ip with SNI=$sni")
            
            socket = Socket()
            socket.connect(InetSocketAddress(ip, 443), 5000)
            
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })
            
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())
            
            sslSocket = sslContext.socketFactory.createSocket(
                socket, sni, 443, true
            ) as SSLSocket
            
            val sslParams = sslSocket.sslParameters
            sslParams.serverNames = listOf(javax.net.ssl.SNIHostName(sni))
            sslSocket.sslParameters = sslParams
            
            sslSocket.startHandshake()
            val session = sslSocket.session
            val isValid = session?.isValid == true
            
            if (isValid) {
                LogManager.i(TAG, "TLS OK: $ip accepts SNI=$sni")
            } else {
                LogManager.w(TAG, "TLS FAIL: $ip rejected SNI=$sni")
            }
            
            isValid
        } catch (e: Exception) {
            LogManager.w(TAG, "TLS FAIL: $ip - ${e.message}")
            false
        } finally {
            try {
                sslSocket?.close()
                socket?.close()
            } catch (e: Exception) {
                // Ignore close errors
            }
        }
    }

    private suspend fun headWithIP(url: String, host: String, ip: String): Pair<Int, Boolean> = withContext(Dispatchers.IO) {
        try {
            LogManager.d(TAG, "HTTP HEAD: $url via $ip")
            
            val customClient = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .followRedirects(false)
                .dns(object : Dns {
                    override fun lookup(hostname: String): List<InetAddress> {
                        return try {
                            if (hostname == host) {
                                listOf(InetAddress.getByName(ip))
                            } else {
                                InetAddress.getAllByName(hostname).toList()
                            }
                        } catch (e: Exception) {
                            emptyList()
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
            val cfRay = response.header("cf-ray")
            val hasCfRay = cfRay != null
            
            if (hasCfRay) {
                LogManager.i(TAG, "HTTP $statusCode with CF-Ray")
            }
            
            response.close()
            Pair(statusCode, hasCfRay)
        } catch (e: Exception) {
            LogManager.e(TAG, "HTTP failed: ${e.message}")
            Pair(0, false)
        }
    }

    suspend fun testSubdomain(target: String, subdomain: String, timeout: Int = 5): ScanResult = withContext(Dispatchers.IO) {
        try {
            LogManager.i(TAG, "Testing: $subdomain")
            
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

            val subIP = resolveDomain(subdomain)
            if (subIP == null) {
                LogManager.e(TAG, "DNS failed")
                return@withContext result.copy(errorMsg = "DNS resolution failed", errorSource = "dns")
            }
            result = result.copy(ip = subIP)

            if (isFakeDnsIP(subIP)) {
                return@withContext result.copy(errorMsg = "Fake DNS IP (VPN)", errorSource = "environment")
            }
            
            if (isPrivateIP(subIP)) {
                return@withContext result.copy(errorMsg = "Private IP", errorSource = "environment")
            }
            
            if (!isCloudflareIP(subIP)) {
                return@withContext result.copy(errorMsg = "Not Cloudflare IP", errorSource = "cloudflare_check")
            }

            result = result.copy(isCloudflare = true)
            LogManager.i(TAG, "IP is Cloudflare")

            val startTime = System.currentTimeMillis()
            var socket: Socket? = null
            try {
                socket = Socket()
                socket.connect(InetSocketAddress(subIP, 443), 2000)
                val latency = System.currentTimeMillis() - startTime
                
                if (latency < 5) {
                    return@withContext result.copy(errorMsg = "VPN detected", errorSource = "latency_check")
                }
                LogManager.i(TAG, "TCP OK: ${latency}ms")
            } catch (e: Exception) {
                LogManager.e(TAG, "TCP failed")
                return@withContext result.copy(errorMsg = "Port 443 blocked", errorSource = "tcp_check")
            } finally {
                try {
                    socket?.close()
                } catch (e: Exception) {}
            }

            if (!checkTLSHandshake(subIP, target)) {
                LogManager.e(TAG, "TLS failed")
                return@withContext result.copy(errorMsg = "TLS handshake failed", errorSource = "tls_handshake")
            }
            LogManager.i(TAG, "TLS SUCCESS")

            val url = "https://$target/"
            val (statusCode, hasCfRay) = headWithIP(url, target, subIP)
            
            result = result.copy(statusCode = statusCode)
            
            if (hasCfRay) {
                result = result.copy(cfRay = "Yes", isWorking = true)
                LogManager.i(TAG, "WORKING SUBDOMAIN!")
            } else {
                result = result.copy(errorMsg = "No CF-Ray header", errorSource = "http_check")
            }

            result
        } catch (e: Exception) {
            LogManager.e(TAG, "Scan error: ${e.message}", e)
            ScanResult(
                subdomain = subdomain,
                ip = "",
                isCloudflare = false,
                isWorking = false,
                isRestricted = false,
                statusCode = null,
                cfRay = null,
                errorMsg = "Scan failed: ${e.message}",
                errorSource = "exception"
            )
        }
    }

    suspend fun batchTest(target: String, subdomains: List<String>, timeout: Int = 5): List<ScanResult> = withContext(Dispatchers.IO) {
        try {
            LogManager.i(TAG, "Batch scan started: ${subdomains.size} subdomains")
            
            val results = subdomains.map { subdomain ->
                testSubdomain(target, subdomain, timeout)
            }
            
            val workingCount = results.count { it.isWorking }
            LogManager.i(TAG, "Batch completed: $workingCount / ${results.size} working")
            
            results
        } catch (e: Exception) {
            LogManager.e(TAG, "Batch test failed", e)
            emptyList()
        }
    }

    suspend fun discoverSubdomains(domain: String, limit: Int = 100): List<String> = withContext(Dispatchers.IO) {
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
            LogManager.e(TAG, "Discovery failed: ${e.message}", e)
            emptyList()
        }
    }
}
