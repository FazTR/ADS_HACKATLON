package com.example.ads_001

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

object LanRelayManager {
    private const val TAG = "LanRelayManager"
    private const val UDP_PORT = 42777
    private const val SOCKET_BUFFER_SIZE = 16 * 1024
    private const val MULTICAST_LOCK_TAG = "ADS_LAN_RELAY"

    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val listenerRunning = AtomicBoolean(false)

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var listenerSocket: DatagramSocket? = null

    @Volatile
    private var multicastLock: WifiManager.MulticastLock? = null

    fun ensureRunning(context: Context) {
        appContext = context.applicationContext
        ensureMulticastLock()
        startListenerIfNeeded()
    }

    fun broadcastReport(context: Context, report: MeshEarthquakeReport) {
        ensureRunning(context)
        sendEnvelope(MeshRelayEnvelope(type = MeshRelayEnvelope.TYPE_REPORT, report = report))
    }

    fun broadcastAck(context: Context, messageId: String) {
        ensureRunning(context)
        sendEnvelope(
            MeshRelayEnvelope(
                type = MeshRelayEnvelope.TYPE_ACK,
                acknowledgedMessageId = messageId
            )
        )
    }

    fun stop() {
        listenerRunning.set(false)
        listenerSocket?.close()
        listenerSocket = null
        releaseMulticastLock()
        scope.cancel()
    }

    private fun startListenerIfNeeded() {
        if (!listenerRunning.compareAndSet(false, true)) return

        scope.launch {
            while (listenerRunning.get()) {
                try {
                    val socket = DatagramSocket(null).apply {
                        reuseAddress = true
                        soTimeout = 0
                        broadcast = true
                        bind(InetSocketAddress(UDP_PORT))
                    }
                    listenerSocket = socket
                    Log.i(TAG, "LAN relay listener started on UDP $UDP_PORT")

                    val buffer = ByteArray(SOCKET_BUFFER_SIZE)
                    while (listenerRunning.get()) {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        handlePacket(packet)
                    }
                } catch (e: SocketException) {
                    if (listenerRunning.get()) {
                        Log.e(TAG, "LAN relay socket error: ${e.message}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "LAN relay listener error: ${e.message}")
                } finally {
                    listenerSocket?.close()
                    listenerSocket = null
                }
            }
        }
    }

    private fun handlePacket(packet: DatagramPacket) {
        val context = appContext ?: return
        val ownDeviceId = SharedPrefsHelper(context).getDeviceId()
        val json = String(packet.data, packet.offset, packet.length, StandardCharsets.UTF_8)

        try {
            val envelope = gson.fromJson(json, MeshRelayEnvelope::class.java)
            when (envelope.type) {
                MeshRelayEnvelope.TYPE_REPORT -> {
                    val report = envelope.report ?: return
                    if (report.originDeviceId == ownDeviceId) return
                    Log.i(TAG, "Received LAN report ${report.messageId} from ${packet.address.hostAddress}")
                    DataTransmissionManager.onLanReportReceived(
                        context,
                        report,
                        packet.address.hostAddress ?: "lan"
                    )
                }

                MeshRelayEnvelope.TYPE_ACK -> {
                    val messageId = envelope.acknowledgedMessageId ?: return
                    Log.i(TAG, "Received LAN ack $messageId from ${packet.address.hostAddress}")
                    DataTransmissionManager.onLanAckReceived(
                        context,
                        messageId,
                        packet.address.hostAddress ?: "lan"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse LAN payload: ${e.message}")
        }
    }

    private fun sendEnvelope(envelope: MeshRelayEnvelope) {
        val context = appContext ?: return
        ensureMulticastLock()
        val targets = getBroadcastTargets(context)
        if (targets.isEmpty()) {
            Log.w(TAG, "No LAN broadcast target available")
            return
        }

        scope.launch {
            val payload = gson.toJson(envelope).toByteArray(StandardCharsets.UTF_8)
            targets.forEach { address ->
                runCatching {
                    DatagramSocket().use { socket ->
                        socket.broadcast = true
                        socket.send(DatagramPacket(payload, payload.size, address, UDP_PORT))
                    }
                    Log.i(TAG, "Sent LAN ${envelope.type} to ${address.hostAddress}:$UDP_PORT")
                }.onFailure { e ->
                    Log.e(TAG, "Failed LAN send to ${address.hostAddress}: ${e.message}")
                }
            }
        }
    }

    private fun getBroadcastTargets(context: Context): Set<InetAddress> {
        val targets = linkedSetOf<InetAddress>()
        targets.add(InetAddress.getByName("255.255.255.255"))

        if (!isWifiTransportActive(context)) {
            return targets
        }

        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val dhcpInfo = wifiManager.dhcpInfo
        if (dhcpInfo != null && dhcpInfo.netmask != 0) {
            val broadcast = (dhcpInfo.ipAddress and dhcpInfo.netmask) or dhcpInfo.netmask.inv()
            val addressBytes = byteArrayOf(
                (broadcast and 0xff).toByte(),
                (broadcast shr 8 and 0xff).toByte(),
                (broadcast shr 16 and 0xff).toByte(),
                (broadcast shr 24 and 0xff).toByte()
            )
            runCatching {
                InetAddress.getByAddress(addressBytes)
            }.getOrNull()?.let { targets.add(it) }
        }

        return targets.filterIsInstance<Inet4Address>().toSet()
    }

    private fun isWifiTransportActive(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun ensureMulticastLock() {
        val context = appContext ?: return
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val existingLock = multicastLock
        if (existingLock != null) {
            if (!existingLock.isHeld) {
                runCatching { existingLock.acquire() }
                    .onFailure { e -> Log.e(TAG, "Failed to re-acquire multicast lock: ${e.message}") }
            }
            return
        }

        runCatching {
            wifiManager.createMulticastLock(MULTICAST_LOCK_TAG).apply {
                setReferenceCounted(false)
                acquire()
            }
        }.onSuccess { lock ->
            multicastLock = lock
            Log.i(TAG, "Acquired Wi-Fi multicast lock")
        }.onFailure { e ->
            Log.e(TAG, "Failed to acquire multicast lock: ${e.message}")
        }
    }

    private fun releaseMulticastLock() {
        val lock = multicastLock ?: return
        runCatching {
            if (lock.isHeld) {
                lock.release()
            }
        }.onFailure { e ->
            Log.e(TAG, "Failed to release multicast lock: ${e.message}")
        }
        multicastLock = null
    }
}
