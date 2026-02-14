package com.hoshiyomix.injecttools

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
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
 * Multi-source subdomain fetcher with user-selectable sources.
 *
 * Available sources:
 * - CRT_SH: crt.sh - Certificate Transparency logs (most comprehensive)
 * - CERTSPOTTER: certspotter.com - Another CT log source (reliable API)
 * - HACKERTARGET: hackertarget.com - Free API for subdomain enumeration
 * - BUFFEROVER: dns.bufferover.run - Rapid7 Open Data (fast)
 */
object SubdomainFetcher {

    // =====================================================
    // Source Definition
    // =====================================================

    enum class Source(val displayName: String, val description: String) {
        CRT_SH("crt.sh", "Certificate Transparency - Paling lengkap"),
        CERTSPOTTER("CertSpotter", "CT Logs - API stabil"),
        HACKERTARGET("HackerTarget", "DNS Records - Cepat"),
        BUFFEROVER("BufferOver", "Rapid7 Data - Alternatif")
    }

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
    // API Interfaces
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
    // Main Fetch Function - User Selected Source
    // =====================================================

    /**
     * Fetch subdomains from a specific source selected by user.
     */
    suspend fun fetchFromSource(
        domain: String,
        source: Source,
        onProgress: ((FetchProgress) -> Unit)? = null
    ): FetchResult = withContext(Dispatchers.IO) {

        val cleanDomain = domain.lowercase().trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("www.")
            .split("/").first()

        onProgress?.invoke(FetchProgress(
            phase = "Connecting to ${source.displayName}...",
            progress = 0.05f,
            source = source.displayName
        ))

        try {
            val rawSubdomains = when (source) {
                Source.CRT_SH -> fetchFromCrtSh(cleanDomain, onProgress)
                Source.CERTSPOTTER -> fetchFromCertSpotter(cleanDomain, onProgress)
                Source.HACKERTARGET -> fetchFromHackerTarget(cleanDomain, onProgress)
                Source.BUFFEROVER -> fetchFromBufferOver(cleanDomain, onProgress)
            }

            if (rawSubdomains.isEmpty()) {
                return@withContext FetchResult(
                    subdomains = emptyList(),
                    source = source.displayName,
                    error = "No subdomains found"
                )
            }

            // DNS Validation
            val validSubdomains = validateSubdomains(rawSubdomains, cleanDomain, onProgress, source.displayName)

            FetchResult(
                subdomains = validSubdomains,
                source = source.displayName
            )

        } catch (e: Exception) {
            FetchResult(
                subdomains = emptyList(),
                source = source.displayName,
                error = e.message ?: "Unknown error"
            )
        }
    }

    // =====================================================
    // Individual Source Fetchers
    // =====================================================

    private suspend fun fetchFromCrtSh(
        domain: String,
        onProgress: ((FetchProgress) -> Unit)?
    ): List<String> {
        onProgress?.invoke(FetchProgress(
            phase = "Querying certificate logs...",
            progress = 0.1f,
            source = Source.CRT_SH.displayName
        ))

        var attempts = 0
        val maxRetries = 2

        while (attempts < maxRetries) {
            try {
                val entries = crtShApi.search(query = domain)

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

                onProgress?.invoke(FetchProgress(
                    phase = "Found ${subdomains.size} raw subdomains",
                    progress = 0.2f,
                    source = Source.CRT_SH.displayName
                ))

                return subdomains.toList()

            } catch (e: Exception) {
                attempts++
                if (e.message?.contains("503") == true || e.message?.contains("504") == true) {
                    onProgress?.invoke(FetchProgress(
                        phase = "Server busy, retrying...",
                        progress = 0.15f,
                        source = Source.CRT_SH.displayName
                    ))
                    delay(2000)
                }
                if (attempts == maxRetries) {
                    throw Exception("crt.sh unavailable: ${e.message}")
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
            phase = "Querying CT logs...",
            progress = 0.1f,
            source = Source.CERTSPOTTER.displayName
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

            onProgress?.invoke(FetchProgress(
                phase = "Found ${subdomains.size} raw subdomains",
                progress = 0.2f,
                source = Source.CERTSPOTTER.displayName
            ))
        } catch (e: Exception) {
            throw Exception("CertSpotter error: ${e.message}")
        }

        return subdomains.toList()
    }

    private suspend fun fetchFromHackerTarget(
        domain: String,
        onProgress: ((FetchProgress) -> Unit)?
    ): List<String> {
        onProgress?.invoke(FetchProgress(
            phase = "Querying DNS records...",
            progress = 0.1f,
            source = Source.HACKERTARGET.displayName
        ))

        val subdomains = mutableSetOf<String>()

        try {
            val response = hackerTargetApi.getSubdomains(
                "https://api.hackertarget.com/hostsearch/?q=$domain"
            )

            response.lines().forEach { line ->
                val parts = line.split(",")
                if (parts.isNotEmpty()) {
                    val subdomain = parts[0].trim()
                    if (subdomain.isNotEmpty() && subdomain.endsWith(domain, ignoreCase = true)) {
                        subdomains.add(subdomain)
                    }
                }
            }

            onProgress?.invoke(FetchProgress(
                phase = "Found ${subdomains.size} raw subdomains",
                progress = 0.2f,
                source = Source.HACKERTARGET.displayName
            ))
        } catch (e: Exception) {
            throw Exception("HackerTarget error: ${e.message}")
        }

        return subdomains.toList()
    }

    private suspend fun fetchFromBufferOver(
        domain: String,
        onProgress: ((FetchProgress) -> Unit)?
    ): List<String> {
        onProgress?.invoke(FetchProgress(
            phase = "Querying Rapid7 data...",
            progress = 0.1f,
            source = Source.BUFFEROVER.displayName
        ))

        val subdomains = mutableSetOf<String>()

        try {
            val response = bufferOverApi.search(
                "https://dns.bufferover.run/dns?q=.$domain"
            )

            response.results?.forEach { result ->
                result.fdnsA?.forEach { entry ->
                    val parts = entry.split(",")
                    if (parts.isNotEmpty()) {
                        val subdomain = parts[0].trim()
                        if (subdomain.isNotEmpty()) {
                            subdomains.add(subdomain)
                        }
                    }
                }

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

            onProgress?.invoke(FetchProgress(
                phase = "Found ${subdomains.size} raw subdomains",
                progress = 0.2f,
                source = Source.BUFFEROVER.displayName
            ))
        } catch (e: Exception) {
            throw Exception("BufferOver error: ${e.message}")
        }

        return subdomains.toList()
    }

    // =====================================================
    // DNS Validation
    // =====================================================

    private suspend fun validateSubdomains(
        subdomains: List<String>,
        targetDomain: String,
        onProgress: ((FetchProgress) -> Unit)?,
        sourceName: String
    ): List<String> = withContext(Dispatchers.IO) {

        val uniqueSubdomains = TreeSet<String>(subdomains)
        val validSubdomains = mutableListOf<String>()
        val total = uniqueSubdomains.size

        if (total == 0) return@withContext emptyList()

        onProgress?.invoke(FetchProgress(
            phase = "Validating DNS for $total subdomains...",
            progress = 0.3f,
            total = total,
            source = sourceName
        ))

        for ((index, sub) in uniqueSubdomains.withIndex()) {
            try {
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

            if (index % 10 == 0 || index == total - 1) {
                onProgress?.invoke(FetchProgress(
                    phase = "DNS: ${index + 1}/$total (${validSubdomains.size} valid)",
                    progress = 0.3f + (0.7f * (index + 1) / total),
                    current = index + 1,
                    total = total,
                    source = sourceName
                ))
            }
        }

        onProgress?.invoke(FetchProgress(
            phase = "Complete! ${validSubdomains.size} valid subdomains",
            progress = 1.0f,
            current = validSubdomains.size,
            total = total,
            source = sourceName
        ))

        validSubdomains
    }

    // =====================================================
    // Legacy Compatibility
    // =====================================================

    /**
     * Backward compatible function - defaults to crt.sh
     */
    suspend fun fetchSubdomains(
        domain: String,
        onProgress: ((FetchProgress) -> Unit)? = null
    ): FetchResult {
        return fetchFromSource(domain, Source.CRT_SH, onProgress)
    }
}
