package com.example.ads_001

import android.content.Context
import android.content.SharedPreferences

class PendingVictimStatusStore(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun save(statusApiValue: String) {
        prefs.edit()
            .putString(KEY_STATUS_API_VALUE, statusApiValue)
            .putLong(KEY_QUEUED_AT_MS, System.currentTimeMillis())
            .apply()
    }

    fun getPendingStatusApiValue(): String? {
        return prefs.getString(KEY_STATUS_API_VALUE, null)?.takeIf { it.isNotBlank() }
    }

    fun clear() {
        prefs.edit()
            .remove(KEY_STATUS_API_VALUE)
            .remove(KEY_QUEUED_AT_MS)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "ADS_PENDING_VICTIM_STATUS"
        private const val KEY_STATUS_API_VALUE = "status_api_value"
        private const val KEY_QUEUED_AT_MS = "queued_at_ms"
    }
}
