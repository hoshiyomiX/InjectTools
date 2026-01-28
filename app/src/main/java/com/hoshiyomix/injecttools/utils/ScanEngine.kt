package com.hoshiyomix.injecttools.utils

import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object ScanEngine {
    
    // Create unsafe OKHttpClient that trusts all certs
    private fun getUnsafeOkHttpClient(timeout: Long): OkHttpClient.Builder {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
        })

        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())

        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(timeout, TimeUnit.SECONDS)
            .readTimeout(timeout, TimeUnit.SECONDS)
    }

    fun checkTarget(target: String, timeout: Long): Boolean {
        try {
             val client = getUnsafeOkHttpClient(timeout).build()
             val request = Request.Builder().url("https://$target/").build()
             val response = client.newCall(request).execute()
             val success = response.isSuccessful || response.code == 404
             response.close()
             return success
        } catch (e: Exception) {
            return false
        }
    }

    fun testSubdomain(target: String, subdomain: String, timeout: Long): Boolean {
        try {
            val ips = InetAddress.getAllByName(subdomain)
            if (ips.isEmpty()) return false
            val subdomainIp = ips[0]

            val client = getUnsafeOkHttpClient(timeout)
                .dns(object : Dns {
                    override fun lookup(hostname: String): List<InetAddress> {
                        if (hostname == target) {
                            return listOf(subdomainIp)
                        }
                        return Dns.SYSTEM.lookup(hostname)
                    }
                })
                .build()

            val request = Request.Builder()
                .url("https://$target/")
                .header("Host", target)
                .build()

            val response = client.newCall(request).execute()
            val code = response.code
            response.close()
            return code < 500
        } catch (e: Exception) {
            return false
        }
    }
}
