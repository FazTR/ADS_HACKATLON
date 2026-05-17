package com.example.ads_001.network.models

import com.google.gson.annotations.SerializedName

data class NearbyResponse(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("status") val status: String,
    @SerializedName("distance_m") val distanceM: Double
)
