package com.example.ads_001

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receives Google's system-level earthquake alert broadcast.
 * Works on Android 12+ devices with Google Play Services.
 * Declared statically in AndroidManifest so it fires even when the app is closed.
 */
class EarthquakeBroadcastReceiver : BroadcastReceiver() {

    companion object {
        // Google's earthquake alert action (Android 12+, API 31+)
        const val EARTHQUAKE_ACTION = "android.location.action.EARTHQUAKE_HAPPENED"
        const val TAG = "EarthquakeBroadcast"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == EARTHQUAKE_ACTION) {
            Log.d(TAG, "Google Earthquake Alert received!")
            EarthquakeAlertManager.onLocalEarthquakeDetected(context, "Google Deprem Uyarı Sistemi")
        }
    }
}
