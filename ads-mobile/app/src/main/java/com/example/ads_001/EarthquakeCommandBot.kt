package com.example.ads_001

import android.content.Context.CONNECTIVITY_SERVICE
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.util.Log
import com.example.ads_001.BuildConfig
import com.example.ads_001.network.AdsApiClient
import com.example.ads_001.network.AssistantApiClient
import com.example.ads_001.network.models.ChatCompletionsRequest
import com.example.ads_001.network.models.ChatMessage
import com.example.ads_001.network.models.ReportRequest
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

object EarthquakeCommandBot {
    private const val TAG = "EarthquakeCommandBot"
    private const val MAX_HISTORY_MESSAGES = 8
    private const val CONTINUATION_PROMPT =
        "Cevabın yarıda kaldı. Aynı noktadan kısa ve doğal şekilde devam et. Baştan başlama."
    private val conversationHistory = mutableListOf<ChatMessage>()
    private val pendingVictimStatusFlushInProgress = AtomicBoolean(false)

    private enum class VictimStatusDispatchState {
        SUCCESS,
        RETRYABLE_FAILURE,
        PERMANENT_FAILURE
    }

    private data class VictimStatusDispatchResult(
        val state: VictimStatusDispatchState,
        val detail: String? = null
    )

    private data class AssistantCompletionResult(
        val text: String,
        val finishReason: String?
    )

    fun restoreConversationHistory(messages: List<AssistantConversationMessage>) {
        synchronized(conversationHistory) {
            conversationHistory.clear()
            conversationHistory.addAll(
                messages
                    .sortedBy { it.createdAtEpochMs }
                    .takeLast(MAX_HISTORY_MESSAGES * 2)
                    .map {
                        ChatMessage(
                            role = if (it.isUser) "user" else "assistant",
                            content = it.text
                        )
                    }
            )
        }
    }

    fun clearConversationHistory() {
        synchronized(conversationHistory) {
            conversationHistory.clear()
        }
    }

    suspend fun handle(
        context: Context,
        userInput: String,
        onAssistantProgress: ((String) -> Unit)? = null
    ): String {
        val normalized = normalize(userInput)
        if (normalized.isBlank()) {
            return "Bir şey yazmadın. Örnek olarak komutlar, konum, sarsıntı veya yardım çağır yazabilirsin."
        }

        val localReply = when {
            isHelpCommand(normalized) -> buildHelpMessage()
            isVictimStatusCommand(normalized) -> submitVictimStatus(context, normalized, onAssistantProgress)
            isEmergencyCommand(normalized) -> triggerEmergencyFlow(context)
            isLocationRefreshCommand(normalized) -> refreshCachedLocation(context)
            isOfflineModelDownloadCommand(normalized) -> OfflineModelManager.startDownload(context)
            isOfflineModelStatusCommand(normalized) -> OfflineModelManager.getStatusSummary(context)
            isOfflineModelDeleteCommand(normalized) -> OfflineModelManager.deleteModel(context)
            isLocationCommand(normalized) -> buildLocationMessage(context)
            isIntensityCommand(normalized) -> buildIntensityMessage(context)
            isConnectivityCommand(normalized) -> buildConnectivityMessage(context)
            isLastEarthquakeCommand(normalized) -> buildLastEarthquakeMessage(context)
            else -> null
        }

        if (localReply != null) {
            appendConversationTurn(userInput, localReply)
            return localReply
        }

        if (!hasUsableNetwork(context)) {
            if (OfflineModelManager.isModelDownloaded(context)) {
                val reply = LiteRtOfflineAssistant.generateReply(context, userInput, buildAppContextSummary(context))
                appendConversationTurn(userInput, reply)
                return reply
            }
            return "İnternet şu an yok. Yine de komutlarla yardımcı olabilirim. Neler yazabileceğini görmek için 'komutlar' yaz."
        }

        return requestAssistantReply(context, userInput)
    }

    private fun triggerEmergencyFlow(context: Context): String {
        DataTransmissionManager.sendEarthquakeReport(context)
        LocationCacheManager.refreshNow(context)
        return "Bilgin gönderilmeye başlandı. Konumun da yenileniyor."
    }

    private fun refreshCachedLocation(context: Context): String {
        LocationCacheManager.refreshNow(context)
        return "Konumun yenileniyor. Hazır olduğunda yeni gönderimler bunu kullanacak."
    }

    private fun buildLocationMessage(context: Context): String {
        val location = LocationCacheManager.getCachedLocation(context)
            ?: return "Henüz güncel konumun yok. 'Konumu yenile' yazarak yeniden deneyebilirsin."

        val ageMinutes = ((System.currentTimeMillis() - location.timestampMs).coerceAtLeast(0L) / 60_000L)
        return "Son konumun: %.5f, %.5f. Yaklaşık doğruluk %.0f metre, güncellik %d dakika."
            .format(location.latitude, location.longitude, location.accuracyMeters, ageMinutes)
    }

    private fun buildIntensityMessage(context: Context): String {
        val status = SeismicIntensityTracker.getCurrentStatus(context)
        return "Yerel sarsıntı seviyesi şu an ${status.label}."
    }

    private fun buildConnectivityMessage(context: Context): String {
        val appContext = context.applicationContext
        val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
        val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        val wifiState = when {
            !wifiManager.isWifiEnabled -> "Wi-Fi kapalı"
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> {
                val ssid = wifiManager.connectionInfo?.ssid?.replace("\"", "")?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
                if (ssid != null) "Wi-Fi bağlı: $ssid" else "Wi-Fi bağlı"
            }
            else -> "Wi-Fi açık ama bağlı değil"
        }

        val internetState = when {
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true -> "İnternet erişimi var"
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true -> "Bağlantı var ama internet henüz hazır değil"
            else -> "İnternet erişimi yok"
        }

        val transportState = when {
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Şu an mobil veri kullanılıyor"
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Şu an Wi-Fi kullanılıyor"
            else -> "Şu an bağlantı kullanılmıyor"
        }

        val transmissionStatus = TransmissionStatusTracker.getCurrentStatus(context).message
        return "$wifiState. $internetState. $transportState. Son durum: $transmissionStatus."
    }

    private fun buildLastEarthquakeMessage(context: Context): String {
        val latest = SystemEarthquakeTracker.getLatest(context)
            ?: return "Henüz yeni bir deprem bildirimi yok."
        val createdSuffix = latest.createdAt?.takeIf { it.isNotBlank() }?.let { " Zaman: $it." } ?: ""
        return "Son deprem bildirimi: ${latest.summary}.$createdSuffix"
    }

    private fun buildHelpMessage(): String {
        return "Şunları yazabilirsin: komutlar, konum, konumu yenile, sarsıntı, bağlantı durumu, son deprem, yardım çağır, iyiyim, yaralıyım, enkaz altındayım, offline modeli indir, offline modeli durum, offline modeli sil. İstersen konuş düğmesiyle yerel sesli giriş de kullanabilirsin."
    }

    private suspend fun submitVictimStatus(
        context: Context,
        normalizedInput: String,
        onAssistantProgress: ((String) -> Unit)? = null
    ): String {
        val status = parseVictimStatus(normalizedInput)
            ?: return "Seni anlayamadım. 'İyiyim', 'yaralıyım' veya 'enkaz altındayım' yazabilirsin."
        val store = PendingVictimStatusStore(context)

        val location = LocationCacheManager.getCachedLocation(context)
        if (location == null) {
            store.save(status.apiValue)
            DataTransmissionManager.initializeRelay(context)
            LocationCacheManager.refreshNow(context)
            TransmissionStatusTracker.update(
                context,
                "Durumun sıraya alındı, konum bekleniyor",
                TransmissionStatusLevel.WARNING
            )
            return "Konumun henüz hazır değil. ${status.userLabel} bilgini sıraya aldım; konum gelir gelmez göndereceğim."
        }

        if (!hasUsableNetwork(context)) {
            store.save(status.apiValue)
            DataTransmissionManager.initializeRelay(context)
            TransmissionStatusTracker.update(
                context,
                "Durumun sıraya alındı, internet bekleniyor",
                TransmissionStatusLevel.WARNING
            )
            return "${status.userLabel} bilgisini sıraya aldım. İnternet gelir gelmez göndereceğim."
        }

        onAssistantProgress?.invoke("${status.userLabel} bilgin gönderiliyor.")
        val result = dispatchVictimStatus(context, status, location, fromQueue = false)
        return when (result.state) {
            VictimStatusDispatchState.SUCCESS -> {
                store.clear()
                "${status.userLabel} bilgisini gönderdim."
            }

            VictimStatusDispatchState.RETRYABLE_FAILURE -> {
                store.save(status.apiValue)
                DataTransmissionManager.initializeRelay(context)
                "${status.userLabel} bilgisini şu an merkeze ulaştıramadım. Sıraya aldım; bağlantı gelince tekrar deneyeceğim."
            }

            VictimStatusDispatchState.PERMANENT_FAILURE -> {
                "Durumun şu an gönderilemedi${result.detail.orEmpty()}"
            }
        }
    }

    internal suspend fun flushPendingVictimStatus(context: Context) {
        if (!pendingVictimStatusFlushInProgress.compareAndSet(false, true)) return

        try {
            val appContext = context.applicationContext
            val store = PendingVictimStatusStore(appContext)
            val status = store.getPendingStatusApiValue()
                ?.let(VictimStatus::fromApiValue)
                ?: run {
                    store.clear()
                    return
                }

            val location = LocationCacheManager.getCachedLocation(appContext) ?: return
            if (!hasUsableNetwork(appContext)) return

            when (dispatchVictimStatus(appContext, status, location, fromQueue = true).state) {
                VictimStatusDispatchState.SUCCESS,
                VictimStatusDispatchState.PERMANENT_FAILURE -> store.clear()

                VictimStatusDispatchState.RETRYABLE_FAILURE -> Unit
            }
        } finally {
            pendingVictimStatusFlushInProgress.set(false)
        }
    }

    private suspend fun dispatchVictimStatus(
        context: Context,
        status: VictimStatus,
        location: CachedLocation,
        fromQueue: Boolean
    ): VictimStatusDispatchResult {
        val batteryLevel = getBatteryLevel(context)
        Log.d(
            TAG,
            "Submitting status=${status.apiValue} fromQueue=$fromQueue with batteryLevel=${batteryLevel ?: "null"}"
        )
        val request = ReportRequest(
            deviceId = SharedPrefsHelper(context).getDeviceId(),
            status = status.apiValue,
            latitude = location.latitude,
            longitude = location.longitude,
            batteryLevel = batteryLevel
        )

        TransmissionStatusTracker.update(
            context,
            if (fromQueue) "Bekleyen durum bilgisi gönderiliyor" else "${status.userLabel} bilgin gönderiliyor",
            TransmissionStatusLevel.INFO
        )

        return try {
            val response = AdsApiClient.apiService.reportStatus(request)
            if (response.isSuccessful) {
                response.body()?.close()
                TransmissionStatusTracker.update(
                    context,
                    if (fromQueue) "Bekleyen durum bilgisi merkeze ulaştı" else "${status.userLabel} bilgin ulaştı",
                    TransmissionStatusLevel.SUCCESS
                )
                VictimStatusDispatchResult(VictimStatusDispatchState.SUCCESS)
            } else {
                val errorBody = response.errorBody().use { body ->
                    body?.string()?.takeIf { it.isNotBlank() }?.take(300)
                }
                val detail = errorBody?.let { ": $it" } ?: ""
                val state = if (response.code() >= 500 || response.code() == 408 || response.code() == 429) {
                    VictimStatusDispatchState.RETRYABLE_FAILURE
                } else {
                    VictimStatusDispatchState.PERMANENT_FAILURE
                }
                Log.w(
                    TAG,
                    "Victim status report failed code=${response.code()} state=$state body=${errorBody ?: "<empty>"}"
                )
                TransmissionStatusTracker.update(
                    context,
                    if (state == VictimStatusDispatchState.RETRYABLE_FAILURE) {
                        "Durumun sıraya alındı, yeniden denenecek"
                    } else {
                        "Durumun gönderilemedi"
                    },
                    if (state == VictimStatusDispatchState.RETRYABLE_FAILURE) {
                        TransmissionStatusLevel.WARNING
                    } else {
                        TransmissionStatusLevel.ERROR
                    }
                )
                VictimStatusDispatchResult(
                    state = state,
                    detail = " (${response.code()})$detail"
                )
            }
        } catch (error: Exception) {
            Log.w(TAG, "Victim status report failed: ${error.message}")
            TransmissionStatusTracker.update(
                context,
                "Durumun sıraya alındı, bağlantı bekleniyor",
                TransmissionStatusLevel.WARNING
            )
            VictimStatusDispatchResult(VictimStatusDispatchState.RETRYABLE_FAILURE)
        }
    }

    private suspend fun requestAssistantReply(context: Context, userInput: String): String {
        val messages = buildMessages(context, userInput)
        return runCatching {
            val initialResult = requestAssistantCompletion(messages)
            if (initialResult == null) {
                if (OfflineModelManager.isModelDownloaded(context)) {
                    val fallback = LiteRtOfflineAssistant.generateReply(context, userInput, buildAppContextSummary(context))
                    appendConversationTurn(userInput, fallback)
                    return@runCatching fallback
                }
                return@runCatching "Şu an yanıt veremiyorum. Biraz sonra tekrar dene."
            }

            val assistantText = if (initialResult.finishReason == "length") {
                val continuationMessages = messages + listOf(
                    ChatMessage(role = "assistant", content = initialResult.text),
                    ChatMessage(role = "user", content = CONTINUATION_PROMPT)
                )
                val continuationResult = requestAssistantCompletion(continuationMessages)
                mergeAssistantReplies(initialResult.text, continuationResult?.text)
            } else {
                initialResult.text
            }.ifBlank {
                "Şu an sana uygun bir yanıt hazırlayamadım."
            }

            appendConversationTurn(userInput, assistantText)
            assistantText
        }.getOrElse { error ->
            Log.w(TAG, "Assistant request exception: ${error.message}")
            if (OfflineModelManager.isModelDownloaded(context)) {
                val fallback = LiteRtOfflineAssistant.generateReply(context, userInput, buildAppContextSummary(context))
                appendConversationTurn(userInput, fallback)
                fallback
            } else {
                "Şu an yanıt veremiyorum. Şimdilik komutlarla yardımcı olabiliyorum."
            }
        }
    }

    private suspend fun requestAssistantCompletion(messages: List<ChatMessage>): AssistantCompletionResult? {
        val response = AssistantApiClient.apiService.createChatCompletion(
            ChatCompletionsRequest(
                model = BuildConfig.ASSISTANT_MODEL,
                messages = messages
            )
        )
        if (!response.isSuccessful) {
            Log.w(TAG, "Assistant API failed: code=${response.code()}")
            return null
        }

        val choice = response.body()
            ?.choices
            ?.firstOrNull()
            ?: return null

        val text = choice.message
            ?.content
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null

        return AssistantCompletionResult(
            text = text,
            finishReason = choice.finishReason
        )
    }

    private fun mergeAssistantReplies(first: String, continuation: String?): String {
        val head = first.trim()
        val tail = continuation?.trim().orEmpty()
        if (tail.isBlank()) return head
        return listOf(head, tail)
            .joinToString(separator = if (head.endsWith("\n")) "" else "\n")
            .trim()
    }

    private fun buildMessages(context: Context, userInput: String): List<ChatMessage> {
        val systemPrompt = """
            Sen ADS uygulamasındaki deprem odaklı Türkçe yardımcı asistansın.
            Kısa, açık ve sakin cevap ver.
            Önceliğin deprem güvenliği, uygulama durumu ve kullanıcının mevcut verisidir.
            Bilmediğin şeyi uydurma.
            Cevapların en fazla 4 kısa cümle olsun.
            Markdown kullanma.
        """.trimIndent()

        val appContextSummary = buildAppContextSummary(context)

        return buildList {
            add(ChatMessage(role = "system", content = systemPrompt))
            add(ChatMessage(role = "system", content = appContextSummary))
            synchronized(conversationHistory) {
                addAll(conversationHistory.takeLast(MAX_HISTORY_MESSAGES))
            }
            add(ChatMessage(role = "user", content = userInput))
        }
    }

    private fun buildAppContextSummary(context: Context): String {
        val location = buildLocationMessage(context)
        val intensity = buildIntensityMessage(context)
        val connectivity = buildConnectivityMessage(context)
        val latestEarthquake = SystemEarthquakeTracker.getLatest(context)?.summary ?: "Henüz yok"
        val transmission = TransmissionStatusTracker.getCurrentStatus(context).message
        return """
            Uygulama bağlamı:
            - $location
            - $intensity
            - $connectivity
            - Son deprem bildirimi: $latestEarthquake
            - Son durum: $transmission
        """.trimIndent()
    }

    private fun appendConversationTurn(userText: String, assistantText: String) {
        synchronized(conversationHistory) {
            conversationHistory += ChatMessage(role = "user", content = userText)
            conversationHistory += ChatMessage(role = "assistant", content = assistantText)
            if (conversationHistory.size > MAX_HISTORY_MESSAGES * 2) {
                val dropCount = conversationHistory.size - (MAX_HISTORY_MESSAGES * 2)
                repeat(dropCount) {
                    conversationHistory.removeAt(0)
                }
            }
        }
    }

    private fun hasUsableNetwork(context: Context): Boolean {
        val connectivityManager = context.applicationContext.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun isHelpCommand(input: String): Boolean {
        return input == "yardim" ||
            input.contains("komut") ||
            input.contains("neler yapabiliyorsun") ||
            input.contains("ne yapabiliyorsun")
    }

    private fun isEmergencyCommand(input: String): Boolean {
        return input.contains("yardim cagir") ||
            input.contains("acil yardim") ||
            input.contains("imdat") ||
            input.contains("deprem bildir") ||
            input.contains("rapor gonder") ||
            input.contains("merkeze gonder")
    }

    private fun isVictimStatusCommand(input: String): Boolean {
        return input.contains("iyiyim") ||
            input.contains("guvendeyim") ||
            input.contains("guvendeyim") ||
            input.contains("yaraliyim") ||
            input.contains("yaralandim") ||
            input.contains("enkaz altindayim") ||
            input.contains("durumumu bildir")
    }

    private fun isLocationRefreshCommand(input: String): Boolean {
        return input.contains("konumu yenile") ||
            input.contains("gps yenile") ||
            input.contains("konum yenile")
    }

    private fun isOfflineModelDownloadCommand(input: String): Boolean {
        return input.contains("offline modeli indir") ||
            input.contains("offline model indir") ||
            input.contains("gemma indir")
    }

    private fun isOfflineModelStatusCommand(input: String): Boolean {
        return input.contains("offline modeli durum") ||
            input.contains("offline model durum") ||
            input.contains("offline model var mi") ||
            input.contains("gemma durum")
    }

    private fun isOfflineModelDeleteCommand(input: String): Boolean {
        return input.contains("offline modeli sil") ||
            input.contains("offline model sil") ||
            input.contains("gemma sil")
    }

    private fun isLocationCommand(input: String): Boolean {
        return input.contains("konum") ||
            input.contains("neredeyim") ||
            input.contains("gps")
    }

    private fun isIntensityCommand(input: String): Boolean {
        return input.contains("sarsinti") ||
            input.contains("siddet") ||
            input.contains("yerel deprem")
    }

    private fun isConnectivityCommand(input: String): Boolean {
        return input.contains("baglanti") ||
            input.contains("internet") ||
            input.contains("wifi") ||
            input.contains("iletim") ||
            input.contains("ag durumu")
    }

    private fun isLastEarthquakeCommand(input: String): Boolean {
        return input.contains("son deprem") ||
            input.contains("son uyari") ||
            input.contains("api deprem")
    }

    private fun normalize(text: String): String {
        return text
            .trim()
            .lowercase(Locale("tr", "TR"))
            .replace('ç', 'c')
            .replace('ğ', 'g')
            .replace('ı', 'i')
            .replace('ö', 'o')
            .replace('ş', 's')
            .replace('ü', 'u')
            .replace(Regex("\\s+"), " ")
    }

    private fun parseVictimStatus(input: String): VictimStatus? {
        return when {
            input.contains("enkaz altindayim") -> VictimStatus.UNDER_RUBBLE
            input.contains("yaraliyim") || input.contains("yaralandim") -> VictimStatus.INJURED
            input.contains("iyiyim") || input.contains("guvendeyim") -> VictimStatus.OK
            else -> null
        }
    }

    private fun getBatteryLevel(context: Context): Int? {
        val appContext = context.applicationContext
        val sharedPrefsHelper = SharedPrefsHelper(appContext)

        val stickyIntent = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val levelFromIntent = stickyIntent
            ?.let { intent ->
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    ((level * 100f) / scale).toInt().coerceIn(0, 100)
                } else {
                    null
                }
            }

        val batteryManager = appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            ?: return rememberValidBatteryLevel(sharedPrefsHelper, levelFromIntent)
        val levelFromManager = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        return rememberValidBatteryLevel(
            sharedPrefsHelper = sharedPrefsHelper,
            levelFromIntent = levelFromIntent,
            levelFromManager = levelFromManager
        )
    }

    private fun rememberValidBatteryLevel(
        sharedPrefsHelper: SharedPrefsHelper,
        levelFromIntent: Int?,
        levelFromManager: Int? = null
    ): Int? {
        val validLevel = when {
            levelFromIntent != null && levelFromIntent in 1..100 -> levelFromIntent
            levelFromManager != null && levelFromManager in 1..100 -> levelFromManager
            else -> null
        }

        if (validLevel != null) {
            sharedPrefsHelper.saveLastValidBatteryLevel(validLevel)
            return validLevel
        }

        return sharedPrefsHelper.getLastValidBatteryLevel()
    }
}

private enum class VictimStatus(val apiValue: String, val userLabel: String) {
    OK("ok", "İyiyim"),
    UNDER_RUBBLE("under_rubble", "Enkaz altındayım"),
    INJURED("injured", "Yaralıyım");

    companion object {
        fun fromApiValue(value: String): VictimStatus? {
            return values().firstOrNull { it.apiValue == value }
        }
    }
}
