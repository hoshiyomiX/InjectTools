package com.hoshiyomix.injecttools.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

data class CrtShEntry(
    val name_value: String
)

interface CrtShApi {
    @GET("/")
    suspend fun search(
        @Query("q") query: String,
        @Query("output") output: String = "json"
    ): List<CrtShEntry>
}

object CrtShClient {
    private const val BASE_URL = "https://crt.sh/"

    val api: CrtShApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CrtShApi::class.java)
    }
}
