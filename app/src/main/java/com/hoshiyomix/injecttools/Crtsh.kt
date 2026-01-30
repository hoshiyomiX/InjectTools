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
import kotlinx.coroutines.delay
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
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
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

    suspend fun fetchSubdomains(domain: String): List<String> = withContext(Dispatchers.IO) {
        var attempts = 0
        val maxRetries = 3
        var entries: List<CrtShEntry> = emptyList()
        
        // 1. Retry Mechanism for API
        while (attempts < maxRetries) {
            try {
                Logger.log("Connecting to crt.sh for $domain (Attempt ${attempts + 1})...")
                entries = api.search(query = "%.$domain")
                break // Success
            } catch (e: Exception) {
                attempts++
                val msg = e.message ?: "Unknown"
                Logger.log("Attempt $attempts failed: $msg")
                if (attempts == maxRetries) {
                    Logger.log("Max retries reached. crt.sh might be down.")
                    return@withContext emptyList()
                }
                delay(2000) // Wait 2s before retry
            }
        }

        if (entries.isEmpty()) {
            Logger.log("crt.sh returned 0 entries")
            return@withContext emptyList()
        } else {
            Logger.log("crt.sh returned ${entries.size} raw entries")
        }

        val uniqueSubdomains = TreeSet<String>()
        
        // 2. Collect Valid Subdomains
        for (entry in entries) {
            val rawName = entry.nameValue ?: continue
            val names = rawName.split("\n")
            
            for (name in names) {
                val cleaned = name.trim().replace("*.", "")
                if (cleaned.endsWith(domain) && !cleaned.contains(" ")) {
                    uniqueSubdomains.add(cleaned)
                }
            }
        }
        
        Logger.log("Found ${uniqueSubdomains.size} unique subdomains. Verifying DNS...")

        // 3. Resolve DNS only (Removed strict CF filtering)
        // We allow non-CF domains to pass through so the Scanner UI can show "Not Cloudflare"
        // instead of silently hiding them. This gives better feedback.
        val validSubdomains = mutableListOf<String>()
        val total = uniqueSubdomains.size
        var processed = 0

        for (sub in uniqueSubdomains) {
            processed++
            if (processed % 20 == 0) {
                 Logger.log("Verifying: $processed/$total...")
            }

            try {
                // Just check if it resolves to an IPv4
                val allIps = InetAddress.getAllByName(sub)
                val ipv4 = allIps.firstOrNull { it is Inet4Address }?.hostAddress
                
                if (ipv4 != null) {
                    validSubdomains.add(sub)
                }
            } catch (e: Exception) {
                // Skip domains that don't resolve
            }
        }
        
        Logger.log("Result: ${validSubdomains.size} resolvable subdomains ready for scan.")
        return@withContext validSubdomains
    }
}
