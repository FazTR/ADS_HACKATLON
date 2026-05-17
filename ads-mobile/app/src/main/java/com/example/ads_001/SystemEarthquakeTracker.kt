package com.example.ads_001

import android.content.Context
import com.example.ads_001.network.models.SystemEarthquakeEvent

data class SystemEarthquakeSnapshot(
    val id: String,
    val summary: String,
    val createdAt: String?
)

object SystemEarthquakeTracker {
    private const val PREFS_NAME = "ADS_SYSTEM_EARTHQUAKE_TRACKER"
    private const val KEY_ID = "last_id"
    private const val KEY_SUMMARY = "last_summary"
    private const val KEY_CREATED_AT = "last_created_at"

    fun update(context: Context, event: SystemEarthquakeEvent) {
        val summary = buildSummary(event)
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ID, event.id)
            .putString(KEY_SUMMARY, summary)
            .putString(KEY_CREATED_AT, event.createdAt)
            .apply()
    }

    fun getLatest(context: Context): SystemEarthquakeSnapshot? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val id = prefs.getString(KEY_ID, null) ?: return null
        val summary = prefs.getString(KEY_SUMMARY, null) ?: return null
        val createdAt = prefs.getString(KEY_CREATED_AT, null)
        return SystemEarthquakeSnapshot(
            id = id,
            summary = summary,
            createdAt = createdAt
        )
    }

    private fun buildSummary(event: SystemEarthquakeEvent): String {
        val magnitudeText = event.magnitude?.let { "M%.1f".format(it) } ?: "Deprem"
        val locationText = event.locationName?.takeIf { it.isNotBlank() } ?: "Konum bilinmiyor"
        val depthText = event.depthKm?.let { "Derinlik %.1f km".format(it) }
        return listOf(magnitudeText, locationText, depthText)
            .filterNotNull()
            .joinToString(" • ")
    }
}
