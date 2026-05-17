package com.example.ads_001

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log

object HotspotRelayManager {
    private const val TAG = "HotspotRelayManager"
    private const val RELAY_SSID = "Arda's Pura 80 Ultra"
    private const val RELAY_PASSPHRASE = "12345678"
    private const val WIFI_CONNECT_FALLBACK_DELAY_MS = 3_000L

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var activeSuggestion: WifiNetworkSuggestion? = null

    @Volatile
    private var wifiConnectFallbackScheduled = false

    @Volatile
    private var hotspotEnableRequested = false

    fun ensureRunning(context: Context) {
        val appCtx = context.applicationContext
        appContext = appCtx

        when {
            hasValidatedCellularInternet(appCtx) -> {
                clearPendingWifiAutomation()
                if (!hotspotEnableRequested) {
                    hotspotEnableRequested = true
                    ensureRelayHostHotspotEnabled(appCtx)
                }
            }

            hasPendingRelayPayload(appCtx) -> {
                hotspotEnableRequested = false
                AdsAccessibilityService.desiredHotspotEnabled = null
                ensureRelayClientConnection(appCtx)
            }

            else -> {
                hotspotEnableRequested = false
                AdsAccessibilityService.desiredHotspotEnabled = null
                if (isConnectedToSuggestedRelayHotspot(appCtx)) {
                    clearPendingWifiAutomation()
                }
            }
        }
    }

    fun onMeshPeerConnected(context: Context, endpointId: String) {
        Unit
    }

    fun onHotspotAnnouncementReceived(context: Context, hotspotInfo: HotspotRelayInfo) {
        ensureRelayClientConnection(context.applicationContext)
    }

    fun isConnectedToSuggestedRelayHotspot(context: Context): Boolean {
        return getCurrentWifiSsid(context.applicationContext) == RELAY_SSID
    }

    private fun ensureRelayHostHotspotEnabled(context: Context) {
        if (!ensureWifiDisabledForRelayHost(context)) {
            return
        }

        AdsAccessibilityService.desiredHotspotEnabled = true
        TransmissionStatusTracker.update(
            context,
            "İnterneti paylaşmak için erişim noktası açılıyor",
            TransmissionStatusLevel.INFO
        )
        openHotspotSettings(context)
    }

    private fun ensureRelayClientConnection(context: Context) {
        if (isConnectedToSuggestedRelayHotspot(context)) {
            clearPendingWifiAutomation()
            return
        }

        if (!ensureWifiEnabled(context)) {
            return
        }

        addWifiSuggestionIfPossible(context)
        AdsAccessibilityService.pendingWifiConnection =
            PendingWifiConnection(RELAY_SSID, RELAY_PASSPHRASE)
        scheduleWifiSettingsFallback(context)
    }

    @Suppress("DEPRECATION")
    private fun ensureWifiEnabled(context: Context): Boolean {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (wifiManager.isWifiEnabled) {
            return true
        }

        AdsAccessibilityService.desiredWifiEnabled = true
        TransmissionStatusTracker.update(
            context,
            "Bağlanmak için Wi-Fi açılıyor",
            TransmissionStatusLevel.INFO
        )
        openWifiSettings(context)
        return false
    }

    @Suppress("DEPRECATION")
    private fun ensureWifiDisabledForRelayHost(context: Context): Boolean {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (!wifiManager.isWifiEnabled) {
            return true
        }

        AdsAccessibilityService.desiredWifiEnabled = false
        TransmissionStatusTracker.update(
            context,
            "İnterneti paylaşmak için Wi-Fi kapatılıyor",
            TransmissionStatusLevel.INFO
        )
        openWifiSettings(context)
        return false
    }

    private fun addWifiSuggestionIfPossible(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return
        }

        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val suggestion = WifiNetworkSuggestion.Builder()
            .setSsid(RELAY_SSID)
            .setWpa2Passphrase(RELAY_PASSPHRASE)
            .build()

        activeSuggestion?.let { previousSuggestion ->
            if (previousSuggestion == suggestion) {
                return
            }
            runCatching { wifiManager.removeNetworkSuggestions(listOf(previousSuggestion)) }
                .onFailure { e -> Log.w(TAG, "Failed to remove old Wi-Fi suggestion: ${e.message}") }
        }

        val result = wifiManager.addNetworkSuggestions(listOf(suggestion))
        val accepted = result == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS ||
            result == WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_DUPLICATE

        if (accepted) {
            activeSuggestion = suggestion
            Log.i(TAG, "Relay Wi-Fi suggestion ready for SSID=$RELAY_SSID")
        } else {
            Log.w(TAG, "Relay Wi-Fi suggestion failed with code=$result")
        }
    }

    private fun scheduleWifiSettingsFallback(context: Context) {
        if (wifiConnectFallbackScheduled) {
            return
        }

        wifiConnectFallbackScheduled = true
        mainHandler.postDelayed({
            wifiConnectFallbackScheduled = false
            if (isConnectedToSuggestedRelayHotspot(context.applicationContext)) {
                clearPendingWifiAutomation()
                return@postDelayed
            }

            TransmissionStatusTracker.update(
                context,
                "Arda's Pura 80 Ultra ağına bağlanılıyor",
                TransmissionStatusLevel.INFO
            )
            openWifiSettings(context)
        }, WIFI_CONNECT_FALLBACK_DELAY_MS)
    }

    private fun hasPendingRelayPayload(context: Context): Boolean {
        return EarthquakeRelayStore(context).getPendingReports().isNotEmpty() ||
            PendingEarthquakeTriggerStore(context).getPendingTriggers().isNotEmpty()
    }

    private fun clearPendingWifiAutomation() {
        AdsAccessibilityService.pendingWifiConnection = null
    }

    private fun openWifiSettings(context: Context) {
        runCatching {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure { e ->
            Log.e(TAG, "Could not open Wi-Fi settings: ${e.message}")
        }
    }

    private fun openHotspotSettings(context: Context) {
        val tetherIntent = Intent("android.settings.TETHER_SETTINGS").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val fallbackIntent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        runCatching {
            context.startActivity(tetherIntent)
        }.recoverCatching {
            context.startActivity(fallbackIntent)
        }.onFailure { e ->
            Log.e(TAG, "Could not open hotspot settings: ${e.message}")
        }
    }

    private fun hasValidatedCellularInternet(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    private fun getCurrentWifiSsid(context: Context): String? {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return null
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return null
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return null
        }

        @Suppress("DEPRECATION")
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val rawSsid = wifiManager.connectionInfo?.ssid?.replace("\"", "") ?: return null
        return rawSsid.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
    }
}
