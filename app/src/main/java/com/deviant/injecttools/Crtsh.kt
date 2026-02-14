package com.deviant.injecttools

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

    /**
     * Progress callback for fetch operations
     * @param phase Current phase description
     * @param progress Progress value 0.0 - 1.0
     * @param current Current item being processed
     * @param total Total items to process
     */
    data class FetchProgress(
        val phase: String,
        val progress: Float,
        val current: Int = 0,
        val total: Int = 0
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
            .callTimeout(60, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "application/json")
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

    /**
     * Fetch subdomains from crt.sh with progress reporting
     * @param domain Domain to search
     * @param onProgress Progress callback (optional)
     * @return List of valid subdomains with IPv4 addresses
     */
    suspend fun fetchSubdomains(
        domain: String,
        onProgress: ((FetchProgress) -> Unit)? = null
    ): List<String> = withContext(Dispatchers.IO) {
        var attempts = 0
        val maxRetries = 3
        var entries: List<CrtShEntry> = emptyList()
        
        // Phase 1: API Request
        onProgress?.invoke(FetchProgress(
            phase = "Connecting to crt.sh...",
            progress = 0.05f
        ))
        
        // Support organization-based queries for major cloud providers
        val query = when {
            // Handle known cloud provider organization patterns
            domain.lowercase().contains("cloudflare") -> "cloudflare.com"
            domain.lowercase().contains("amazon") || domain.lowercase().contains("aws") -> "amazon.com"
            domain.lowercase().contains("google") || domain.lowercase().contains("gcp") -> "google.com"
            domain.lowercase().contains("akamai") -> "akamai.com"
            // Default: use plain domain query instead of wildcard "%." prefix
            // Wildcard queries on crt.sh often cause DB timeouts (503) for popular domains.
            // Searching "example.com" returns certificates for "*.example.com" and "sub.example.com" anyway.
            else -> domain
        }
        
        while (attempts < maxRetries) {
            try {
                onProgress?.invoke(FetchProgress(
                    phase = "Fetching certificates (attempt ${attempts + 1}/$maxRetries)...",
                    progress = 0.05f + (0.05f * attempts / maxRetries)
                ))
                
                entries = api.search(query = query)
                break // Success
            } catch (e: Exception) {
                attempts++
                val msg = e.message ?: "Unknown"
                
                // If 503 (server overload), wait longer
                if (msg.contains("503") || msg.contains("504")) {
                    onProgress?.invoke(FetchProgress(
                        phase = "Server busy, retrying in 3s...",
                        progress = 0.1f
                    ))
                    delay(3000)
                } else {
                    onProgress?.invoke(FetchProgress(
                        phase = "Retrying in 2s...",
                        progress = 0.1f
                    ))
                    delay(2000)
                }
                
                if (attempts == maxRetries) {
                    return@withContext emptyList()
                }
            }
        }

        if (entries.isEmpty()) {
            return@withContext emptyList()
        }

        // Phase 2: Parse certificates
        onProgress?.invoke(FetchProgress(
            phase = "Parsing ${entries.size} certificates...",
            progress = 0.2f,
            total = entries.size
        ))
        
        val uniqueSubdomains = TreeSet<String>()
        
        for ((index, entry) in entries.withIndex()) {
            val rawName = entry.nameValue ?: continue
            val names = rawName.split("\n")
            
            for (name in names) {
                val cleaned = name.trim().replace("*.", "")
                // Basic validation: must contain the domain and not have spaces
                if (cleaned.endsWith(domain) && !cleaned.contains(" ")) {
                    uniqueSubdomains.add(cleaned)
                }
            }
            
            // Report progress during parsing (20% - 30%)
            if (index % 50 == 0 || index == entries.size - 1) {
                onProgress?.invoke(FetchProgress(
                    phase = "Parsing certificates: ${index + 1}/${entries.size}",
                    progress = 0.2f + (0.1f * (index + 1) / entries.size),
                    current = index + 1,
                    total = entries.size
                ))
            }
        }
        
        if (uniqueSubdomains.isEmpty()) {
            return@withContext emptyList()
        }

        // Phase 3: DNS Validation
        val validSubdomains = mutableListOf<String>()
        val total = uniqueSubdomains.size
        
        onProgress?.invoke(FetchProgress(
            phase = "Validating DNS for $total subdomains...",
            progress = 0.3f,
            total = total
        ))

        for ((index, sub) in uniqueSubdomains.withIndex()) {
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
            
            // Report progress every 10 items or at the end (30% - 100%)
            if (index % 10 == 0 || index == total - 1) {
                onProgress?.invoke(FetchProgress(
                    phase = "DNS validation: ${index + 1}/$total (${validSubdomains.size} valid)",
                    progress = 0.3f + (0.7f * (index + 1) / total),
                    current = index + 1,
                    total = total
                ))
            }
        }
        
        onProgress?.invoke(FetchProgress(
            phase = "Complete! Found ${validSubdomains.size} valid subdomains",
            progress = 1.0f,
            current = validSubdomains.size,
            total = total
        ))
        
        return@withContext validSubdomains
    }
}
