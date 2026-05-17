package com.example.ads_001

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService

object LocalSpeechRecognizer {
    private const val SAMPLE_RATE = 16_000.0f
    private val gson = Gson()
    private val mutex = Mutex()

    @Volatile
    private var model: Model? = null

    @Volatile
    private var loadedPath: String? = null

    @Volatile
    private var speechService: SpeechService? = null

    sealed class StartResult {
        object Started : StartResult()
        data class Unavailable(val message: String) : StartResult()
    }

    interface Listener {
        fun onListeningStarted()
        fun onPartialText(text: String)
        fun onFinalText(text: String)
        fun onError(message: String)
        fun onStopped()
    }

    suspend fun startListening(context: Context, listener: Listener): StartResult = mutex.withLock {
        if (speechService != null) {
            return@withLock StartResult.Unavailable("Dinleme zaten açık.")
        }

        if (!OfflineSpeechModelManager.ensurePrepared(context)) {
            return@withLock StartResult.Unavailable(OfflineSpeechModelManager.getStatusSummary(context))
        }

        val modelDir = OfflineSpeechModelManager.getModelDir(context)
            ?: return@withLock StartResult.Unavailable("${BuildConfig.OFFLINE_SPEECH_MODEL_LABEL} hazır değil.")

        val activeModel = ensureModelLoaded(modelDir.absolutePath)
        val recognizer = Recognizer(activeModel, SAMPLE_RATE)
        val partials = StringBuilder()

        val service = SpeechService(recognizer, SAMPLE_RATE)
        speechService = service
        service.startListening(object : RecognitionListener {
            override fun onPartialResult(hypothesis: String?) {
                val partial = hypothesis.parsePartialText()
                listener.onPartialText(partial)
            }

            override fun onResult(hypothesis: String?) {
                hypothesis.parseResultText()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { chunk ->
                        if (partials.isNotEmpty()) partials.append(' ')
                        partials.append(chunk)
                    }
            }

            override fun onFinalResult(hypothesis: String?) {
                val finalChunk = hypothesis.parseResultText()
                if (!finalChunk.isNullOrBlank()) {
                    if (partials.isNotEmpty()) partials.append(' ')
                    partials.append(finalChunk)
                }
                val finalText = partials.toString().trim()
                speechService = null
                if (finalText.isBlank()) {
                    listener.onError("Seni duyamadım. Yeniden konuşabilirsin.")
                } else {
                    listener.onFinalText(finalText)
                }
                listener.onStopped()
            }

            override fun onError(exception: Exception?) {
                speechService = null
                listener.onError(exception?.message ?: "Ses algılama sırasında hata oluştu.")
                listener.onStopped()
            }

            override fun onTimeout() {
                speechService = null
                val finalText = partials.toString().trim()
                if (finalText.isNotBlank()) {
                    listener.onFinalText(finalText)
                } else {
                    listener.onError("Konuşma süresi doldu. Yeniden deneyebilirsin.")
                }
                listener.onStopped()
            }
        })
        listener.onListeningStarted()
        return@withLock StartResult.Started
    }

    fun stopListening() {
        speechService?.stop()
    }

    fun cancelListening() {
        speechService?.cancel()
        speechService = null
    }

    fun isListening(): Boolean = speechService != null

    fun shutdown() {
        cancelListening()
        model?.close()
        model = null
        loadedPath = null
    }

    private fun ensureModelLoaded(path: String): Model {
        if (model != null && loadedPath == path) {
            return model!!
        }

        model?.close()
        model = Model(path)
        loadedPath = path
        return model!!
    }

    private fun String?.parsePartialText(): String {
        if (this.isNullOrBlank()) return ""
        return runCatching {
            gson.fromJson(this, VoskPartialResult::class.java).partial.orEmpty().trim()
        }.getOrDefault("")
    }

    private fun String?.parseResultText(): String? {
        if (this.isNullOrBlank()) return null
        return runCatching {
            gson.fromJson(this, VoskTextResult::class.java).text?.trim()
        }.getOrNull()
    }
}

private data class VoskPartialResult(
    val partial: String? = null
)

private data class VoskTextResult(
    val text: String? = null
)
