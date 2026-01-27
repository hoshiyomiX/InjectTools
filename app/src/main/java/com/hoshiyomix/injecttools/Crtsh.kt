package com.hoshiyomix.injecttools

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.TreeSet

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
        Retrofit.Builder()
            .baseUrl("https://crt.sh/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CrtShApi::class.java)
    }

    suspend fun fetchSubdomains(domain: String): List<String> {
        try {
            // Using query format consistent with Rust: %.domain
            val entries = api.search(query = "%.$domain")
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
            return emptyList()
        }
    }
}
