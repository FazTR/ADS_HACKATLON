package com.example.ads_001

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.ads_001.BuildConfig
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.google.gson.Gson
import java.nio.charset.StandardCharsets

object MeshNetworkManager {
    private const val TAG = "MeshNetworkManager"
    private const val RETRY_DELAY_MS = 8_000L
    private val SERVICE_ID = BuildConfig.APPLICATION_ID
    private val STRATEGY = Strategy.P2P_CLUSTER
    private val gson = Gson()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var appContext: Context? = null
    private var connectionsClient: ConnectionsClient? = null
    private var isAdvertising = false
    private var isDiscovering = false
    private val connectedEndpoints = mutableSetOf<String>()
    private val pendingConnectionEndpoints = mutableSetOf<String>()
    private var currentMode = MeshMode.UNKNOWN
    @Volatile
    private var retryScheduled = false
    @Volatile
    private var idleRecoveryScheduled = false

    private enum class MeshMode {
        UNKNOWN,
        ONLINE_RECEIVER,
        OFFLINE_SENDER
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Log.i(TAG, "Connection initiated by endpoint: $endpointId (${info.endpointName})")
            connectionsClient?.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            pendingConnectionEndpoints.remove(endpointId)
            if (result.status.isSuccess) {
                Log.i(TAG, "Successfully connected to endpoint: $endpointId")
                connectedEndpoints.add(endpointId)
                appContext?.let { DataTransmissionManager.onMeshPeerConnected(it, endpointId) }
            } else {
                Log.e(TAG, "Connection failed to endpoint $endpointId: ${result.status.statusCode}")
                scheduleRetry("connection failure ${result.status.statusCode}")
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.i(TAG, "Disconnected from endpoint: $endpointId")
            connectedEndpoints.remove(endpointId)
            pendingConnectionEndpoints.remove(endpointId)
            scheduleRetry("endpoint disconnected")
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (connectedEndpoints.contains(endpointId) || pendingConnectionEndpoints.contains(endpointId)) {
                return
            }

            Log.i(TAG, "Found nearby endpoint: $endpointId (${info.endpointName}). Requesting connection...")
            pendingConnectionEndpoints.add(endpointId)
            connectionsClient?.requestConnection(
                getLocalEndpointName(),
                endpointId,
                connectionLifecycleCallback
            )?.addOnFailureListener { e ->
                pendingConnectionEndpoints.remove(endpointId)
                Log.e(TAG, "Failed to request connection to $endpointId", e)
                scheduleRetry("request connection failure")
            }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.i(TAG, "Lost nearby endpoint: $endpointId")
            pendingConnectionEndpoints.remove(endpointId)
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val data = payload.asBytes()
                if (data != null) {
                    val json = String(data, StandardCharsets.UTF_8)
                    handleIncomingPayload(endpointId, json)
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            Unit
        }
    }

    fun ensureRunning(context: Context) {
        appContext = context.applicationContext

        if (!hasRequiredPermissions(appContext!!)) {
            Log.w(TAG, "Mesh permissions missing; relay is not started")
            return
        }

        if (connectionsClient == null) {
            connectionsClient = Nearby.getConnectionsClient(appContext!!)
        }

        val nextMode = resolveMode(appContext!!)
        if (currentMode != nextMode) {
            Log.i(TAG, "Switching mesh mode from $currentMode to $nextMode")
            currentMode = nextMode
        }

        when (nextMode) {
            MeshMode.ONLINE_RECEIVER -> {
                startAdvertising(critical = true)
                startDiscovery(critical = false)
            }

            MeshMode.OFFLINE_SENDER -> {
                startDiscovery(critical = true)
                startAdvertising(critical = true)
            }

            MeshMode.UNKNOWN -> Unit
        }
    }

    fun broadcastReport(
        context: Context,
        report: MeshEarthquakeReport,
        excludeEndpointId: String? = null
    ) {
        ensureRunning(context)
        if (connectedEndpoints.isEmpty()) {
            restartTransportIfIdle("pending report broadcast")
            scheduleIdleRecoveryIfNeeded()
        }
        sendEnvelopeToAll(
            MeshRelayEnvelope(type = MeshRelayEnvelope.TYPE_REPORT, report = report),
            excludeEndpointId
        )
    }

    fun broadcastAck(context: Context, messageId: String, excludeEndpointId: String? = null) {
        ensureRunning(context)
        sendEnvelopeToAll(
            MeshRelayEnvelope(
                type = MeshRelayEnvelope.TYPE_ACK,
                acknowledgedMessageId = messageId
            ),
            excludeEndpointId
        )
    }

    fun broadcastHotspot(context: Context, hotspotInfo: HotspotRelayInfo, excludeEndpointId: String? = null) {
        ensureRunning(context)
        sendEnvelopeToAll(
            MeshRelayEnvelope(
                type = MeshRelayEnvelope.TYPE_HOTSPOT,
                hotspot = hotspotInfo
            ),
            excludeEndpointId
        )
    }

    fun sendReportToEndpoint(endpointId: String, report: MeshEarthquakeReport) {
        sendEnvelopeToEndpoint(
            endpointId,
            MeshRelayEnvelope(type = MeshRelayEnvelope.TYPE_REPORT, report = report)
        )
    }

    fun sendAckToEndpoint(endpointId: String, messageId: String) {
        sendEnvelopeToEndpoint(
            endpointId,
            MeshRelayEnvelope(
                type = MeshRelayEnvelope.TYPE_ACK,
                acknowledgedMessageId = messageId
            )
        )
    }

    fun sendHotspotToEndpoint(endpointId: String, hotspotInfo: HotspotRelayInfo) {
        sendEnvelopeToEndpoint(
            endpointId,
            MeshRelayEnvelope(
                type = MeshRelayEnvelope.TYPE_HOTSPOT,
                hotspot = hotspotInfo
            )
        )
    }

    private fun startAdvertising(critical: Boolean) {
        if (isAdvertising) return
        val context = appContext ?: return
        if (!hasRequiredPermissions(context)) return
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()

        connectionsClient?.startAdvertising(
            getLocalEndpointName(),
            SERVICE_ID,
            connectionLifecycleCallback,
            options
        )?.addOnSuccessListener {
            Log.i(TAG, "Successfully started advertising as Mesh Node")
            isAdvertising = true
            retryScheduled = false
        }?.addOnFailureListener { e ->
            isAdvertising = false
            Log.e(TAG, "Failed to start advertising", e)
            if (critical) {
                scheduleRetry("advertising failure")
            }
        }
    }

    private fun startDiscovery(critical: Boolean) {
        if (isDiscovering) return
        val context = appContext ?: return
        if (!hasRequiredPermissions(context)) return

        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()

        connectionsClient?.startDiscovery(
            SERVICE_ID,
            endpointDiscoveryCallback,
            options
        )?.addOnSuccessListener {
            Log.i(TAG, "Successfully started discovering Mesh Nodes")
            isDiscovering = true
            retryScheduled = false
        }?.addOnFailureListener { e ->
            isDiscovering = false
            Log.e(TAG, "Failed to start discovery", e)
            if (critical) {
                scheduleRetry("discovery failure")
            }
        }
    }

    private fun stopAdvertising() {
        if (!isAdvertising) return
        connectionsClient?.stopAdvertising()
        isAdvertising = false
    }

    private fun stopDiscovery() {
        if (!isDiscovering) return
        connectionsClient?.stopDiscovery()
        isDiscovering = false
    }

    private fun handleIncomingPayload(endpointId: String, json: String) {
        try {
            val envelope = gson.fromJson(json, MeshRelayEnvelope::class.java)
            val context = appContext ?: return

            when (envelope.type) {
                MeshRelayEnvelope.TYPE_REPORT -> {
                    val report = envelope.report
                    if (report != null) {
                        Log.i(TAG, "Received report ${report.messageId} from $endpointId")
                        DataTransmissionManager.onMeshReportReceived(context, report, endpointId)
                    }
                }

                MeshRelayEnvelope.TYPE_ACK -> {
                    val messageId = envelope.acknowledgedMessageId
                    if (!messageId.isNullOrBlank()) {
                        Log.i(TAG, "Received ack $messageId from $endpointId")
                        DataTransmissionManager.onMeshAckReceived(context, messageId, endpointId)
                    }
                }

                MeshRelayEnvelope.TYPE_HOTSPOT -> {
                    val hotspotInfo = envelope.hotspot
                    if (hotspotInfo != null) {
                        Log.i(TAG, "Received hotspot relay info for ${hotspotInfo.ssid} from $endpointId")
                        DataTransmissionManager.onHotspotAnnouncementReceived(context, hotspotInfo, endpointId)
                    }
                }

                else -> Log.w(TAG, "Ignoring unknown payload type: ${envelope.type}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse incoming payload: ${e.message}")
        }
    }

    private fun sendEnvelopeToAll(envelope: MeshRelayEnvelope, excludeEndpointId: String?) {
        val targetEndpoints = connectedEndpoints.filter { it != excludeEndpointId }
        for (endpointId in targetEndpoints) {
            sendEnvelopeToEndpoint(endpointId, envelope)
        }
    }

    private fun sendEnvelopeToEndpoint(endpointId: String, envelope: MeshRelayEnvelope) {
        try {
            val json = gson.toJson(envelope)
            val bytesPayload = Payload.fromBytes(json.toByteArray(StandardCharsets.UTF_8))
            connectionsClient?.sendPayload(endpointId, bytesPayload)
                ?.addOnSuccessListener {
                    Log.i(TAG, "Sent ${envelope.type} payload to endpoint $endpointId")
                }
                ?.addOnFailureListener { e ->
                    Log.e(TAG, "Failed to send ${envelope.type} payload to $endpointId", e)
                    connectedEndpoints.remove(endpointId)
                    pendingConnectionEndpoints.remove(endpointId)
                    scheduleRetry("payload send failure")
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending payload to $endpointId", e)
        }
    }

    fun stopAll() {
        mainHandler.removeCallbacksAndMessages(null)
        stopAdvertising()
        stopDiscovery()
        connectionsClient?.stopAllEndpoints()
        isAdvertising = false
        isDiscovering = false
        connectedEndpoints.clear()
        pendingConnectionEndpoints.clear()
        currentMode = MeshMode.UNKNOWN
        retryScheduled = false
        idleRecoveryScheduled = false
        appContext = null
        Log.i(TAG, "Stopped all Mesh Network operations")
    }

    private fun hasRequiredPermissions(context: Context): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val bluetoothPermissionsGranted =
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
            if (!bluetoothPermissionsGranted) {
                return false
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        return true
    }

    private fun getLocalEndpointName(): String {
        val context = appContext ?: return Build.MODEL
        val deviceSuffix = SharedPrefsHelper(context).getDeviceId().takeLast(6)
        return "ADS-${Build.MODEL.take(18)}-$deviceSuffix"
    }

    private fun resolveMode(context: Context): MeshMode {
        return if (hasInternetConnection(context)) {
            MeshMode.ONLINE_RECEIVER
        } else {
            MeshMode.OFFLINE_SENDER
        }
    }

    private fun hasInternetConnection(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun scheduleRetry(reason: String) {
        val context = appContext ?: return
        if (retryScheduled) return

        retryScheduled = true
        Log.w(TAG, "Scheduling mesh retry: $reason")
        mainHandler.postDelayed({
            retryScheduled = false
            ensureRunning(context)
        }, RETRY_DELAY_MS)
    }

    private fun restartTransportIfIdle(reason: String) {
        val context = appContext ?: return
        if (connectedEndpoints.isNotEmpty() || pendingConnectionEndpoints.isNotEmpty()) {
            return
        }

        Log.w(TAG, "Restarting idle mesh transport: $reason")
        stopAdvertising()
        stopDiscovery()
        ensureRunning(context)
    }

    private fun scheduleIdleRecoveryIfNeeded() {
        val context = appContext ?: return
        if (idleRecoveryScheduled) return

        idleRecoveryScheduled = true
        mainHandler.postDelayed({
            idleRecoveryScheduled = false
            val hasPendingDelivery = EarthquakeRelayStore(context).getPendingReports().isNotEmpty() ||
                PendingEarthquakeTriggerStore(context).getPendingTriggers().isNotEmpty()
            if (!hasPendingDelivery) {
                return@postDelayed
            }
            if (connectedEndpoints.isEmpty()) {
                restartTransportIfIdle("idle recovery")
                scheduleIdleRecoveryIfNeeded()
            }
        }, RETRY_DELAY_MS)
    }
}
