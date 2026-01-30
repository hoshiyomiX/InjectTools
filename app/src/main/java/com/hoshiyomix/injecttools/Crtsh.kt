package com.hoshiyomix.injecttools

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.TreeSet
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.Inet4Address

object Crtsh {
    data class CrtShEntry(
        @SerializedName("name_value") val nameValue: String?
    )

    interface CrtShApi {
        @GET("/")
        suspend fun search(
            @Query("q") query: String,
            @Query("output") output: String = "json"
        ): List<CrtShEntry>
    }

    private val api: CrtShApi by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Android 10; Mobile; rv:125.0) Gecko/125.0 Firefox/125.0")
                    .build()
                chain.proceed(request)
            }
            .build()

        Retrofit.Builder()
            .baseUrl("https://crt.sh/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CrtShApi::class.java)
    }

    // Cloudflare IP ranges for filtering
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

    private fun ipToLong(ip: String): Long {
        val octets = ip.split(".")
        var result: Long = 0
        for (octet in octets) {
            result = (result shl 8) + (octet.toIntOrNull() ?: 0)
        }
        return result
    }

    suspend fun fetchSubdomains(domain: String): List<String> = withContext(Dispatchers.IO) {
        try {
            Logger.log("Connecting to crt.sh for $domain...")
            
            val entries = api.search(query = "%.$domain")
            
            if (entries.isEmpty()) {
                Logger.log("crt.sh returned 0 entries")
                return@withContext emptyList()
            } else {
                Logger.log("crt.sh returned ${entries.size} raw entries")
            }

            val uniqueSubdomains = TreeSet<String>()
            
            // 1. Collect Valid Subdomains
            for (entry in entries) {
                // Safe handling of nullable nameValue
                val rawName = entry.nameValue ?: continue
                val names = rawName.split("\n")
                
                for (name in names) {
                    val cleaned = name.trim().replace("*.", "")
                    if (cleaned.endsWith(domain) && !cleaned.contains(" ")) {
                        uniqueSubdomains.add(cleaned)
                    }
                }
            }
            
            Logger.log("Found ${uniqueSubdomains.size} unique subdomains. Filtering Cloudflare IPs...")

            // 2. Filter Cloudflare IPs
            val cfSubdomains = mutableListOf<String>()
            val total = uniqueSubdomains.size
            var processed = 0

            for (sub in uniqueSubdomains) {
                processed++
                // Log progress every 10 items
                if (processed % 10 == 0) {
                     Logger.log("Filtering: $processed/$total...")
                }

                try {
                    val allIps = InetAddress.getAllByName(sub)
                    val ipv4 = allIps.firstOrNull { it is Inet4Address }?.hostAddress
                    
                    if (ipv4 != null && isCloudflareIp(ipv4)) {
                        cfSubdomains.add(sub)
                    }
                } catch (e: Exception) {
                    // Ignore DNS resolution errors during filtering
                }
            }
            
            Logger.log("Filtered: ${cfSubdomains.size} Cloudflare subdomains")
            return@withContext cfSubdomains
        } catch (e: Exception) {
            e.printStackTrace()
            val errorMsg = e.message ?: "Unknown error"
            Logger.log("Crtsh Error: $errorMsg")
            return@withContext emptyList()
        }
    }
}
