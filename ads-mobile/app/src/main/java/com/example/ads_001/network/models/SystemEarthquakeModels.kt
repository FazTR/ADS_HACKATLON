package com.example.ads_001.network.models

import com.google.gson.annotations.SerializedName

data class SystemEarthquakeEvent(
    val id: String,
    val magnitude: Double? = null,
    @SerializedName("depth_km")
    val depthKm: Double? = null,
    @SerializedName("location_name")
    val locationName: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerializedName(value = "created_at", alternate = ["reported_at", "detected_at", "broadcasted_at"])
    val createdAt: String? = null
)
