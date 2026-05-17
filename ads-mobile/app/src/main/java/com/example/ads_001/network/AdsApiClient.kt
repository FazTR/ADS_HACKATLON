package com.example.ads_001.network

import com.example.ads_001.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object AdsApiClient {
    private val API_KEY: String = BuildConfig.ADS_API_KEY
    private val baseUrl: String = BuildConfig.ADS_BASE_URL.ensureTrailingSlash()

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
        redactHeader("x-api-key")
    }

    private val httpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val originalRequest = chain.request()
            // Add API Key to all endpoints except /health
            val requestBuilder = originalRequest.newBuilder()
            
            if (!originalRequest.url.encodedPath.contains("/health")) {
                requestBuilder.header("x-api-key", API_KEY)
            }
            
            chain.proceed(requestBuilder.build())
        }
        .addInterceptor(loggingInterceptor)
        .connectTimeout(3, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(6, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(GsonConverterFactory.create())
        .client(httpClient)
        .build()

    val apiService: AdsApiService by lazy {
        retrofit.create(AdsApiService::class.java)
    }

    private fun String.ensureTrailingSlash(): String {
        return if (endsWith("/")) this else "$this/"
    }
}
