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
import java.io.IOException

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
        val client = OkHttpClient.Builder()
            .connectTimeout(120, TimeUnit.SECONDS) // Increased to 120s
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

    suspend fun fetchSubdomains(domain: String): List<String> = withContext(Dispatchers.IO) {
        try {
            Logger.log("Connecting to crt.sh for $domain...")
            
            // crt.sh query format: %.domain
            val entries = api.search(query = "%.$domain")
            
            if (entries.isEmpty()) {
                Logger.log("crt.sh returned 0 entries (empty response)")
            } else {
                Logger.log("crt.sh returned ${entries.size} raw entries")
            }

            val subdomains = TreeSet<String>()
            
            for (entry in entries) {
                val names = entry.nameValue.split("\n")
                for (name in names) {
                    val cleaned = name.trim().replace("*.", "")
                    if (cleaned.endsWith(domain) && !cleaned.contains(" ")) {
                        subdomains.add(cleaned)
                    }
                }
            }
            return@withContext subdomains.toList()
        } catch (e: Exception) {
            e.printStackTrace()
            val errorMsg = e.message ?: "Unknown error"
            Logger.log("Crtsh Error: $errorMsg")
            
            if (errorMsg.contains("timeout") || errorMsg.contains("cancelled")) {
                Logger.log("Tip: crt.sh is slow or overloaded. Try again later.")
            }
            return@withContext emptyList()
        }
    }
}
