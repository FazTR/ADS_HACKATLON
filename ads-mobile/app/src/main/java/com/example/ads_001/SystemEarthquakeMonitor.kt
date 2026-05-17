package com.example.ads_001

import android.content.Context
import android.util.Log
import com.example.ads_001.network.AdsApiClient
import com.example.ads_001.network.models.SystemEarthquakeEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object SystemEarthquakeMonitor {
    private const val TAG = "SystemEqMonitor"
    private const val POLL_INTERVAL_MS = 5_000L
    private const val PREFS_NAME = "ADS_SYSTEM_EARTHQUAKES"
    private const val KEY_LAST_SEEN_EARTHQUAKE_ID = "last_seen_earthquake_id"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var monitorJob: Job? = null

    fun ensureRunning(context: Context) {
        if (monitorJob?.isActive == true) return

        val appContext = context.applicationContext
        monitorJob = scope.launch {
            while (isActive) {
                runCatching {
                    pollOnce(appContext)
                }.onFailure { e ->
                    Log.w(TAG, "System earthquake poll failed: ${e.message}")
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
    }

    private suspend fun pollOnce(context: Context) {
        val response = AdsApiClient.apiService.getSystemEarthquakes()
        if (!response.isSuccessful) {
            Log.w(TAG, "System earthquake poll returned ${response.code()}")
            return
        }

        val latestEvent = response.body().orEmpty().firstOrNull() ?: return
        SystemEarthquakeTracker.update(context, latestEvent)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastSeenId = prefs.getString(KEY_LAST_SEEN_EARTHQUAKE_ID, null)

        if (lastSeenId == null) {
            prefs.edit().putString(KEY_LAST_SEEN_EARTHQUAKE_ID, latestEvent.id).apply()
            Log.d(TAG, "Initialized earthquake monitor baseline with ${latestEvent.id}")
            return
        }

        if (latestEvent.id == lastSeenId) {
            return
        }

        prefs.edit().putString(KEY_LAST_SEEN_EARTHQUAKE_ID, latestEvent.id).apply()
        Log.w(TAG, "New system earthquake received: ${latestEvent.id}")
        EarthquakeAlertManager.onSystemEarthquakeReceived(context, latestEvent)
    }
}
