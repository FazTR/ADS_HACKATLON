package com.example.ads_001.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object TurkiyeApiClient {
    private const val BASE_URL = "https://turkiyeapi.dev/"

    private val okHttpClient = OkHttpClient.Builder().build()

    val apiService: TurkiyeApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TurkiyeApiService::class.java)
    }
}
