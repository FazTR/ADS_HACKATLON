package com.example.ads_001.network

import com.example.ads_001.network.models.TurkiyeApiResponse
import retrofit2.Response
import retrofit2.http.GET

interface TurkiyeApiService {
    @GET("api/v1/provinces")
    suspend fun getProvinces(): Response<TurkiyeApiResponse>
}
