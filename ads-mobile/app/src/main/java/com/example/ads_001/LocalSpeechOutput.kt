package com.example.ads_001

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

object LocalSpeechOutput {
    private const val PREFS_NAME = "ADS_SPEECH_OUTPUT"
    private const val KEY_ENABLED = "enabled"

    @Volatile
    private var textToSpeech: TextToSpeech? = null

    @Volatile
    private var ready = false

    @Volatile
    private var pendingUtterance: String? = null

    fun isEnabled(context: Context): Boolean {
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
        if (!enabled) {
            stop()
        }
    }

    fun speak(context: Context, text: String) {
        if (!isEnabled(context)) return
        val utterance = text.trim()
        if (utterance.isBlank()) return

        val tts = ensureInitialized(context)
        if (ready) {
            tts.speak(utterance, TextToSpeech.QUEUE_FLUSH, null, "ads_reply")
        } else {
            pendingUtterance = utterance
        }
    }

    fun stop() {
        textToSpeech?.stop()
    }

    fun shutdown() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        ready = false
        pendingUtterance = null
    }

    private fun ensureInitialized(context: Context): TextToSpeech {
        textToSpeech?.let { return it }

        val appContext = context.applicationContext
        val tts = TextToSpeech(appContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (!ready) return@TextToSpeech

            val locale = Locale("tr", "TR")
            val availability = textToSpeech?.setLanguage(locale) ?: TextToSpeech.LANG_NOT_SUPPORTED
            if (availability == TextToSpeech.LANG_MISSING_DATA || availability == TextToSpeech.LANG_NOT_SUPPORTED) {
                ready = false
                return@TextToSpeech
            }

            textToSpeech?.setSpeechRate(1.0f)
            pendingUtterance?.let { queued ->
                pendingUtterance = null
                textToSpeech?.speak(queued, TextToSpeech.QUEUE_FLUSH, null, "ads_reply")
            }
        }
        textToSpeech = tts
        return tts
    }
}
