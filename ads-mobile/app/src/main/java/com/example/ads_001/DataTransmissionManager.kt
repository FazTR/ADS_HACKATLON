package com.example.ads_001

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import com.example.ads_001.network.AdsApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

object DataTransmissionManager {
    private const val TAG = "DataTransmission"
    private const val DIRECT_UPLOAD_GRACE_MS = 1_500L
    private val relayScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingTriggerDrainInProgress = AtomicBoolean(false)

    @Volatile
    private var networkCallbackRegistered = false

    fun initializeRelay(context: Context) {
        val appContext = context.applicationContext
        MeshNetworkManager.ensureRunning(appContext)
        LanRelayManager.ensureRunning(appContext)
        HotspotRelayManager.ensureRunning(appContext)
        registerNetworkCallbackIfNeeded(appContext)
        clearLegacyMeshStatusIfNeeded(appContext)
        TransmissionStatusTracker.ensureDefault(
            appContext,
            "Yakındaki cihazlarla paylaşım hazır",
            TransmissionStatusLevel.INFO
        )
        relayScope.launch {
            processQueuedEarthquakeTriggers(appContext)
            flushPendingReports(appContext)
            EarthquakeCommandBot.flushPendingVictimStatus(appContext)
        }
    }

    private fun clearLegacyMeshStatusIfNeeded(context: Context) {
        val currentStatus = TransmissionStatusTracker.getCurrentStatus(context)
        val legacyMeshMessages = setOf(
            "Mesh baglantisi yeniden deneniyor",
            "Mesh alici hazir",
            "Mesh gonderici ariyor",
            "Yakin cihazla baglanti kuruldu"
        )

        if (currentStatus.message in legacyMeshMessages) {
            TransmissionStatusTracker.update(
                context,
                "Yakındaki cihazlarla paylaşım hazır",
                TransmissionStatusLevel.INFO
            )
        }
    }

    fun sendEarthquakeReport(context: Context) {
        val appContext = context.applicationContext
        initializeRelay(appContext)

        relayScope.launch {
            val intensityScore = SeismicIntensityTracker.getCurrentStatus(appContext).score
            val trigger = PendingEarthquakeTrigger.create(intensityScore = intensityScore)
            TransmissionStatusTracker.update(
                appContext,
                "Deprem bilgisi hazırlanıyor",
                TransmissionStatusLevel.INFO
            )
            val dispatched = materializeAndDispatchLocalReport(appContext, trigger)
            if (!dispatched) {
                val queued = PendingEarthquakeTriggerStore(appContext).addTrigger(trigger)
                if (queued) {
                    Log.w(TAG, "Queued earthquake trigger ${trigger.triggerId} until GPS cache is ready")
                } else {
                    Log.w(TAG, "Earthquake trigger ${trigger.triggerId} was already queued")
                }
                TransmissionStatusTracker.update(
                    appContext,
                    "Konum alınır alınmaz bilgi gönderilecek",
                    TransmissionStatusLevel.WARNING
                )
                HotspotRelayManager.ensureRunning(appContext)
                processQueuedEarthquakeTriggers(appContext)
            }
        }
    }

    internal fun onCachedLocationUpdated(context: Context) {
        val appContext = context.applicationContext
        initializeRelay(appContext)

        relayScope.launch {
            processQueuedEarthquakeTriggers(appContext)
            EarthquakeCommandBot.flushPendingVictimStatus(appContext)
        }
    }

    internal fun onLanReportReceived(context: Context, report: MeshEarthquakeReport, sourceAddress: String) {
        onMeshReportReceived(context, report, sourceAddress)
    }

    internal fun onLanAckReceived(context: Context, messageId: String, sourceAddress: String) {
        onMeshAckReceived(context, messageId, sourceAddress)
    }

    internal fun onMeshReportReceived(context: Context, report: MeshEarthquakeReport, sourceEndpointId: String) {
        val appContext = context.applicationContext
        initializeRelay(appContext)

        relayScope.launch {
            val store = EarthquakeRelayStore(appContext)
            val queued = store.addPendingReport(report)

            if (!queued) {
                if (store.getDeliveredMessageIds().contains(report.messageId)) {
                    MeshNetworkManager.sendAckToEndpoint(sourceEndpointId, report.messageId)
                }
                return@launch
            }

            Log.i(TAG, "Queued mesh report ${report.messageId} from endpoint $sourceEndpointId")
            TransmissionStatusTracker.update(
                appContext,
                "Yakındaki bir cihazdan bilgi alındı",
                TransmissionStatusLevel.INFO
            )

            val uploaded = uploadReport(appContext, report)
            if (uploaded) {
                store.markDelivered(report.messageId)
                MeshNetworkManager.broadcastAck(appContext, report.messageId, sourceEndpointId)
                TransmissionStatusTracker.update(
                    appContext,
                    "Alınan bilgi merkeze ulaştı",
                    TransmissionStatusLevel.SUCCESS
                )
            } else {
                MeshNetworkManager.broadcastReport(appContext, report, sourceEndpointId)
                TransmissionStatusTracker.update(
                    appContext,
                    "Bilgi yakındaki cihazlara aktarılıyor",
                    TransmissionStatusLevel.WARNING
                )
            }
        }
    }

    internal fun onMeshAckReceived(context: Context, messageId: String, sourceEndpointId: String) {
        val appContext = context.applicationContext
        initializeRelay(appContext)

        relayScope.launch {
            val changed = EarthquakeRelayStore(appContext).markDelivered(messageId)
            if (changed) {
                Log.i(TAG, "Marked report $messageId as delivered via mesh ack")
                MeshNetworkManager.broadcastAck(appContext, messageId, sourceEndpointId)
                TransmissionStatusTracker.update(
                    appContext,
                    "Bir bilgi başarıyla ulaştı",
                    TransmissionStatusLevel.SUCCESS
                )
            }
        }
    }

    internal fun onHotspotAnnouncementReceived(
        context: Context,
        hotspotInfo: HotspotRelayInfo,
        sourceEndpointId: String
    ) {
        val appContext = context.applicationContext
        initializeRelay(appContext)
        Log.i(TAG, "Received hotspot announcement ${hotspotInfo.ssid} from $sourceEndpointId")
        HotspotRelayManager.onHotspotAnnouncementReceived(appContext, hotspotInfo)
    }

    internal fun onMeshPeerConnected(context: Context, endpointId: String) {
        val appContext = context.applicationContext
        initializeRelay(appContext)

        relayScope.launch {
            HotspotRelayManager.onMeshPeerConnected(appContext, endpointId)
            val store = EarthquakeRelayStore(appContext)
            if (store.getPendingReports().isNotEmpty()) {
                TransmissionStatusTracker.update(
                    appContext,
                    "Yakındaki cihazla bağlantı kuruldu",
                    TransmissionStatusLevel.INFO
                )
            }
            store.getPendingReports().forEach { report ->
                MeshNetworkManager.sendReportToEndpoint(endpointId, report)
            }
            store.getDeliveredMessageIds().forEach { messageId ->
                MeshNetworkManager.sendAckToEndpoint(endpointId, messageId)
            }
            flushPendingReports(appContext)
        }
    }

    private suspend fun processQueuedEarthquakeTriggers(context: Context) {
        if (!pendingTriggerDrainInProgress.compareAndSet(false, true)) return

        try {
            val cachedLocation = LocationCacheManager.getCachedLocation(context) ?: return
            val triggerStore = PendingEarthquakeTriggerStore(context)
            val pendingTriggers = triggerStore.getPendingTriggers()
            if (pendingTriggers.isEmpty()) return

            TransmissionStatusTracker.update(
                context,
                "Konum alındı, bekleyen bilgiler gönderiliyor",
                TransmissionStatusLevel.INFO
            )

            for (trigger in pendingTriggers) {
                val dispatched = materializeAndDispatchLocalReport(
                    context = context,
                    trigger = trigger,
                    cachedLocation = cachedLocation
                )
                if (dispatched) {
                    triggerStore.removeTrigger(trigger.triggerId)
                }
            }
        } finally {
            pendingTriggerDrainInProgress.set(false)
        }
    }

    private suspend fun materializeAndDispatchLocalReport(
        context: Context,
        trigger: PendingEarthquakeTrigger,
        cachedLocation: CachedLocation? = LocationCacheManager.getCachedLocation(context)
    ): Boolean {
        val location = cachedLocation ?: run {
            Log.w(TAG, "Cannot materialize trigger ${trigger.triggerId}: no cached GPS location")
            return false
        }

        val lat = location.latitude
        val lon = location.longitude
        if (!isValidCoordinates(lat, lon)) {
            Log.e(TAG, "Cannot materialize trigger ${trigger.triggerId}: invalid coordinates lat=$lat, lon=$lon")
            return false
        }

        val deviceId = SharedPrefsHelper(context).getDeviceId()
        val report = MeshEarthquakeReport.create(
            originDeviceId = deviceId,
            latitude = lat,
            longitude = lon,
            createdAtEpochMs = trigger.createdAtEpochMs,
            intensity = trigger.intensityScore
        )

        Log.d(
            TAG,
            "Created local relay report ${report.messageId} from trigger ${trigger.triggerId} for deviceId=$deviceId"
        )
        TransmissionStatusTracker.update(
            context,
            "Deprem bilgisi hazırlandı",
            TransmissionStatusLevel.INFO
        )

        dispatchReport(context, report)
        return true
    }

    private fun hasActiveNetwork(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun registerNetworkCallbackIfNeeded(context: Context) {
        if (networkCallbackRegistered) return

        synchronized(this) {
            if (networkCallbackRegistered) return

            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    relayScope.launch {
                        MeshNetworkManager.ensureRunning(context.applicationContext)
                        LanRelayManager.ensureRunning(context.applicationContext)
                        HotspotRelayManager.ensureRunning(context.applicationContext)
                        flushPendingReports(context.applicationContext)
                        EarthquakeCommandBot.flushPendingVictimStatus(context.applicationContext)
                        rebroadcastPendingReportsIfNeeded(context.applicationContext)
                    }
                }

                override fun onLost(network: android.net.Network) {
                    MeshNetworkManager.ensureRunning(context.applicationContext)
                    LanRelayManager.ensureRunning(context.applicationContext)
                    HotspotRelayManager.ensureRunning(context.applicationContext)
                }

                override fun onCapabilitiesChanged(
                    network: android.net.Network,
                    networkCapabilities: android.net.NetworkCapabilities
                ) {
                    MeshNetworkManager.ensureRunning(context.applicationContext)
                    LanRelayManager.ensureRunning(context.applicationContext)
                    HotspotRelayManager.ensureRunning(context.applicationContext)
                    relayScope.launch {
                        EarthquakeCommandBot.flushPendingVictimStatus(context.applicationContext)
                        rebroadcastPendingReportsIfNeeded(context.applicationContext)
                    }
                }
            }

            try {
                connectivityManager.registerDefaultNetworkCallback(callback)
                networkCallbackRegistered = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register network callback: ${e.message}")
            }
        }
    }

    private suspend fun dispatchReport(context: Context, report: MeshEarthquakeReport) {
        val store = EarthquakeRelayStore(context)
        val queued = store.addPendingReport(report)
        if (!queued) {
            Log.w(TAG, "Local report ${report.messageId} was already queued/delivered")
        }

        if (!hasActiveNetwork(context)) {
            broadcastReportForRelay(context, report, "İnternet yok, bilgi yakındaki cihazlara gönderiliyor")
            return
        }

        TransmissionStatusTracker.update(
            context,
            "Deprem bilgisi gönderiliyor",
            TransmissionStatusLevel.INFO
        )

        coroutineScope {
            val uploadDeferred = async {
                uploadReport(context, report)
            }

            when (val immediateResult = withTimeoutOrNull(DIRECT_UPLOAD_GRACE_MS) { uploadDeferred.await() }) {
                true -> {
                    markReportDelivered(context, store, report, "Deprem bilgisi merkeze ulaştı")
                }

                false -> {
                    broadcastReportForRelay(context, report, "Merkeze ulaşılamadı, bilgi yakındaki cihazlara gönderiliyor")
                }

                null -> {
                    Log.w(
                        TAG,
                        "API upload grace window exceeded for ${report.messageId}; starting mesh relay in parallel"
                    )
                    broadcastReportForRelay(context, report, "Gönderim uzadı, bilgi yakındaki cihazlara da gönderiliyor")
                    if (uploadDeferred.await()) {
                        markReportDelivered(context, store, report, "Deprem bilgisi merkeze ulaştı")
                    }
                }
            }
        }
    }

    private fun markReportDelivered(
        context: Context,
        store: EarthquakeRelayStore,
        report: MeshEarthquakeReport,
        statusMessage: String
    ) {
        store.markDelivered(report.messageId)
        MeshNetworkManager.broadcastAck(context, report.messageId)
        LanRelayManager.broadcastAck(context, report.messageId)
        TransmissionStatusTracker.update(
            context,
            statusMessage,
            TransmissionStatusLevel.SUCCESS
        )
    }

    private fun broadcastReportForRelay(context: Context, report: MeshEarthquakeReport, statusMessage: String) {
        HotspotRelayManager.ensureRunning(context)
        MeshNetworkManager.broadcastReport(context, report)
        LanRelayManager.broadcastReport(context, report)
        TransmissionStatusTracker.update(
            context,
            statusMessage,
            TransmissionStatusLevel.WARNING
        )
    }

    private suspend fun flushPendingReports(context: Context) {
        if (!hasActiveNetwork(context)) return

        val store = EarthquakeRelayStore(context)
        val pendingReports = store.getPendingReports()
        if (pendingReports.isEmpty()) return

        TransmissionStatusTracker.update(
            context,
            "Bekleyen bilgiler gönderiliyor",
            TransmissionStatusLevel.INFO
        )

        for (report in pendingReports) {
            if (uploadReport(context, report)) {
                store.markDelivered(report.messageId)
                MeshNetworkManager.broadcastAck(context, report.messageId)
                LanRelayManager.broadcastAck(context, report.messageId)
                TransmissionStatusTracker.update(
                    context,
                    "Bekleyen bilgi merkeze ulaştı",
                    TransmissionStatusLevel.SUCCESS
                )
            }
        }
    }

    private fun rebroadcastPendingReportsIfNeeded(context: Context) {
        if (!HotspotRelayManager.isConnectedToSuggestedRelayHotspot(context)) {
            return
        }

        val pendingReports = EarthquakeRelayStore(context).getPendingReports()
        if (pendingReports.isEmpty()) {
            return
        }

        TransmissionStatusTracker.update(
            context,
            "Bağlantı kuruldu, bekleyen bilgiler gönderiliyor",
            TransmissionStatusLevel.INFO
        )

        pendingReports.forEach { report ->
            MeshNetworkManager.broadcastReport(context, report)
            LanRelayManager.broadcastReport(context, report)
        }
    }

    private suspend fun uploadReport(context: Context, report: MeshEarthquakeReport): Boolean {
        if (!hasActiveNetwork(context)) {
            Log.w(TAG, "No active network available for report ${report.messageId}")
            TransmissionStatusTracker.update(
                context,
                "İnternet yok, bilgi beklemede",
                TransmissionStatusLevel.WARNING
            )
            return false
        }

        return try {
            val response = AdsApiClient.apiService.sendEarthquakeReport(report.toApiRequest())
            if (response.isSuccessful) {
                Log.d(TAG, "Uploaded relay report ${report.messageId} to API")
                true
            } else {
                val errorBody = response.errorBody()?.string()?.take(500)
                Log.e(
                    TAG,
                    "API upload failed for ${report.messageId} with code ${response.code()} body=$errorBody"
                )
                TransmissionStatusTracker.update(
                    context,
                    "Şu an gönderim tamamlanamadı",
                    TransmissionStatusLevel.ERROR
                )
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "API upload threw exception for ${report.messageId}: ${e.message}")
            TransmissionStatusTracker.update(
                context,
                "Bağlantı kesildi, yeniden denenecek",
                TransmissionStatusLevel.ERROR
            )
            false
        }
    }

    private fun isValidCoordinates(latitude: Double, longitude: Double): Boolean {
        if (latitude == 0.0 && longitude == 0.0) return false
        return latitude in -90.0..90.0 && longitude in -180.0..180.0
    }
}
