package com.example.ads_001

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

class PendingEarthquakeTriggerStore(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun addTrigger(trigger: PendingEarthquakeTrigger): Boolean = synchronized(lock) {
        val activeTriggers = loadTriggers().filterNot(::isExpired).toMutableList()
        if (activeTriggers.any { it.triggerId == trigger.triggerId }) {
            saveTriggers(activeTriggers)
            return false
        }

        activeTriggers.add(trigger)
        saveTriggers(activeTriggers.sortedBy { it.createdAtEpochMs })
        true
    }

    fun getPendingTriggers(): List<PendingEarthquakeTrigger> = synchronized(lock) {
        val activeTriggers = loadTriggers()
            .filterNot(::isExpired)
            .sortedBy { it.createdAtEpochMs }
        saveTriggers(activeTriggers)
        activeTriggers
    }

    fun removeTrigger(triggerId: String): Boolean = synchronized(lock) {
        val currentTriggers = loadTriggers().filterNot(::isExpired)
        val remainingTriggers = currentTriggers.filterNot { it.triggerId == triggerId }
        val removed = remainingTriggers.size != currentTriggers.size
        if (removed) {
            saveTriggers(remainingTriggers)
        } else {
            saveTriggers(currentTriggers)
        }
        removed
    }

    private fun loadTriggers(): List<PendingEarthquakeTrigger> {
        val json = prefs.getString(KEY_PENDING_TRIGGERS, null) ?: return emptyList()
        val type = object : TypeToken<List<PendingEarthquakeTrigger>>() {}.type
        return runCatching { gson.fromJson<List<PendingEarthquakeTrigger>>(json, type) }
            .getOrNull()
            .orEmpty()
    }

    private fun saveTriggers(triggers: List<PendingEarthquakeTrigger>) {
        prefs.edit().putString(KEY_PENDING_TRIGGERS, gson.toJson(triggers)).apply()
    }

    private fun isExpired(trigger: PendingEarthquakeTrigger): Boolean {
        return System.currentTimeMillis() - trigger.createdAtEpochMs > TRIGGER_TTL_MS
    }

    companion object {
        private const val PREFS_NAME = "ADS_PENDING_EARTHQUAKE_TRIGGERS"
        private const val KEY_PENDING_TRIGGERS = "pending_earthquake_triggers"
        private const val TRIGGER_TTL_MS = 24L * 60L * 60L * 1000L

        private val gson = Gson()
        private val lock = Any()
    }
}

data class PendingEarthquakeTrigger(
    val triggerId: String,
    val createdAtEpochMs: Long,
    val intensityScore: Float = 0f
) {
    init {
        require(triggerId.isNotBlank()) { "triggerId cannot be blank" }
        require(intensityScore >= 0f) { "intensityScore cannot be negative" }
    }

    companion object {
        fun create(
            createdAtEpochMs: Long = System.currentTimeMillis(),
            intensityScore: Float = 0f
        ): PendingEarthquakeTrigger {
            return PendingEarthquakeTrigger(
                triggerId = UUID.randomUUID().toString(),
                createdAtEpochMs = createdAtEpochMs,
                intensityScore = intensityScore
            )
        }
    }
}
