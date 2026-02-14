package com.hoshiyomix.injecttools

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.Request
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
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate

/**
 * Multi-source subdomain fetcher with user-selectable sources.
 *
 * Available sources:
 * - CRT_SH: crt.sh - Certificate Transparency logs (most comprehensive)
 * - HACKERTARGET: hackertarget.com - Free API for subdomain enumeration (reliable)
 * - ALIENVAULT: otx.alienvault.com - AlienVault Open Threat Exchange
 */
object SubdomainFetcher {

    // =====================================================
    // Source Definition
    // =====================================================

    enum class Source(val displayName: String, val description: String) {
        CRT_SH("crt.sh", "Certificate Transparency - Paling lengkap"),
        HACKERTARGET("HackerTarget", "DNS Records - Cepat & stabil"),
        ALIENVAULT("AlienVault", "Threat Intel - Alternatif bagus")
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
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    // =====================================================
    // Source 1: crt.sh API (Retrofit)
    // =====================================================

    private data class CrtShEntry(
        @SerializedName("name_value") val nameValue: String?
    )

    private interface CrtShApi {
        @GET("/")
        suspend fun search(
            @Query("q") query: String,
            @Query("output") output: String = "json"
        ): List<CrtShEntry>?
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
    // Source 2: HackerTarget (Plain Text via OkHttp)
    // =====================================================

    // HackerTarget returns plain text, not JSON
    // Format: subdomain.domain.com,IP
    private suspend fun fetchHackerTargetRaw(domain: String): String {
        val request = Request.Builder()
            .url("https://api.hackertarget.com/hostsearch/?q=$domain")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .header("Accept", "text/plain")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}")
            }
            return response.body?.string() ?: ""
        }
    }

    // =====================================================
    // Source 3: AlienVault OTX API
    // =====================================================

    private data class AlienVaultPassiveDns(
        @SerializedName("hostname") val hostname: String?
    )

    private data class AlienVaultResponse(
        @SerializedName("passive_dns") val passiveDns: List<AlienVaultPassiveDns>?
    )

    private interface AlienVaultApi {
        @GET("api/v1/indicators/domain/{domain}/passive_dns")
        suspend fun getSubdomains(
            @retrofit2.http.Path("domain") domain: String
        ): AlienVaultResponse?
    }

    private val alienVaultApi: AlienVaultApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://otx.alienvault.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AlienVaultApi::class.java)
    }

    // =====================================================
    // Main Fetch Function - User Selected Source
    // =====================================================

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
                Source.HACKERTARGET -> fetchFromHackerTarget(cleanDomain, onProgress)
                Source.ALIENVAULT -> fetchFromAlienVault(cleanDomain, onProgress)
            }

            if (rawSubdomains.isEmpty()) {
                return@withContext FetchResult(
                    subdomains = emptyList(),
                    source = source.displayName,
                    error = "No subdomains found for $cleanDomain"
                )
            }

            // DNS Validation
            val validSubdomains = validateSubdomains(rawSubdomains, cleanDomain, onProgress, source.displayName)

            FetchResult(
                subdomains = validSubdomains,
                source = source.displayName
            )

        } catch (e: Exception) {
            val errorMsg = e.message ?: "Unknown error"
            FetchResult(
                subdomains = emptyList(),
                source = source.displayName,
                error = errorMsg
            )
        }
    }

    // =====================================================
    // Source 1: crt.sh Implementation
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
        val maxRetries = 3

        while (attempts < maxRetries) {
            try {
                val entries = crtShApi.search(query = domain)

                if (entries == null) {
                    throw Exception("Empty response from crt.sh")
                }

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
                val errorMsg = e.message ?: ""

                when {
                    errorMsg.contains("503") || errorMsg.contains("504") -> {
                        onProgress?.invoke(FetchProgress(
                            phase = "Server busy, retry ${attempts}/$maxRetries...",
                            progress = 0.1f + (0.03f * attempts),
                            source = Source.CRT_SH.displayName
                        ))
                        delay(3000)
                    }
                    errorMsg.contains("timeout", ignoreCase = true) -> {
                        onProgress?.invoke(FetchProgress(
                            phase = "Timeout, retry ${attempts}/$maxRetries...",
                            progress = 0.1f + (0.03f * attempts),
                            source = Source.CRT_SH.displayName
                        ))
                        delay(2000)
                    }
                    else -> {
                        if (attempts == maxRetries) {
                            throw Exception("crt.sh: $errorMsg")
                        }
                        delay(1000)
                    }
                }

                if (attempts == maxRetries) {
                    throw Exception("crt.sh unavailable after $maxRetries attempts: $errorMsg")
                }
            }
        }

        return emptyList()
    }

    // =====================================================
    // Source 2: HackerTarget Implementation (Plain Text)
    // =====================================================

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
            val response = fetchHackerTargetRaw(domain)

            if (response.isBlank()) {
                throw Exception("Empty response")
            }

            // Parse plain text response
            // Format: subdomain.domain.com,IP
            response.lines().forEach { line ->
                val trimmedLine = line.trim()
                if (trimmedLine.isEmpty()) return@forEach

                // Split by comma, first part is subdomain
                val commaIndex = trimmedLine.indexOf(',')
                val subdomain = if (commaIndex > 0) {
                    trimmedLine.substring(0, commaIndex).trim()
                } else {
                    trimmedLine
                }

                // Validate: must end with target domain
                if (subdomain.isNotEmpty() &&
                    subdomain.endsWith(domain, ignoreCase = true) &&
                    !subdomain.contains("error", ignoreCase = true)) {
                    subdomains.add(subdomain)
                }
            }

            onProgress?.invoke(FetchProgress(
                phase = "Found ${subdomains.size} raw subdomains",
                progress = 0.2f,
                source = Source.HACKERTARGET.displayName
            ))

        } catch (e: Exception) {
            throw Exception("HackerTarget: ${e.message}")
        }

        return subdomains.toList()
    }

    // =====================================================
    // Source 3: AlienVault OTX Implementation
    // =====================================================

    private suspend fun fetchFromAlienVault(
        domain: String,
        onProgress: ((FetchProgress) -> Unit)?
    ): List<String> {
        onProgress?.invoke(FetchProgress(
            phase = "Querying threat intelligence...",
            progress = 0.1f,
            source = Source.ALIENVAULT.displayName
        ))

        val subdomains = mutableSetOf<String>()

        try {
            val response = alienVaultApi.getSubdomains(domain)

            if (response?.passiveDns == null) {
                throw Exception("Empty response")
            }

            for (entry in response.passiveDns) {
                val hostname = entry.hostname?.trim() ?: continue

                // Must end with target domain
                if (hostname.isNotEmpty() &&
                    hostname.endsWith(domain, ignoreCase = true)) {
                    subdomains.add(hostname)
                }
            }

            onProgress?.invoke(FetchProgress(
                phase = "Found ${subdomains.size} raw subdomains",
                progress = 0.2f,
                source = Source.ALIENVAULT.displayName
            ))

        } catch (e: Exception) {
            throw Exception("AlienVault: ${e.message}")
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

    suspend fun fetchSubdomains(
        domain: String,
        onProgress: ((FetchProgress) -> Unit)? = null
    ): FetchResult {
        return fetchFromSource(domain, Source.CRT_SH, onProgress)
    }
}
