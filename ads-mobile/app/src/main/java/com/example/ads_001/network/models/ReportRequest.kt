package com.example.ads_001.network.models

import com.google.gson.annotations.SerializedName

data class ReportRequest(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("status") val status: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("battery_level") val batteryLevel: Int? = null
)
