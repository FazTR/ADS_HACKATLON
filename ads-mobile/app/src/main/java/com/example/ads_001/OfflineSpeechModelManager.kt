package com.example.ads_001

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream

object OfflineSpeechModelManager {
    private const val MODELS_DIR = "offline_speech_models"
    private val prepareMutex = Mutex()

    @Volatile
    private var state: OfflineSpeechModelState = OfflineSpeechModelState.Idle

    fun isModelReady(context: Context): Boolean {
        val dir = getModelDir(context)
        return dir?.let(::containsModelFiles) == true
    }

    fun getStatusSummary(context: Context): String {
        return when (val current = state) {
            OfflineSpeechModelState.Preparing,
            OfflineSpeechModelState.Copying -> "${BuildConfig.OFFLINE_SPEECH_MODEL_LABEL} hazırlanıyor."
            is OfflineSpeechModelState.Error -> "${BuildConfig.OFFLINE_SPEECH_MODEL_LABEL} hazırlanamadı: ${current.message}"
            OfflineSpeechModelState.Ready -> "${BuildConfig.OFFLINE_SPEECH_MODEL_LABEL} hazır."
            OfflineSpeechModelState.Idle -> {
                if (isModelReady(context)) {
                    "${BuildConfig.OFFLINE_SPEECH_MODEL_LABEL} hazır."
                } else {
                    "${BuildConfig.OFFLINE_SPEECH_MODEL_LABEL} henüz hazır değil."
                }
            }
        }
    }

    suspend fun ensurePrepared(context: Context): Boolean {
        if (isModelReady(context)) {
            state = OfflineSpeechModelState.Ready
            return true
        }

        return prepareMutex.withLock {
            if (isModelReady(context)) {
                state = OfflineSpeechModelState.Ready
                return@withLock true
            }

            runCatching {
                copyBundledModel(context.applicationContext)
            }.fold(
                onSuccess = {
                    state = OfflineSpeechModelState.Ready
                    true
                },
                onFailure = { error ->
                    state = OfflineSpeechModelState.Error(error.message ?: "Bilinmeyen hata")
                    false
                }
            )
        }
    }

    fun warmUp(context: Context) {
        if (isModelReady(context)) {
            state = OfflineSpeechModelState.Ready
            return
        }
        if (state is OfflineSpeechModelState.Preparing) return
        state = OfflineSpeechModelState.Preparing
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val prepared = ensurePrepared(context.applicationContext)
            if (!prepared && state !is OfflineSpeechModelState.Error) {
                state = OfflineSpeechModelState.Error("Hazırlama tamamlanamadı")
            }
        }
    }

    fun startPreparation(context: Context): String {
        if (isModelReady(context)) {
            state = OfflineSpeechModelState.Ready
            return "${BuildConfig.OFFLINE_SPEECH_MODEL_LABEL} zaten hazır."
        }

        warmUp(context)
        return "${BuildConfig.OFFLINE_SPEECH_MODEL_LABEL} hazırlanıyor. Kısa süre sonra tekrar deneyebilirsin."
    }

    fun getPreparationMessage(context: Context): String {
        return when (state) {
            is OfflineSpeechModelState.Preparing,
            is OfflineSpeechModelState.Copying -> "${BuildConfig.OFFLINE_SPEECH_MODEL_LABEL} hazırlanıyor. Kısa süre sonra tekrar deneyebilirsin."
            is OfflineSpeechModelState.Error -> getStatusSummary(context)
            else -> startPreparation(context)
        }
    }

    private fun copyBundledModel(context: Context) {
        val modelsRoot = File(context.filesDir, MODELS_DIR)
        if (!modelsRoot.exists()) {
            modelsRoot.mkdirs()
        }

        val finalDir = File(modelsRoot, BuildConfig.OFFLINE_SPEECH_MODEL_DIR)
        val stagingDir = File(modelsRoot, "${BuildConfig.OFFLINE_SPEECH_MODEL_DIR}.staging")
        stagingDir.deleteRecursively()
        stagingDir.mkdirs()

        copyAssetFolder(context, BuildConfig.OFFLINE_SPEECH_MODEL_DIR, stagingDir)

        require(containsModelFiles(stagingDir)) { "Paket içindeki ses modeli eksik" }

        finalDir.deleteRecursively()
        check(stagingDir.renameTo(finalDir)) { "Ses modeli yerel klasöre taşınamadı" }
    }

    private fun copyAssetFolder(context: Context, assetPath: String, destinationDir: File) {
        state = OfflineSpeechModelState.Copying
        val entries = context.assets.list(assetPath).orEmpty()
        if (entries.isEmpty()) {
            destinationDir.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                FileOutputStream(destinationDir).use { output ->
                    input.copyTo(output)
                }
            }
            return
        }

        destinationDir.mkdirs()
        entries.forEach { child ->
            val childAssetPath = "$assetPath/$child"
            val childDestination = File(destinationDir, child)
            copyAssetFolder(context, childAssetPath, childDestination)
        }
    }

    fun getModelDir(context: Context): File? {
        val modelsRoot = File(context.applicationContext.filesDir, MODELS_DIR)
        val preferred = File(modelsRoot, BuildConfig.OFFLINE_SPEECH_MODEL_DIR)
        if (containsModelFiles(preferred)) {
            return preferred
        }

        return modelsRoot.listFiles()
            ?.firstOrNull { it.isDirectory && containsModelFiles(it) }
    }

    private fun containsModelFiles(dir: File): Boolean {
        return dir.isDirectory &&
            File(dir, "final.mdl").exists() &&
            File(dir, "HCLr.fst").exists() &&
            File(dir, "mfcc.conf").exists() &&
            File(dir, "ivector").isDirectory
    }
}

sealed class OfflineSpeechModelState {
    object Idle : OfflineSpeechModelState()
    object Preparing : OfflineSpeechModelState()
    object Copying : OfflineSpeechModelState()
    object Ready : OfflineSpeechModelState()
    data class Error(val message: String) : OfflineSpeechModelState()
}
