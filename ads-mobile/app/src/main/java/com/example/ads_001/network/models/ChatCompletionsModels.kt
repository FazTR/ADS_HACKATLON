package com.example.ads_001.network.models

import com.google.gson.annotations.SerializedName

data class ChatCompletionsRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.2,
    @SerializedName("max_tokens")
    val maxTokens: Int = 480
)

data class ChatMessage(
    val role: String,
    val content: String
)

data class ChatCompletionsResponse(
    val choices: List<ChatChoice> = emptyList()
)

data class ChatChoice(
    val message: ChatMessage? = null,
    @SerializedName("finish_reason")
    val finishReason: String? = null
)
