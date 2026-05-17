package com.example.ads_001

import android.content.Context
import android.os.StatFs
import com.example.ads_001.network.AssistantApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

object OfflineModelManager {
    private const val MODELS_DIR = "offline_models"
    private const val STORAGE_HEADROOM_BYTES = 256L * 1024L * 1024L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = OkHttpClient.Builder().build()

    @Volatile
    private var state: OfflineModelState = OfflineModelState.Idle

    fun getModelFile(context: Context): File {
        val dir = File(context.applicationContext.filesDir, MODELS_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, BuildConfig.OFFLINE_MODEL_FILENAME)
    }

    fun isModelDownloaded(context: Context): Boolean {
        val file = getModelFile(context)
        return file.exists() && file.length() > 0L
    }

    fun getStatusSummary(context: Context): String {
        val file = getModelFile(context)
        return when (val current = state) {
            is OfflineModelState.Downloading -> {
                val percent = if (current.totalBytes > 0L) {
                    ((current.downloadedBytes * 100L) / current.totalBytes).toInt()
                } else {
                    -1
                }
                if (percent >= 0) {
                    "Offline model indiriliyor: %$percent (${formatBytes(current.downloadedBytes)} / ${formatBytes(current.totalBytes)})."
                } else {
                    "Offline model indiriliyor: ${formatBytes(current.downloadedBytes)}."
                }
            }
            is OfflineModelState.Error -> "Offline model hatası: ${current.message}"
            OfflineModelState.Ready -> "Offline model hazır: ${BuildConfig.OFFLINE_MODEL_LABEL} (${formatBytes(file.length())})."
            OfflineModelState.Idle -> {
                if (isModelDownloaded(context)) {
                    "Offline model hazır: ${BuildConfig.OFFLINE_MODEL_LABEL} (${formatBytes(file.length())})."
                } else {
                    val freeSpace = formatBytes(getFreeBytes(file.parentFile ?: context.filesDir))
                    "Offline model yüklü değil. Varsayılan model: ${BuildConfig.OFFLINE_MODEL_LABEL}. Boş alan: $freeSpace."
                }
            }
        }
    }

    fun startDownload(context: Context): String {
        if (isModelDownloaded(context)) {
            state = OfflineModelState.Ready
            return "Offline model zaten hazır. ${getStatusSummary(context)}"
        }

        if (state is OfflineModelState.Downloading) {
            return getStatusSummary(context)
        }

        val appContext = context.applicationContext
        state = OfflineModelState.Downloading(0L, -1L)
        scope.launch {
            runCatching {
                downloadModel(appContext)
            }.onSuccess {
                state = OfflineModelState.Ready
            }.onFailure { error ->
                state = OfflineModelState.Error(error.message ?: "Bilinmeyen hata")
                getModelFile(appContext).parentFile
                    ?.resolve("${BuildConfig.OFFLINE_MODEL_FILENAME}.download")
                    ?.takeIf { it.exists() }
                    ?.delete()
            }
        }
        return "Offline model indirme başlatıldı: ${BuildConfig.OFFLINE_MODEL_LABEL}."
    }

    fun deleteModel(context: Context): String {
        LiteRtOfflineAssistant.unload()
        val file = getModelFile(context)
        val deleted = if (file.exists()) file.delete() else false
        state = OfflineModelState.Idle
        return if (deleted) {
            "Offline model silindi."
        } else {
            "Silinecek offline model bulunamadı."
        }
    }

    private fun downloadModel(context: Context) {
        val url = BuildConfig.OFFLINE_MODEL_URL
        require(url.isNotBlank()) { "OFFLINE_MODEL_URL boş." }

        val targetFile = getModelFile(context)
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.download")
        if (tempFile.exists()) {
            tempFile.delete()
        }

        val requestBuilder = Request.Builder().url(url)
        if (BuildConfig.OFFLINE_MODEL_AUTH_TOKEN.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer ${BuildConfig.OFFLINE_MODEL_AUTH_TOKEN}")
        }

        httpClient.newCall(requestBuilder.build()).execute().use { response ->
            require(response.isSuccessful) { "Model indirilemedi: HTTP ${response.code}" }
            val body = response.body ?: error("Model cevabı boş geldi")
            val totalBytes = body.contentLength()
            if (totalBytes > 0L) {
                val requiredBytes = totalBytes + STORAGE_HEADROOM_BYTES
                val freeBytes = getFreeBytes(targetFile.parentFile ?: context.filesDir)
                require(freeBytes >= requiredBytes) {
                    "Yetersiz depolama. En az ${formatBytes(requiredBytes)} boş alan gerekiyor."
                }
            }

            body.byteStream().use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        state = OfflineModelState.Downloading(downloaded, totalBytes)
                    }
                    output.flush()
                }
            }
        }

        if (targetFile.exists()) {
            targetFile.delete()
        }
        check(tempFile.renameTo(targetFile)) { "İndirilen model dosyası taşınamadı." }
    }

    private fun getFreeBytes(dir: File): Long {
        val stat = StatFs(dir.absolutePath)
        return stat.availableBytes
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 0L) return "?"
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        return when {
            bytes >= gb -> String.format("%.2f GB", bytes / gb)
            bytes >= mb -> String.format("%.1f MB", bytes / mb)
            bytes >= kb -> String.format("%.1f KB", bytes / kb)
            else -> "$bytes B"
        }
    }
}

sealed class OfflineModelState {
    object Idle : OfflineModelState()
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : OfflineModelState()
    object Ready : OfflineModelState()
    data class Error(val message: String) : OfflineModelState()
}
