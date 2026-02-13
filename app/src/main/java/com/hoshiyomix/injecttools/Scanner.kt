package com.hoshiyomix.injecttools

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter

object Scanner {

    data class ScanResult(
        val subdomain: String,
        val ip: String,
        val isWorking: Boolean,
        val isCloudflare: Boolean,
        val errorMsg: String? = null,
        val targetHost: String = "" // Target host yang digunakan saat scan
    )

    // Gson instance for serialization
    private val gson = Gson()
    private const val PREFS_KEY_HISTORY = "scan_history"

    /**
     * Save scan history to SharedPreferences
     * Uses commit() for synchronous save to ensure data persists even if app is closed quickly
     * @param prefs SharedPreferences instance
     * @param history List of ScanResult to save (max 20 items)
     */
    fun saveHistory(prefs: SharedPreferences, history: List<ScanResult>): Boolean {
        return try {
            val json = gson.toJson(history)
            prefs.edit().putString(PREFS_KEY_HISTORY, json).commit()
        } catch (e: Exception) {
            android.util.Log.e("Scanner", "Failed to save history: ${e.message}")
            false
        }
    }

    /**
     * Load scan history from SharedPreferences
     * @param prefs SharedPreferences instance
     * @return List of ScanResult (empty if not found or error)
     */
    fun loadHistory(prefs: SharedPreferences): List<ScanResult> {
        val json = prefs.getString(PREFS_KEY_HISTORY, null)
        if (json == null) {
            android.util.Log.d("Scanner", "No saved history found")
            return emptyList()
        }
        return try {
            val type = object : TypeToken<List<ScanResult>>() {}.type
            val result: List<ScanResult> = gson.fromJson(json, type) ?: emptyList()
            android.util.Log.d("Scanner", "Loaded ${result.size} history items")
            result
        } catch (e: Exception) {
            android.util.Log.e("Scanner", "Failed to load history: ${e.message}")
            emptyList()
        }
    }

    /**
     * Clear scan history from SharedPreferences
     * @param prefs SharedPreferences instance
     */
    fun clearHistory(prefs: SharedPreferences) {
        prefs.edit().remove(PREFS_KEY_HISTORY).apply()
    }

    private val CLOUDFLARE_RANGES = listOf(
        "173.245.48.0/20",
        "103.21.244.0/22",
        "103.22.200.0/22",
        "103.31.4.0/22",
        "141.101.64.0/18",
        "108.162.192.0/18",
        "190.93.240.0/20",
        "188.114.96.0/20",
        "197.234.240.0/22",
        "198.41.128.0/17",
        "162.158.0.0/15",
        "104.16.0.0/13",
        "104.24.0.0/14",
        "172.64.0.0/13",
        "131.0.72.0/22"
    )

    private fun isCloudflareIp(ip: String): Boolean {
        if (ip.startsWith("104.")) return true
        if (ip.startsWith("162.158.") || ip.startsWith("162.159.")) return true
        if (ip.startsWith("188.114.")) return true
        if (ip.startsWith("198.41.")) return true
        if (ip.startsWith("197.234.")) return true
        if (ip.startsWith("190.93.")) return true
        
        try {
            val ipAddr = ipToLong(ip)
            for (range in CLOUDFLARE_RANGES) {
                val parts = range.split("/")
                val rangeIp = ipToLong(parts[0])
                val bits = parts[1].toInt()
                val mask = -1 shl (32 - bits)
                
                if ((ipAddr and mask.toLong()) == (rangeIp and mask.toLong())) {
                    return true
                }
            }
        } catch (e: Exception) {
            return false
        }
        return false
    }

    private fun isPrivateIp(ip: String): Boolean {
        if (ip.startsWith("10.") || ip.startsWith("192.168.") || ip.startsWith("127.")) return true
        if (ip.startsWith("172.")) {
            val parts = ip.split(".")
            if (parts.size > 1) {
                val second = parts[1].toIntOrNull() ?: 0
                if (second in 16..31) return true
            }
        }
        return false
    }

    private fun isFakeDnsIp(ip: String): Boolean {
        return ip.startsWith("198.18.") || ip.startsWith("198.19.")
    }

    private fun ipToLong(ip: String): Long {
        val octets = ip.split(".")
        var result: Long = 0
        for (octet in octets) {
            result = (result shl 8) + (octet.toIntOrNull() ?: 0)
        }
        return result
    }

    suspend fun testSingle(target: String, subdomain: String): ScanResult = withContext(Dispatchers.IO) {
        var ip = ""
        var isCf = false
        
        try {
            // 1. DNS Resolution
            val allIps = InetAddress.getAllByName(subdomain)
            val ipv4 = allIps.firstOrNull { it is Inet4Address }
            
            if (ipv4 == null) {
                return@withContext ScanResult(subdomain, "", false, false, "No IPv4 address", target)
            }
            
            ip = ipv4.hostAddress ?: return@withContext ScanResult(subdomain, "", false, false, "Invalid IP", target)

            // 2. Environment Checks
            if (isFakeDnsIp(ip)) {
                return@withContext ScanResult(subdomain, ip, false, false, "Fake DNS (VPN Active)", target)
            }
            if (isPrivateIp(ip)) {
                return@withContext ScanResult(subdomain, ip, false, false, "Private IP", target)
            }
            
            isCf = isCloudflareIp(ip)
            if (!isCf) {
                return@withContext ScanResult(subdomain, ip, false, false, "Not Cloudflare IP", target)
            }

            // 3. Latency Check
            val socket = Socket()
            val start = System.currentTimeMillis()
            try {
                socket.connect(InetSocketAddress(ip, 443), 2000)
            } catch (e: Exception) {
                return@withContext ScanResult(subdomain, ip, false, isCf, "TCP Port 443 Blocked", target)
            }
            val latency = System.currentTimeMillis() - start
            if (latency < 5) {
                socket.close()
                return@withContext ScanResult(subdomain, ip, false, isCf, "VPN Interception (<5ms)", target)
            }

            // 4. SSL Handshake & HTTP Check
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            val sslSocket = factory.createSocket(socket, ip, 443, true) as SSLSocket
            
            val sslParameters = sslSocket.sslParameters
            sslParameters.serverNames = listOf(SNIHostName(target))
            sslSocket.sslParameters = sslParameters
            
            sslSocket.startHandshake()
            
            val writer = PrintWriter(sslSocket.outputStream)
            val reader = BufferedReader(InputStreamReader(sslSocket.inputStream))
            
            writer.print("HEAD / HTTP/1.1\r\n")
            writer.print("Host: $target\r\n")
            writer.print("Connection: close\r\n")
            writer.print("\r\n")
            writer.flush()
            
            var line: String?
            var hasCfRay = false
            var responseCode = ""
            
            while (reader.readLine().also { line = it } != null) {
                if (line.isNullOrBlank()) break
                
                if (line?.startsWith("HTTP/") == true) {
                    responseCode = line?.split(" ")?.getOrNull(1) ?: ""
                }
                
                if (line?.lowercase()?.startsWith("cf-ray:") == true) {
                    hasCfRay = true
                }
            }
            
            sslSocket.close()
            socket.close()

            if (hasCfRay) {
                // Reject Cloudflare Errors
                if (responseCode == "530" || responseCode.startsWith("52")) {
                    return@withContext ScanResult(subdomain, ip, false, isCf, "Domain Host Offline (HTTP $responseCode)", target)
                }

                return@withContext ScanResult(subdomain, ip, true, isCf, targetHost = target)
            } else {
                return@withContext ScanResult(subdomain, ip, false, isCf, "No CF-Ray (Incompatible)", target)
            }

        } catch (e: Exception) {
            return@withContext ScanResult(subdomain, ip, false, isCf, e.message ?: "Unknown Error", target)
        }
    }
}
