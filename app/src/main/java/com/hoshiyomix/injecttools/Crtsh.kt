package com.hoshiyomix.injecttools

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.TreeSet
import java.util.concurrent.TimeUnit

object Crtsh {
    data class CrtShEntry(
        @SerializedName("name_value") val nameValue: String
    )

    interface CrtShApi {
        @GET("/")
        suspend fun search(
            @Query("q") query: String,
            @Query("output") output: String = "json"
        ): List<CrtShEntry>
    }

    private val api: CrtShApi by lazy {
        // Custom OkHttp client with longer timeouts and User-Agent
        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS) // crt.sh can be slow
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
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

    suspend fun fetchSubdomains(domain: String): List<String> {
        try {
            Logger.log("Connecting to crt.sh for $domain...")
            // Using query format consistent with Rust: %.domain
            val entries = api.search(query = "%.$domain")
            
            if (entries.isEmpty()) {
                Logger.log("crt.sh returned 0 entries (empty response)")
            }

            val subdomains = TreeSet<String>() // Auto-sorted and unique
            
            for (entry in entries) {
                // Handle potentially multiple domains in one entry (separated by newline)
                val names = entry.nameValue.split("\n")
                for (name in names) {
                    val cleaned = name.trim().replace("*.", "")
                    // Filter logic from Rust: ends with domain and no spaces
                    if (cleaned.endsWith(domain) && !cleaned.contains(" ")) {
                        subdomains.add(cleaned)
                    }
                }
            }
            return subdomains.toList()
        } catch (e: Exception) {
            e.printStackTrace()
            Logger.log("Crtsh Error: ${e.message}")
            if (e.message?.contains("timeout") == true) {
                Logger.log("Tip: crt.sh is slow. Try a smaller domain or try again later.")
            }
            return emptyList()
        }
    }
}
