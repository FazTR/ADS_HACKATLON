package com.example.ads_001

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

class SharedPrefsHelper(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("ADS_PREFS", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_CACHED_LATITUDE = "cached_latitude"
        private const val KEY_CACHED_LONGITUDE = "cached_longitude"
        private const val KEY_CACHED_LOCATION_TIME_MS = "cached_location_time_ms"
        private const val KEY_CACHED_LOCATION_ACCURACY = "cached_location_accuracy"
        private const val KEY_RELAY_HOTSPOT_SSID = "relay_hotspot_ssid"
        private const val KEY_RELAY_HOTSPOT_PASSPHRASE = "relay_hotspot_passphrase"
        private const val KEY_RELAY_HOTSPOT_HOST_DEVICE_ID = "relay_hotspot_host_device_id"
        private const val KEY_RELAY_HOTSPOT_CREATED_AT_MS = "relay_hotspot_created_at_ms"
        private const val KEY_LAST_VALID_BATTERY_LEVEL = "last_valid_battery_level"
    }

    fun isRegistered(): Boolean {
        return prefs.getBoolean("is_registered", false)
    }

    fun setRegistered(status: Boolean) {
        prefs.edit().putBoolean("is_registered", status).apply()
    }

    fun getDeviceId(): String {
        var deviceId = prefs.getString("device_id", null)
        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString()
            prefs.edit().putString("device_id", deviceId).apply()
        }
        return deviceId
    }

    fun saveUserProfile(fullName: String, birthDate: String, address: String) {
        prefs.edit()
            .putString("full_name", fullName)
            .putString("birth_date", birthDate)
            .putString("address", address)
            .apply()
    }

    fun getUserProfile(): UserProfile {
        return UserProfile(
            fullName = prefs.getString("full_name", "") ?: "",
            birthDate = prefs.getString("birth_date", "1985-01-01") ?: "1985-01-01",
            address = prefs.getString("address", "") ?: ""
        )
    }

    fun saveCachedLocation(latitude: Double, longitude: Double, timestampMs: Long, accuracyMeters: Float) {
        prefs.edit()
            .putLong(KEY_CACHED_LATITUDE, java.lang.Double.doubleToRawLongBits(latitude))
            .putLong(KEY_CACHED_LONGITUDE, java.lang.Double.doubleToRawLongBits(longitude))
            .putLong(KEY_CACHED_LOCATION_TIME_MS, timestampMs)
            .putFloat(KEY_CACHED_LOCATION_ACCURACY, accuracyMeters)
            .apply()
    }

    fun getCachedLocation(): CachedLocation? {
        if (!prefs.contains(KEY_CACHED_LATITUDE) || !prefs.contains(KEY_CACHED_LONGITUDE)) {
            return null
        }

        val latitude = java.lang.Double.longBitsToDouble(
            prefs.getLong(KEY_CACHED_LATITUDE, 0L)
        )
        val longitude = java.lang.Double.longBitsToDouble(
            prefs.getLong(KEY_CACHED_LONGITUDE, 0L)
        )
        val timestampMs = prefs.getLong(KEY_CACHED_LOCATION_TIME_MS, 0L)
        val accuracyMeters = prefs.getFloat(KEY_CACHED_LOCATION_ACCURACY, Float.MAX_VALUE)

        return CachedLocation(
            latitude = latitude,
            longitude = longitude,
            timestampMs = timestampMs,
            accuracyMeters = accuracyMeters
        )
    }

    fun saveRelayHotspot(info: HotspotRelayInfo) {
        prefs.edit()
            .putString(KEY_RELAY_HOTSPOT_SSID, info.ssid)
            .putString(KEY_RELAY_HOTSPOT_PASSPHRASE, info.passphrase)
            .putString(KEY_RELAY_HOTSPOT_HOST_DEVICE_ID, info.hostDeviceId)
            .putLong(KEY_RELAY_HOTSPOT_CREATED_AT_MS, info.createdAtEpochMs)
            .apply()
    }

    fun getRelayHotspot(): HotspotRelayInfo? {
        val ssid = prefs.getString(KEY_RELAY_HOTSPOT_SSID, null) ?: return null
        val passphrase = prefs.getString(KEY_RELAY_HOTSPOT_PASSPHRASE, null) ?: return null
        val hostDeviceId = prefs.getString(KEY_RELAY_HOTSPOT_HOST_DEVICE_ID, null) ?: return null
        val createdAtEpochMs = prefs.getLong(KEY_RELAY_HOTSPOT_CREATED_AT_MS, 0L)
        return HotspotRelayInfo(
            ssid = ssid,
            passphrase = passphrase,
            hostDeviceId = hostDeviceId,
            createdAtEpochMs = createdAtEpochMs
        )
    }

    fun clearRelayHotspot() {
        prefs.edit()
            .remove(KEY_RELAY_HOTSPOT_SSID)
            .remove(KEY_RELAY_HOTSPOT_PASSPHRASE)
            .remove(KEY_RELAY_HOTSPOT_HOST_DEVICE_ID)
            .remove(KEY_RELAY_HOTSPOT_CREATED_AT_MS)
            .apply()
    }

    fun saveLastValidBatteryLevel(level: Int) {
        if (level !in 1..100) return
        prefs.edit()
            .putInt(KEY_LAST_VALID_BATTERY_LEVEL, level)
            .apply()
    }

    fun getLastValidBatteryLevel(): Int? {
        val level = prefs.getInt(KEY_LAST_VALID_BATTERY_LEVEL, -1)
        return level.takeIf { it in 1..100 }
    }
}

data class UserProfile(
    val fullName: String,
    val birthDate: String,
    val address: String
)

data class CachedLocation(
    val latitude: Double,
    val longitude: Double,
    val timestampMs: Long,
    val accuracyMeters: Float
)
