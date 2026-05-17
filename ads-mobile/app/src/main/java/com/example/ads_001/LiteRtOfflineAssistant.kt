package com.example.ads_001

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.LogSeverity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object LiteRtOfflineAssistant {
    private const val TAG = "LiteRtOfflineAssistant"
    private val mutex = Mutex()

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var loadedModelPath: String? = null

    suspend fun generateReply(context: Context, userInput: String, appContextSummary: String): String {
        val appContext = context.applicationContext
        val modelFile = OfflineModelManager.getModelFile(appContext)
        if (!modelFile.exists()) {
            return "Offline model yüklü değil."
        }

        return mutex.withLock {
            runCatching {
                ensureInitialized(appContext, modelFile.absolutePath)
                val prompt = buildPrompt(userInput, appContextSummary)
                conversation!!.sendMessage(prompt).toString().trim().ifBlank {
                    "Offline model boş yanıt döndürdü."
                }
            }.onFailure { error ->
                Log.w(TAG, "Offline generation failed: ${error.message}")
                unloadInternal()
            }.getOrElse {
                "Offline model şu an yanıt üretemedi."
            }
        }
    }

    fun unload() {
        runCatching {
            unloadInternal()
        }
    }

    private fun ensureInitialized(context: Context, modelPath: String) {
        if (engine != null && conversation != null && loadedModelPath == modelPath) {
            return
        }

        unloadInternal()

        Engine.setNativeMinLogSeverity(LogSeverity.ERROR)
        val newEngine = Engine(
            EngineConfig(
                modelPath = modelPath,
                backend = Backend.CPU(),
                cacheDir = context.cacheDir.absolutePath,
                maxNumTokens = 1024
            )
        )
        newEngine.initialize()

        val newConversation = newEngine.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(
                    "Sen ADS uygulamasındaki deprem odaklı offline Türkçe yardımcı asistansın. " +
                        "Kısa, açık ve sakin cevap ver. Bilmediğin şeyi uydurma. " +
                        "Cevapların en fazla 4 kısa cümle olsun."
                )
            )
        )

        engine = newEngine
        conversation = newConversation
        loadedModelPath = modelPath
    }

    private fun unloadInternal() {
        runCatching { conversation?.close() }
        runCatching { engine?.close() }
        conversation = null
        engine = null
        loadedModelPath = null
    }

    private fun buildPrompt(userInput: String, appContextSummary: String): String {
        return """
            Uygulama bağlamı:
            $appContextSummary

            Kullanıcı mesajı:
            $userInput
        """.trimIndent()
    }
}
