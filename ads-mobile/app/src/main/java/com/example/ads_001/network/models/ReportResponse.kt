package com.example.ads_001.network.models

import com.google.gson.annotations.SerializedName

data class ReportResponse(
    @SerializedName("id") val id: String,
    @SerializedName("message") val message: String
)
