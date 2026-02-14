package com.deviant.injecttools

import com.google.gson.annotations.SerializedName
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
        @SerializedName("subdomain") val subdomain: String,
        @SerializedName("ip") val ip: String,
        @SerializedName("isWorking") val isWorking: Boolean,
        @SerializedName("isCloudflare") val isCloudflare: Boolean,
        @SerializedName("errorMsg") val errorMsg: String? = null,
        @SerializedName("targetHost") val targetHost: String = "", // Target host yang digunakan saat scan
        @SerializedName("latency") val latency: Long = 0 // Latency in milliseconds
    )

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
        var measuredLatency = 0L
        
        try {
            // 1. DNS Resolution
            val allIps = InetAddress.getAllByName(subdomain)
            val ipv4 = allIps.firstOrNull { it is Inet4Address }
            
            if (ipv4 == null) {
                return@withContext ScanResult(subdomain, "", false, false, "No IPv4 address", target, 0)
            }
            
            ip = ipv4.hostAddress ?: return@withContext ScanResult(subdomain, "", false, false, "Invalid IP", target, 0)

            // 2. Environment Checks
            if (isFakeDnsIp(ip)) {
                return@withContext ScanResult(subdomain, ip, false, false, "Fake DNS (VPN Active)", target, 0)
            }
            if (isPrivateIp(ip)) {
                return@withContext ScanResult(subdomain, ip, false, false, "Private IP", target, 0)
            }
            
            isCf = isCloudflareIp(ip)
            if (!isCf) {
                return@withContext ScanResult(subdomain, ip, false, false, "Not Cloudflare IP", target, 0)
            }

            // 3. Latency Check
            val socket = Socket()
            val start = System.currentTimeMillis()
            try {
                socket.connect(InetSocketAddress(ip, 443), 2000)
            } catch (e: Exception) {
                return@withContext ScanResult(subdomain, ip, false, isCf, "TCP Port 443 Blocked", target, 0)
            }
            measuredLatency = System.currentTimeMillis() - start
            if (measuredLatency < 5) {
                socket.close()
                return@withContext ScanResult(subdomain, ip, false, isCf, "VPN Interception (<5ms)", target, measuredLatency)
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
                    return@withContext ScanResult(subdomain, ip, false, isCf, "Domain Host Offline (HTTP $responseCode)", target, measuredLatency)
                }

                return@withContext ScanResult(subdomain, ip, true, isCf, targetHost = target, latency = measuredLatency)
            } else {
                return@withContext ScanResult(subdomain, ip, false, isCf, "No CF-Ray (Incompatible)", target, measuredLatency)
            }

        } catch (e: Exception) {
            return@withContext ScanResult(subdomain, ip, false, isCf, e.message ?: "Unknown Error", target, measuredLatency)
        }
    }
}
