package com.example.ads_001.network

import com.example.ads_001.network.models.NearbyResponse
import com.example.ads_001.network.models.ReportRequest
import com.example.ads_001.network.models.SystemEarthquakeEvent
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

import com.example.ads_001.network.models.RegisterRequest
import com.example.ads_001.network.models.RegisterResponse

interface AdsApiService {
    @POST("api/v1/mobile/register")
    suspend fun registerUser(@Body request: RegisterRequest): Response<RegisterResponse>

    @GET("api/v1/mobile/profile/{device_id}")
    suspend fun getProfile(@retrofit2.http.Path("device_id") deviceId: String): Response<com.example.ads_001.network.models.UserProfileResponse>

    @POST("api/v1/mobile/report")
    suspend fun reportStatus(@Body request: ReportRequest): Response<ResponseBody>

    @GET("api/v1/mobile/nearby")
    suspend fun getNearbyVictims(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("radius_m") radiusM: Double
    ): Response<List<NearbyResponse>>

    @POST("api/v1/mobile/earthquake_report")
    suspend fun sendEarthquakeReport(@Body request: com.example.ads_001.network.models.EarthquakeReportRequest): Response<com.example.ads_001.network.models.EarthquakeReportResponse>

    @GET("api/v1/system/earthquakes")
    suspend fun getSystemEarthquakes(): Response<List<SystemEarthquakeEvent>>

    @GET("health")
    suspend fun checkHealth(): Response<ResponseBody>
}
