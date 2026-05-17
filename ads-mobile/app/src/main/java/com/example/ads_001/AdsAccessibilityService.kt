package com.example.ads_001

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AdsAccessibilityService : AccessibilityService() {

    companion object {
        var shouldAutoClick = false          // WiFi toggle
        var shouldEnableMobileData = false   // Mobile data toggle
        @Volatile
        var desiredWifiEnabled: Boolean? = null
        @Volatile
        var desiredHotspotEnabled: Boolean? = null
        @Volatile
        var pendingWifiConnection: PendingWifiConnection? = null
        @Volatile
        var desiredLocationEnabled: Boolean? = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!shouldAutoClick &&
            !shouldEnableMobileData &&
            desiredWifiEnabled == null &&
            desiredHotspotEnabled == null &&
            pendingWifiConnection == null &&
            desiredLocationEnabled == null
        ) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {

            val rootNode = rootInActiveWindow ?: return

            // --- WiFi toggle ---
            val targetWifiState = desiredWifiEnabled ?: if (shouldAutoClick) true else null
            if (targetWifiState != null) {
                val wifiKeywords = if (targetWifiState) {
                    listOf("Aç", "Turn on", "Wi-Fi'yi aç", "Wi-Fi", "WLAN", "WLAN'ı aç", "Kapalı", "Off")
                } else {
                    listOf("Kapat", "Turn off", "Wi-Fi'yi kapat", "Wi-Fi", "WLAN", "Açık", "On")
                }
                if (clickByKeywords(rootNode, wifiKeywords, desiredChecked = targetWifiState)) {
                    shouldAutoClick = false
                    desiredWifiEnabled = null
                    return
                }
                if (findAndClickSwitch(rootNode, "wifi", desiredChecked = targetWifiState)) {
                    shouldAutoClick = false
                    desiredWifiEnabled = null
                    return
                }
            }

            // --- Hotspot toggle ---
            val targetHotspotState = desiredHotspotEnabled
            if (targetHotspotState != null) {
                val hotspotKeywords = if (targetHotspotState) {
                    listOf(
                        "Hotspot", "Erişim Noktası", "Kişisel Erişim Noktası",
                        "Taşınabilir erişim noktası", "Portable hotspot", "Personal Hotspot"
                    )
                } else {
                    listOf(
                        "Hotspot", "Erişim Noktası", "Kişisel Erişim Noktası",
                        "Taşınabilir erişim noktası", "Portable hotspot", "Personal Hotspot"
                    )
                }
                if (findAndClickSwitch(rootNode, "hotspot", desiredChecked = targetHotspotState)) {
                    desiredHotspotEnabled = null
                    return
                }
                if (clickByKeywords(rootNode, hotspotKeywords, desiredChecked = targetHotspotState)) {
                    desiredHotspotEnabled = null
                    return
                }
            }

            // --- Mobile data toggle ---
            if (shouldEnableMobileData) {
                val dataKeywords = listOf(
                    "Mobil veri", "Mobile data", "Hücresel veri", "Cellular data",
                    "Mobil ağ", "Mobile network", "Veri", "Data"
                )
                if (clickByKeywords(rootNode, dataKeywords, desiredChecked = true)) {
                    shouldEnableMobileData = false
                    return
                }
                if (findAndClickSwitch(rootNode, "data", desiredChecked = true)) {
                    shouldEnableMobileData = false
                    return
                }
            }

            // --- WiFi network connection ---
            val pendingConnection = pendingWifiConnection
            if (pendingConnection != null) {
                if (handlePendingWifiConnection(rootNode, pendingConnection)) {
                    return
                }
            }

            // --- Location toggle ---
            val targetLocationState = desiredLocationEnabled
            if (targetLocationState != null) {
                if (findAndClickSwitch(rootNode, "location", desiredChecked = targetLocationState)) {
                    desiredLocationEnabled = null
                    return
                }

                val locationKeywords = if (targetLocationState) {
                    listOf("Konum", "Location", "Konum Erişimi", "Location access", "Kapalı", "Off")
                } else {
                    listOf("Konum", "Location", "Konum Erişimi", "Location access", "Açık", "On")
                }
                if (clickByKeywords(rootNode, locationKeywords, desiredChecked = targetLocationState)) {
                    desiredLocationEnabled = null
                    return
                }
            }
        }
    }

    private fun clickByKeywords(
        root: AccessibilityNodeInfo,
        keywords: List<String>,
        desiredChecked: Boolean
    ): Boolean {
        for (keyword in keywords) {
            val nodes = root.findAccessibilityNodeInfosByText(keyword)
            for (node in nodes) {
                if (node.isClickable) {
                    if (node.className?.toString()?.let {
                            it.contains("Switch") || it.contains("Button") || it.contains("Toggle")
                        } == true) {
                        if (node.isCheckable && node.isChecked == desiredChecked) {
                            Log.d("AdsAccessibility", "Already in desired state: ${node.text} / ${node.className}")
                            return true
                        }
                        if (!node.isCheckable || node.isChecked != desiredChecked) {
                            Log.d("AdsAccessibility", "Clicking: ${node.text} / ${node.className}")
                            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            return true
                        }
                    }
                } else {
                    val parent = node.parent
                    if (parent != null && parent.isClickable) {
                        if (parent.isCheckable && parent.isChecked == desiredChecked) {
                            Log.d("AdsAccessibility", "Parent already in desired state: ${parent.className}")
                            return true
                        }
                        Log.d("AdsAccessibility", "Clicking parent: ${parent.className}")
                        parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun handlePendingWifiConnection(
        root: AccessibilityNodeInfo,
        connection: PendingWifiConnection
    ): Boolean {
        val editableNode = findEditableNode(root)
        if (editableNode != null) {
            val textSet = setNodeText(editableNode, connection.passphrase)
            val connectClicked = clickSimpleKeywords(root, listOf("Bağlan", "Connect", "Join", "Tamam", "OK"))
            return textSet || connectClicked
        }

        return clickSimpleKeywords(root, listOf(connection.ssid))
    }

    private fun findAndClickSwitch(node: AccessibilityNodeInfo?, hint: String, desiredChecked: Boolean): Boolean {
        if (node == null) return false
        val className = node.className?.toString()
        if (className != null && (className.contains("Switch") || className.contains("ToggleButton"))) {
            if (node.isChecked == desiredChecked) {
                Log.d("AdsAccessibility", "[$hint] Switch already in desired state")
                return true
            }
            if (node.isClickable) {
                Log.d("AdsAccessibility", "[$hint] Clicking switch: $className")
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            } else if (node.parent?.isClickable == true) {
                Log.d("AdsAccessibility", "[$hint] Clicking parent of switch")
                node.parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }
        }
        for (i in 0 until node.childCount) {
            if (findAndClickSwitch(node.getChild(i), hint, desiredChecked)) return true
        }
        return false
    }

    private fun clickSimpleKeywords(root: AccessibilityNodeInfo, keywords: List<String>): Boolean {
        for (keyword in keywords) {
            val nodes = root.findAccessibilityNodeInfosByText(keyword)
            for (node in nodes) {
                if (node.isClickable) {
                    Log.d("AdsAccessibility", "Clicking text node: $keyword / ${node.className}")
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return true
                }
                val parent = node.parent
                if (parent != null && parent.isClickable) {
                    Log.d("AdsAccessibility", "Clicking parent for text node: $keyword / ${parent.className}")
                    parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return true
                }
            }
        }
        return false
    }

    private fun findEditableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        val className = node.className?.toString() ?: ""
        if (className.contains("EditText") && node.isEditable) {
            return node
        }
        for (i in 0 until node.childCount) {
            val match = findEditableNode(node.getChild(i))
            if (match != null) {
                return match
            }
        }
        return null
    }

    private fun setNodeText(node: AccessibilityNodeInfo, text: String): Boolean {
        return try {
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        } catch (e: Exception) {
            Log.e("AdsAccessibility", "Failed to set text: ${e.message}")
            false
        }
    }

    override fun onInterrupt() {
        Log.e("AdsAccessibility", "Service interrupted")
    }
}

data class PendingWifiConnection(
    val ssid: String,
    val passphrase: String
)
