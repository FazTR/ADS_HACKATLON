package com.example.ads_001.network.models

import com.google.gson.annotations.SerializedName

data class EarthquakeReportRequest(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("intensity") val intensity: Float = 0f
) {
    init {
        require(!(latitude == 0.0 && longitude == 0.0)) {
            "Earthquake report coordinates cannot be 0,0"
        }
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0) {
            "Earthquake report coordinates are out of range"
        }
        require(intensity >= 0f) {
            "Earthquake report intensity cannot be negative"
        }
    }
}

data class EarthquakeReportResponse(
    @SerializedName("id") val id: String,
    @SerializedName("message") val message: String,
    @SerializedName("triggered_alert") val triggeredAlert: Boolean
)
