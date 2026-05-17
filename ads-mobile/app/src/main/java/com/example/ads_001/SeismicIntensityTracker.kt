package com.example.ads_001

import android.content.Context
import android.content.Intent
import kotlin.math.roundToInt

enum class SeismicIntensityLevel {
    CALM,
    LIGHT,
    MODERATE,
    STRONG,
    SEVERE
}

data class SeismicIntensityStatus(
    val score: Float,
    val label: String,
    val level: SeismicIntensityLevel
)

object SeismicIntensityTracker {
    const val ACTION_STATUS_CHANGED = "com.example.ads_001.ACTION_SEISMIC_INTENSITY_CHANGED"

    private const val PREFS_NAME = "ADS_SEISMIC_INTENSITY"
    private const val KEY_SCORE = "score"
    private const val KEY_LABEL = "label"
    private const val KEY_LEVEL = "level"
    private const val DEFAULT_SCORE = 0f
    private const val DEFAULT_LABEL = "0.0 · Sakin"

    fun update(context: Context, score: Float, level: SeismicIntensityLevel) {
        val normalizedScore = score.coerceIn(0f, 10f)
        val label = formatLabel(normalizedScore, level)
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val previous = getCurrentStatus(appContext)
        if (previous.label == label && previous.level == level) {
            return
        }

        prefs.edit()
            .putFloat(KEY_SCORE, normalizedScore)
            .putString(KEY_LABEL, label)
            .putString(KEY_LEVEL, level.name)
            .apply()

        val intent = Intent(ACTION_STATUS_CHANGED)
            .setPackage(appContext.packageName)
            .putExtra("score", normalizedScore)
            .putExtra("label", label)
            .putExtra("level", level.name)
        appContext.sendBroadcast(intent)
    }

    fun reset(context: Context) {
        update(context, 0f, SeismicIntensityLevel.CALM)
    }

    fun getCurrentStatus(context: Context): SeismicIntensityStatus {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val score = prefs.getFloat(KEY_SCORE, DEFAULT_SCORE)
        val label = prefs.getString(KEY_LABEL, DEFAULT_LABEL) ?: DEFAULT_LABEL
        val level = prefs.getString(KEY_LEVEL, SeismicIntensityLevel.CALM.name)
            ?.let { runCatching { SeismicIntensityLevel.valueOf(it) }.getOrDefault(SeismicIntensityLevel.CALM) }
            ?: SeismicIntensityLevel.CALM
        return SeismicIntensityStatus(score = score, label = label, level = level)
    }

    private fun formatLabel(score: Float, level: SeismicIntensityLevel): String {
        val rounded = ((score * 10f).roundToInt() / 10f)
        val levelText = when (level) {
            SeismicIntensityLevel.CALM -> "Sakin"
            SeismicIntensityLevel.LIGHT -> "Çok Hafif"
            SeismicIntensityLevel.MODERATE -> "Hafif"
            SeismicIntensityLevel.STRONG -> "Orta"
            SeismicIntensityLevel.SEVERE -> "Kuvvetli"
        }
        return String.format("%.1f · %s", rounded, levelText)
    }
}
