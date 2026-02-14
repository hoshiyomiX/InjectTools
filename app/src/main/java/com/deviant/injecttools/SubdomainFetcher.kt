package com.deviant.injecttools

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.TreeSet
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.Inet4Address

/**
 * Subdomain fetcher using HackerTarget API.
 * Returns plain text: subdomain.domain.com,IP
 */
object SubdomainFetcher {

    data class FetchProgress(
        val phase: String,
        val progress: Float,
        val current: Int = 0,
        val total: Int = 0
    )

    data class FetchResult(
        val subdomains: List<String>,
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
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Accept", "text/plain, */*")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    // =====================================================
    // Main Fetch Function
    // =====================================================

    suspend fun fetchSubdomains(
        domain: String,
        onProgress: ((FetchProgress) -> Unit)? = null
    ): FetchResult = withContext(Dispatchers.IO) {

        val cleanDomain = domain.lowercase().trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("www.")
            .split("/").first()

        onProgress?.invoke(FetchProgress(
            phase = "Connecting to HackerTarget...",
            progress = 0.05f
        ))

        try {
            val rawSubdomains = fetchFromHackerTarget(cleanDomain, onProgress)

            if (rawSubdomains.isEmpty()) {
                return@withContext FetchResult(
                    subdomains = emptyList(),
                    error = "No subdomains found for $cleanDomain"
                )
            }

            // DNS Validation
            val validSubdomains = validateSubdomains(rawSubdomains, cleanDomain, onProgress)

            FetchResult(subdomains = validSubdomains)

        } catch (e: Exception) {
            FetchResult(
                subdomains = emptyList(),
                error = e.message ?: "Unknown error"
            )
        }
    }

    // =====================================================
    // HackerTarget API (Plain Text)
    // =====================================================

    private suspend fun fetchFromHackerTarget(
        domain: String,
        onProgress: ((FetchProgress) -> Unit)?
    ): List<String> {
        onProgress?.invoke(FetchProgress(
            phase = "Querying DNS records...",
            progress = 0.1f
        ))

        val subdomains = mutableSetOf<String>()

        val request = Request.Builder()
            .url("https://api.hackertarget.com/hostsearch/?q=$domain")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}")
            }

            val responseBody = response.body?.string() ?: ""

            if (responseBody.isBlank()) {
                throw Exception("Empty response")
            }

            // Parse plain text: subdomain.domain.com,IP
            responseBody.lines().forEach { line ->
                val trimmedLine = line.trim()
                if (trimmedLine.isEmpty()) return@forEach

                val commaIndex = trimmedLine.indexOf(',')
                val subdomain = if (commaIndex > 0) {
                    trimmedLine.substring(0, commaIndex).trim()
                } else {
                    trimmedLine
                }

                if (subdomain.isNotEmpty() &&
                    subdomain.endsWith(domain, ignoreCase = true) &&
                    !subdomain.contains("error", ignoreCase = true)) {
                    subdomains.add(subdomain)
                }
            }
        }

        onProgress?.invoke(FetchProgress(
            phase = "Found ${subdomains.size} raw subdomains",
            progress = 0.2f
        ))

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
            total = total
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
                    total = total
                ))
            }
        }

        onProgress?.invoke(FetchProgress(
            phase = "Complete! ${validSubdomains.size} valid subdomains",
            progress = 1.0f,
            current = validSubdomains.size,
            total = total
        ))

        validSubdomains
    }
}
