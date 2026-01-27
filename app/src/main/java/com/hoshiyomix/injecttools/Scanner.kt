package com.hoshiyomix.injecttools

import java.net.InetAddress
import java.net.Socket
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    suspend fun testSingle(target: String, subdomain: String, timeoutMs: Int = 3000): ScanResult = withContext(Dispatchers.IO) {
        var ip = ""
        var isCf = false
        try {
            // 1. DNS Resolution
            val inetAddress = InetAddress.getByName(subdomain)
            ip = inetAddress.hostAddress ?: return@withContext ScanResult(subdomain, "", false, false, "No IP found")
            isCf = isCloudflareIp(ip)

            // 2. SSL/TLS Connection with SNI
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
            
            // If we reach here, handshake is successful
            sslSocket.close()
            socket.close()
            
            return@withContext ScanResult(subdomain, ip, true, isCf)

        } catch (e: Exception) {
            return@withContext ScanResult(subdomain, ip, false, isCf, e.message)
        }
    }
}
