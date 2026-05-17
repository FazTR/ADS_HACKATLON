package com.example.ads_001

import com.example.ads_001.network.models.EarthquakeReportRequest
import java.util.UUID

data class MeshEarthquakeReport(
    val messageId: String,
    val originDeviceId: String,
    val latitude: Double,
    val longitude: Double,
    val createdAtEpochMs: Long,
    val intensity: Float = 0f
) {
    init {
        require(messageId.isNotBlank()) { "messageId cannot be blank" }
        require(originDeviceId.isNotBlank()) { "originDeviceId cannot be blank" }
        require(!(latitude == 0.0 && longitude == 0.0)) { "Report coordinates cannot be 0,0" }
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0) {
            "Report coordinates are out of range"
        }
        require(intensity >= 0f) { "Report intensity cannot be negative" }
    }

    fun toApiRequest(): EarthquakeReportRequest {
        return EarthquakeReportRequest(
            deviceId = originDeviceId,
            latitude = latitude,
            longitude = longitude,
            intensity = intensity
        )
    }

    companion object {
        fun create(
            originDeviceId: String,
            latitude: Double,
            longitude: Double,
            createdAtEpochMs: Long = System.currentTimeMillis(),
            intensity: Float = 0f
        ): MeshEarthquakeReport {
            return MeshEarthquakeReport(
                messageId = UUID.randomUUID().toString(),
                originDeviceId = originDeviceId,
                latitude = latitude,
                longitude = longitude,
                createdAtEpochMs = createdAtEpochMs,
                intensity = intensity
            )
        }
    }
}

data class MeshRelayEnvelope(
    val type: String,
    val report: MeshEarthquakeReport? = null,
    val acknowledgedMessageId: String? = null,
    val hotspot: HotspotRelayInfo? = null
) {
    companion object {
        const val TYPE_REPORT = "report"
        const val TYPE_ACK = "ack"
        const val TYPE_HOTSPOT = "hotspot"
    }
}

data class HotspotRelayInfo(
    val ssid: String,
    val passphrase: String,
    val hostDeviceId: String,
    val createdAtEpochMs: Long
) {
    init {
        require(ssid.isNotBlank()) { "Hotspot SSID cannot be blank" }
        require(passphrase.length >= 8) { "Hotspot passphrase must be at least 8 chars" }
        require(hostDeviceId.isNotBlank()) { "Hotspot hostDeviceId cannot be blank" }
    }
}
