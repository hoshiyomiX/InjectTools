package com.hoshiyomix.injecttools

import java.net.Inet4Address
import java.net.InetAddress
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

    private fun ipToLong(ip: String): Long {
        val octets = ip.split(".")
        var result: Long = 0
        for (octet in octets) {
            result = (result shl 8) + octet.toInt()
        }
        return result
    }

    suspend fun testSingle(target: String, subdomain: String, verbose: Boolean = false, timeoutMs: Int = 5000): ScanResult = withContext(Dispatchers.IO) {
        var ip = ""
        var isCf = false
        
        if (verbose) Logger.log("--> Resolving DNS for: $subdomain (IPv4 Only)")

        try {
            // 1. DNS Resolution (Force IPv4)
            val allIps = InetAddress.getAllByName(subdomain)
            val ipv4 = allIps.firstOrNull { it is Inet4Address }
            
            if (ipv4 == null) {
                return@withContext ScanResult(subdomain, "", false, false, "No IPv4 address found")
            }
            
            ip = ipv4.hostAddress ?: return@withContext ScanResult(subdomain, "", false, false, "Invalid IP")
            
            if (verbose) Logger.log("    Resolved IP: $ip")
            
            isCf = isCloudflareIp(ip)
            if (verbose) {
                if (isCf) {
                    Logger.log("    Cloudflare IP: true")
                } else {
                    Logger.log("!!  WARNING: Resolved IP is NOT a Cloudflare IP (proceeding anyway)")
                }
            }

            // 2. SSL/TLS Connection with SNI
            if (verbose) Logger.log("--> Starting SSL Handshake (SNI: $target)...")
            
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            val socket = Socket(ip, 443)
            socket.soTimeout = timeoutMs
            
            // Layering SSL over the existing socket connected to IP
            val sslSocket = factory.createSocket(socket, ip, 443, true) as SSLSocket
            
            // Set SNI to target
            val sslParameters = sslSocket.sslParameters
            sslParameters.serverNames = listOf(SNIHostName(target))
            sslSocket.sslParameters = sslParameters
            
            // Start Handshake
            sslSocket.startHandshake()
            
            if (verbose) Logger.log("    SSL Handshake: SUCCESS")
            
            // 3. HTTP Request Check (Tunneling Validation)
            // Send a HEAD request to check if server actually responds to HTTP
            if (verbose) Logger.log("--> Validating HTTP Tunneling...")
            val writer = PrintWriter(sslSocket.outputStream)
            val reader = BufferedReader(InputStreamReader(sslSocket.inputStream))
            
            writer.print("HEAD / HTTP/1.1\r\n")
            writer.print("Host: $target\r\n")
            writer.print("Connection: close\r\n")
            writer.print("\r\n")
            writer.flush()
            
            val responseLine = reader.readLine()
            
            sslSocket.close()
            socket.close()

            if (responseLine.isNullOrBlank()) {
                if (verbose) Logger.log("!!  FAIL: Server closed connection without response (Offline/Blocked)")
                return@withContext ScanResult(subdomain, ip, false, isCf, "SSL Handshake OK, but Server Closed Connection (No HTTP)")
            }

            if (verbose) Logger.log("    HTTP Response: $responseLine")
            
            // If we got a response line like "HTTP/1.1 200 OK" or "HTTP/1.1 404 Not Found" or "HTTP/1.1 101 Switching Protocols"
            // It means the tunneling path is at least listening and responding.
            return@withContext ScanResult(subdomain, ip, true, isCf)

        } catch (e: Exception) {
            if (verbose) Logger.log("!!  ERROR: ${e.message}")
            return@withContext ScanResult(subdomain, ip, false, isCf, e.message)
        }
    }
}
