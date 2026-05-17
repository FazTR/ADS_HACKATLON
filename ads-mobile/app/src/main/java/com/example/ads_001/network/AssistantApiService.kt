package com.example.ads_001.network

import com.example.ads_001.network.models.ChatCompletionsRequest
import com.example.ads_001.network.models.ChatCompletionsResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AssistantApiService {
    @POST("v1/chat/completions")
    suspend fun createChatCompletion(
        @Body request: ChatCompletionsRequest
    ): Response<ChatCompletionsResponse>
}
