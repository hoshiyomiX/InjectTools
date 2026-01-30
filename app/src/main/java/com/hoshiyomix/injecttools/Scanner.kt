package com.hoshiyomix.injecttools

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
        val errorMsg: String? = null
    )

    // Using the ranges from previous version, as they are standard CF ranges
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
        // Logic from beta branch (simplified prefix check + existing CIDR check)
        if (ip.startsWith("104.")) return true
        if (ip.startsWith("162.158.") || ip.startsWith("162.159.")) return true
        if (ip.startsWith("188.114.")) return true
        if (ip.startsWith("198.41.")) return true
        if (ip.startsWith("197.234.")) return true
        if (ip.startsWith("190.93.")) return true
        
        // CIDR Check for accuracy
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

    // Logic from beta branch: is_private_ip
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

    // Logic from beta branch: is_fake_dns_ip
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

    suspend fun testSingle(target: String, subdomain: String, verbose: Boolean = false, timeoutMs: Int = 5000): ScanResult = withContext(Dispatchers.IO) {
        var ip = ""
        var isCf = false
        
        if (verbose) Logger.log("--> Resolving DNS for: $subdomain")

        try {
            // 1. DNS Resolution (Force IPv4)
            val allIps = InetAddress.getAllByName(subdomain)
            val ipv4 = allIps.firstOrNull { it is Inet4Address }
            
            if (ipv4 == null) {
                return@withContext ScanResult(subdomain, "", false, false, "No IPv4 address found")
            }
            
            ip = ipv4.hostAddress ?: return@withContext ScanResult(subdomain, "", false, false, "Invalid IP")
            
            if (verbose) Logger.log("    Resolved IP: $ip")

            // 2. Environment Checks (Beta Logic)
            if (isFakeDnsIp(ip)) {
                if (verbose) Logger.log("!!  FAIL: Fake DNS IP detected")
                return@withContext ScanResult(subdomain, ip, false, false, "Fake DNS IP")
            }
            if (isPrivateIp(ip)) {
                if (verbose) Logger.log("!!  FAIL: Private IP detected")
                return@withContext ScanResult(subdomain, ip, false, false, "Private IP")
            }
            
            isCf = isCloudflareIp(ip)
            if (!isCf) {
                if (verbose) Logger.log("!!  FAIL: Not a Cloudflare IP")
                // Beta logic returns "NOT_WORKING" if not CF, so we act same
                return@withContext ScanResult(subdomain, ip, false, false, "Not Cloudflare IP")
            } else {
                 if (verbose) Logger.log("    Cloudflare IP: true")
            }

            // 3. Latency Check (Beta Logic: <5ms = VPN Interception)
            val socket = Socket()
            val start = System.currentTimeMillis()
            try {
                socket.connect(InetSocketAddress(ip, 443), 2000) // 2s timeout like beta
            } catch (e: Exception) {
                if (verbose) Logger.log("!!  FAIL: TCP Connection Blocked")
                return@withContext ScanResult(subdomain, ip, false, isCf, "TCP Blocked")
            }
            val latency = System.currentTimeMillis() - start
            if (latency < 5) {
                socket.close()
                if (verbose) Logger.log("!!  FAIL: Latency < 5ms (VPN Interception Detected)")
                return@withContext ScanResult(subdomain, ip, false, isCf, "VPN Interception (Lat < 5ms)")
            }

            // 4. SSL Handshake & HTTP Check (Beta Logic: Must have CF-Ray)
            if (verbose) Logger.log("--> Starting SSL Handshake + HTTP Check (SNI: $target)...")
            
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
            
            // Read headers
            while (reader.readLine().also { line = it } != null) {
                if (line.isNullOrBlank()) break
                
                if (verbose) Logger.log("    Header: $line")
                
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
                if (verbose) Logger.log("    SUCCESS: CF-Ray header found")
                return@withContext ScanResult(subdomain, ip, true, isCf)
            } else {
                if (verbose) Logger.log("!!  FAIL: No CF-Ray header found (Response: $responseCode)")
                return@withContext ScanResult(subdomain, ip, false, isCf, "No CF-Ray Header")
            }

        } catch (e: Exception) {
            if (verbose) Logger.log("!!  ERROR: ${e.message}")
            return@withContext ScanResult(subdomain, ip, false, isCf, e.message)
        }
    }
}
