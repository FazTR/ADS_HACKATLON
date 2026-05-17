package com.example.ads_001

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Starts the EarthquakeDetectionService automatically after device reboot.
 * Requires RECEIVE_BOOT_COMPLETED permission in AndroidManifest.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") { // Huawei fast boot

            Log.d("BootReceiver", "Boot detected — starting EarthquakeDetectionService")
            startEarthquakeService(context)
        }
    }

    companion object {
        fun startEarthquakeService(context: Context) {
            try {
                val serviceIntent = Intent(context, EarthquakeDetectionService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: Exception) {
                Log.e("BootReceiver", "Failed to start service: ${e.message}")
            }
        }
    }
}
