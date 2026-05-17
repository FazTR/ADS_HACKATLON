package com.example.ads_001

import android.content.Context
import android.content.Intent

enum class TransmissionStatusLevel {
    INFO,
    SUCCESS,
    WARNING,
    ERROR
}

data class TransmissionStatus(
    val message: String,
    val level: TransmissionStatusLevel
)

object TransmissionStatusTracker {
    const val ACTION_STATUS_CHANGED = "com.example.ads_001.ACTION_TRANSMISSION_STATUS_CHANGED"

    private const val PREFS_NAME = "ADS_TRANSMISSION_STATUS"
    private const val KEY_MESSAGE = "message"
    private const val KEY_LEVEL = "level"
    private const val EXTRA_MESSAGE = "extra_message"
    private const val EXTRA_LEVEL = "extra_level"
    private const val DEFAULT_MESSAGE = "Beklemede"

    fun update(context: Context, message: String, level: TransmissionStatusLevel) {
        val appContext = context.applicationContext
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MESSAGE, message)
            .putString(KEY_LEVEL, level.name)
            .apply()

        val intent = Intent(ACTION_STATUS_CHANGED)
            .setPackage(appContext.packageName)
            .putExtra(EXTRA_MESSAGE, message)
            .putExtra(EXTRA_LEVEL, level.name)
        appContext.sendBroadcast(intent)
    }

    fun ensureDefault(context: Context, message: String, level: TransmissionStatusLevel) {
        val currentStatus = getCurrentStatus(context)
        if (currentStatus.message == DEFAULT_MESSAGE) {
            update(context, message, level)
        }
    }

    fun getCurrentStatus(context: Context): TransmissionStatus {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val message = prefs.getString(KEY_MESSAGE, DEFAULT_MESSAGE) ?: DEFAULT_MESSAGE
        val levelName = prefs.getString(KEY_LEVEL, TransmissionStatusLevel.INFO.name)
        return TransmissionStatus(
            message = message,
            level = levelName?.let(::parseLevel) ?: TransmissionStatusLevel.INFO
        )
    }

    private fun parseLevel(value: String): TransmissionStatusLevel {
        return runCatching { TransmissionStatusLevel.valueOf(value) }
            .getOrDefault(TransmissionStatusLevel.INFO)
    }
}
