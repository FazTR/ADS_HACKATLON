package com.example.ads_001

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class EarthquakeRelayStore(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun addPendingReport(report: MeshEarthquakeReport): Boolean = synchronized(lock) {
        val pendingReports = loadPendingReports().filterNot(::isExpired).toMutableList()
        val deliveredIds = loadDeliveredMessageIds().toMutableList()

        if (deliveredIds.contains(report.messageId)) {
            return false
        }

        val alreadyQueued = pendingReports.any { it.messageId == report.messageId }
        if (alreadyQueued) {
            savePendingReports(pendingReports)
            return false
        }

        pendingReports.add(report)
        savePendingReports(pendingReports)
        true
    }

    fun getPendingReports(): List<MeshEarthquakeReport> = synchronized(lock) {
        val activeReports = loadPendingReports().filterNot(::isExpired)
        savePendingReports(activeReports)
        activeReports
    }

    fun markDelivered(messageId: String): Boolean = synchronized(lock) {
        val currentPending = loadPendingReports().filterNot(::isExpired)
        val remainingPending = currentPending.filterNot { it.messageId == messageId }
        val removedPending = remainingPending.size != currentPending.size

        val deliveredIds = loadDeliveredMessageIds().toMutableList()
        val alreadyDelivered = deliveredIds.contains(messageId)
        if (!alreadyDelivered) {
            deliveredIds.add(0, messageId)
        }

        savePendingReports(remainingPending)
        saveDeliveredMessageIds(deliveredIds.distinct().take(MAX_DELIVERED_IDS))
        removedPending || !alreadyDelivered
    }

    fun getDeliveredMessageIds(): List<String> = synchronized(lock) {
        val deliveredIds = loadDeliveredMessageIds().distinct().take(MAX_DELIVERED_IDS)
        saveDeliveredMessageIds(deliveredIds)
        deliveredIds
    }

    private fun loadPendingReports(): List<MeshEarthquakeReport> {
        val json = prefs.getString(KEY_PENDING_REPORTS, null) ?: return emptyList()
        val type = object : TypeToken<List<MeshEarthquakeReport>>() {}.type
        return runCatching { gson.fromJson<List<MeshEarthquakeReport>>(json, type) }
            .getOrNull()
            .orEmpty()
    }

    private fun savePendingReports(reports: List<MeshEarthquakeReport>) {
        prefs.edit().putString(KEY_PENDING_REPORTS, gson.toJson(reports)).apply()
    }

    private fun loadDeliveredMessageIds(): List<String> {
        val json = prefs.getString(KEY_DELIVERED_IDS, null) ?: return emptyList()
        val type = object : TypeToken<List<String>>() {}.type
        return runCatching { gson.fromJson<List<String>>(json, type) }
            .getOrNull()
            .orEmpty()
    }

    private fun saveDeliveredMessageIds(messageIds: List<String>) {
        prefs.edit().putString(KEY_DELIVERED_IDS, gson.toJson(messageIds)).apply()
    }

    private fun isExpired(report: MeshEarthquakeReport): Boolean {
        return System.currentTimeMillis() - report.createdAtEpochMs > REPORT_TTL_MS
    }

    companion object {
        private const val PREFS_NAME = "ADS_RELAY_STORE"
        private const val KEY_PENDING_REPORTS = "pending_reports"
        private const val KEY_DELIVERED_IDS = "delivered_ids"
        private const val MAX_DELIVERED_IDS = 500
        private const val REPORT_TTL_MS = 24L * 60L * 60L * 1000L

        private val gson = Gson()
        private val lock = Any()
    }
}
