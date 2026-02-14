package com.hoshiyomix.injecttools

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url
import java.util.TreeSet
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.net.InetAddress
import java.net.Inet4Address

/**
 * Multi-source subdomain fetcher with automatic fallback.
 *
 * Sources (in order of reliability):
 * 1. crt.sh - Certificate Transparency logs (original)
 * 2. certspotter.com - Another CT log source
 * 3. hackertarget.com - Free API for subdomain enumeration
 * 4. bufferover.run - Rapid7 Open Data (certificate data)
 */
object SubdomainFetcher {

    data class FetchProgress(
        val phase: String,
        val progress: Float,
        val current: Int = 0,
        val total: Int = 0,
        val source: String = ""
    )

    data class FetchResult(
        val subdomains: List<String>,
        val source: String,
        val error: String? = null
    )

    // =====================================================
    // HTTP Client Configuration
    // =====================================================
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "application/json, text/html, */*")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    // =====================================================
    // Source 1: crt.sh (Certificate Transparency)
    // =====================================================
    private data class CrtShEntry(
        @SerializedName("name_value") val nameValue: String?
    )

    private interface CrtShApi {
        @GET("/")
        suspend fun search(
            @Query("q") query: String,
            @Query("output") output: String = "json"
        ): List<CrtShEntry>
    }

    private val crtShApi: CrtShApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://crt.sh/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CrtShApi::class.java)
    }

    // =====================================================
    // Source 2: CertSpotter (Certificate Transparency)
    // =====================================================
    private data class CertSpotterEntry(
        @SerializedName("dns_names") val dnsNames: List<String>?
    )

    private interface CertSpotterApi {
        @GET("v1/issuances")
        suspend fun search(
            @Query("domain") domain: String,
            @Query("include_subdomains") includeSubdomains: String = "true",
            @Query("expand") expand: String = "dns_names"
        ): List<CertSpotterEntry>
    }

    private val certSpotterApi: CertSpotterApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.certspotter.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CertSpotterApi::class.java)
    }

    // =====================================================
    // Source 3: HackerTarget API
    // =====================================================
    private interface HackerTargetApi {
        @GET
        suspend fun getSubdomains(@Url url: String): String
    }

    private val hackerTargetApi: HackerTargetApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.hackertarget.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(HackerTargetApi::class.java)
    }

    // =====================================================
    // Source 4: BufferOver.run (Rapid7 Open Data)
    // =====================================================
    private data class BufferOverResult(
        @SerializedName("FDNS_A") val fdnsA: List<String>?,
        @SerializedName("RDNS") val rdns: List<String>?
    )

    private data class BufferOverResponse(
        @SerializedName("Results") val results: List<BufferOverResult>?
    )

    private interface BufferOverApi {
        @GET
        suspend fun search(@Url url: String): BufferOverResponse
    }

    private val bufferOverApi: BufferOverApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://dns.bufferover.run/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BufferOverApi::class.java)
    }

    // =====================================================
    // Main Fetch Function with Fallback
    // =====================================================

    /**
     * Fetch subdomains from multiple sources with automatic fallback.
     * Tries each source in order until one succeeds.
     */
    suspend fun fetchSubdomains(
        domain: String,
        onProgress: ((FetchProgress) -> Unit)? = null
    ): FetchResult = withContext(Dispatchers.IO) {

        val cleanDomain = domain.lowercase().trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("www.")
            .split("/").first()

        // Try sources in order
        val sources = listOf(
            "crt.sh" to { fetchFromCrtSh(cleanDomain, onProgress) },
            "certspotter" to { fetchFromCertSpotter(cleanDomain, onProgress) },
            "hackertarget" to { fetchFromHackerTarget(cleanDomain, onProgress) },
            "bufferover" to { fetchFromBufferOver(cleanDomain, onProgress) }
        )

        var lastError: String? = null

        for ((sourceName, fetcher) in sources) {
            try {
                onProgress?.invoke(FetchProgress(
                    phase = "Trying $sourceName...",
                    progress = 0.05f,
                    source = sourceName
                ))

                val result = fetcher()

                if (result.isNotEmpty()) {
                    // Validate DNS and filter
                    val validSubdomains = validateSubdomains(result, cleanDomain, onProgress)

                    return@withContext FetchResult(
                        subdomains = validSubdomains,
                        source = sourceName
                    )
                }
            } catch (e: Exception) {
                lastError = "${e.message}"
                // Continue to next source
            }
        }

        // All sources failed
        FetchResult(
            subdomains = emptyList(),
            source = "failed",
            error = lastError ?: "All sources failed"
        )
    }

    // =====================================================
    // Individual Source Fetchers
    // =====================================================

    private suspend fun fetchFromCrtSh(
        domain: String,
        onProgress: ((FetchProgress) -> Unit)?
    ): List<String> {
        onProgress?.invoke(FetchProgress(
            phase = "Querying crt.sh certificate logs...",
            progress = 0.1f,
            source = "crt.sh"
        ))

        val query = domain
        var attempts = 0
        val maxRetries = 2

        while (attempts < maxRetries) {
            try {
                val entries = crtShApi.search(query = query)

                val subdomains = mutableSetOf<String>()
                for (entry in entries) {
                    val rawName = entry.nameValue ?: continue
                    val names = rawName.split("\n")

                    for (name in names) {
                        val cleaned = name.trim()
                            .removePrefix("*.")
                            .removePrefix(".")

                        if (cleaned.isNotEmpty() && !cleaned.contains(" ")) {
                            subdomains.add(cleaned)
                        }
                    }
                }

                return subdomains.toList()

            } catch (e: Exception) {
                attempts++
                if (e.message?.contains("503") == true || e.message?.contains("504") == true) {
                    delay(2000)
                }
                if (attempts == maxRetries) {
                    throw e
                }
            }
        }

        return emptyList()
    }

    private suspend fun fetchFromCertSpotter(
        domain: String,
        onProgress: ((FetchProgress) -> Unit)?
    ): List<String> {
        onProgress?.invoke(FetchProgress(
            phase = "Querying CertSpotter CT logs...",
            progress = 0.1f,
            source = "certspotter"
        ))

        val subdomains = mutableSetOf<String>()

        try {
            val entries = certSpotterApi.search(domain = domain)

            for (entry in entries) {
                entry.dnsNames?.forEach { name ->
                    val cleaned = name.trim()
                        .removePrefix("*.")
                        .removePrefix(".")

                    if (cleaned.isNotEmpty() && !cleaned.startsWith("_")) {
                        subdomains.add(cleaned)
                    }
                }
            }
        } catch (e: Exception) {
            // CertSpotter might fail silently, continue with what we have
        }

        return subdomains.toList()
    }

    private suspend fun fetchFromHackerTarget(
        domain: String,
        onProgress: ((FetchProgress) -> Unit)?
    ): List<String> {
        onProgress?.invoke(FetchProgress(
            phase = "Querying HackerTarget API...",
            progress = 0.1f,
            source = "hackertarget"
        ))

        val subdomains = mutableSetOf<String>()

        try {
            val response = hackerTargetApi.getSubdomains(
                "https://api.hackertarget.com/hostsearch/?q=$domain"
            )

            // Response format: "subdomain.domain.com,IP"
            response.lines().forEach { line ->
                val parts = line.split(",")
                if (parts.isNotEmpty()) {
                    val subdomain = parts[0].trim()
                    if (subdomain.isNotEmpty() && subdomain.endsWith(domain, ignoreCase = true)) {
                        subdomains.add(subdomain)
                    }
                }
            }
        } catch (e: Exception) {
            // Continue with what we have
        }

        return subdomains.toList()
    }

    private suspend fun fetchFromBufferOver(
        domain: String,
        onProgress: ((FetchProgress) -> Unit)?
    ): List<String> {
        onProgress?.invoke(FetchProgress(
            phase = "Querying BufferOver DNS data...",
            progress = 0.1f,
            source = "bufferover"
        ))

        val subdomains = mutableSetOf<String>()

        try {
            val response = bufferOverApi.search(
                "https://dns.bufferover.run/dns?q=.$domain"
            )

            response.results?.forEach { result ->
                // FDNS_A format: "subdomain.domain.com,IP"
                result.fdnsA?.forEach { entry ->
                    val parts = entry.split(",")
                    if (parts.isNotEmpty()) {
                        val subdomain = parts[0].trim()
                        if (subdomain.isNotEmpty()) {
                            subdomains.add(subdomain)
                        }
                    }
                }

                // RDNS format: "IP,subdomain.domain.com"
                result.rdns?.forEach { entry ->
                    val parts = entry.split(",")
                    if (parts.size > 1) {
                        val subdomain = parts[1].trim()
                        if (subdomain.isNotEmpty() && subdomain != "None") {
                            subdomains.add(subdomain)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Continue with what we have
        }

        return subdomains.toList()
    }

    // =====================================================
    // DNS Validation
    // =====================================================

    private suspend fun validateSubdomains(
        subdomains: List<String>,
        targetDomain: String,
        onProgress: ((FetchProgress) -> Unit)?
    ): List<String> = withContext(Dispatchers.IO) {

        val uniqueSubdomains = TreeSet<String>(subdomains)
        val validSubdomains = mutableListOf<String>()
        val total = uniqueSubdomains.size

        if (total == 0) return@withContext emptyList()

        onProgress?.invoke(FetchProgress(
            phase = "Validating DNS for $total subdomains...",
            progress = 0.3f,
            total = total,
            source = "dns"
        ))

        for ((index, sub) in uniqueSubdomains.withIndex()) {
            try {
                // Only accept subdomains that:
                // 1. End with target domain (or are the domain itself)
                // 2. Resolve to IPv4
                val isValidDomain = sub.equals(targetDomain, ignoreCase = true) ||
                        sub.endsWith(".$targetDomain", ignoreCase = true)

                if (isValidDomain) {
                    val allIps = InetAddress.getAllByName(sub)
                    val ipv4 = allIps.firstOrNull { it is Inet4Address }?.hostAddress

                    if (ipv4 != null) {
                        validSubdomains.add(sub)
                    }
                }
            } catch (e: Exception) {
                // Domain doesn't resolve, skip
            }

            // Report progress every 10 items
            if (index % 10 == 0 || index == total - 1) {
                onProgress?.invoke(FetchProgress(
                    phase = "DNS validation: ${index + 1}/$total (${validSubdomains.size} valid)",
                    progress = 0.3f + (0.7f * (index + 1) / total),
                    current = index + 1,
                    total = total,
                    source = "dns"
                ))
            }
        }

        onProgress?.invoke(FetchProgress(
            phase = "Complete! Found ${validSubdomains.size} valid subdomains",
            progress = 1.0f,
            current = validSubdomains.size,
            total = total,
            source = "complete"
        ))

        validSubdomains
    }

    // =====================================================
    // Legacy Compatibility
    // =====================================================

    /**
     * Backward compatible function for existing code.
     * @deprecated Use fetchSubdomains() instead for better error handling.
     */
    suspend fun fetchSubdomainsLegacy(
        domain: String,
        onProgress: ((FetchProgress) -> Unit)? = null
    ): List<String> {
        val result = fetchSubdomains(domain, onProgress)
        return result.subdomains
    }
}
