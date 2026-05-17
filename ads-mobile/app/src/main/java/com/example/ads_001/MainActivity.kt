package com.example.ads_001

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.widget.ArrayAdapter
import android.widget.AdapterView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.ads_001.network.AdsApiClient
import com.example.ads_001.network.models.RegisterRequest
import com.example.ads_001.network.TurkiyeApiClient
import com.example.ads_001.network.models.ProvinceData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log
import android.text.TextUtils
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.PowerManager
import android.view.Gravity
import android.view.KeyEvent
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.view.setPadding
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var systemIndicatorDot: View
    private lateinit var wifiStatusText: TextView
    private lateinit var wifiIndicatorDot: View
    private lateinit var transmissionStatusText: TextView
    private lateinit var transmissionIndicatorDot: View
    private lateinit var assistantMessagesScroll: ScrollView
    private lateinit var assistantMessagesContainer: LinearLayout
    private lateinit var assistantInput: TextInputEditText
    private lateinit var assistantSendButton: Button
    private lateinit var assistantVoiceButton: Button
    private lateinit var btnAssistantVoiceReply: Button
    private lateinit var btnAssistantHistory: Button
    private lateinit var assistantHistoryScrim: View
    private lateinit var assistantHistoryPanel: View
    private lateinit var assistantHistoryTitle: TextView
    private lateinit var assistantHistoryEmptyText: TextView
    private lateinit var assistantHistoryContainer: LinearLayout
    private lateinit var assistantHistoryScroll: ScrollView
    private lateinit var btnNewAssistantConversation: Button
    private lateinit var btnCloseAssistantHistory: Button
    private lateinit var btnEditProfile: Button
    private lateinit var btnDeleteAccount: Button

    private lateinit var layoutDashboard: View
    private lateinit var layoutRegistration: View
    private lateinit var etFullName: EditText
    private lateinit var etBirthDate: EditText
    private lateinit var spinnerCity: Spinner
    private lateinit var spinnerDistrict: Spinner
    private lateinit var etAddress: EditText
    private lateinit var spinnerGender: Spinner
    private lateinit var spinnerBloodType: Spinner
    private lateinit var btnRegister: Button
    private lateinit var btnCancelEdit: Button
    
    private lateinit var sharedPrefsHelper: SharedPrefsHelper
    private lateinit var assistantConversationStore: AssistantConversationStore
    private var isAutoWifiAttempted = false
    private var provincesList: List<ProvinceData> = emptyList()
    private var locationRefreshRequestedThisLaunch = false
    private var batteryOptimizationPromptShownThisLaunch = false
    private var pendingVoiceSubmission = false

    private val wifiStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updateWifiStatus()
        }
    }

    private val transmissionStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updateTransmissionStatus()
        }
    }

    private val requiredPermissions = buildList {
        add(Manifest.permission.CAMERA)
        add(Manifest.permission.RECORD_AUDIO)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        var allGranted = true
        permissions.entries.forEach {
            if (!it.value) {
                allGranted = false
            }
        }
        
        if (allGranted) {
            updateSystemStatus()
            handleInitialFlow()
        } else {
            updateSystemStatus()
            Toast.makeText(this, "Uygulamanın tam çalışması için izinlere ihtiyaç var", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPrefsHelper = SharedPrefsHelper(this)
        assistantConversationStore = AssistantConversationStore(this)
        OfflineSpeechModelManager.warmUp(this)

        statusText = findViewById(R.id.statusText)
        systemIndicatorDot = findViewById(R.id.systemIndicatorDot)
        wifiStatusText = findViewById(R.id.wifiStatusText)
        wifiIndicatorDot = findViewById(R.id.wifiIndicatorDot)
        transmissionStatusText = findViewById(R.id.transmissionStatusText)
        transmissionIndicatorDot = findViewById(R.id.transmissionIndicatorDot)
        assistantMessagesScroll = findViewById(R.id.assistantMessagesScroll)
        assistantMessagesContainer = findViewById(R.id.assistantMessagesContainer)
        assistantInput = findViewById(R.id.assistantInput)
        assistantSendButton = findViewById(R.id.assistantSendButton)
        assistantVoiceButton = findViewById(R.id.assistantVoiceButton)
        btnAssistantVoiceReply = findViewById(R.id.btnAssistantVoiceReply)
        btnAssistantHistory = findViewById(R.id.btnAssistantHistory)
        assistantHistoryScrim = findViewById(R.id.assistantHistoryScrim)
        assistantHistoryPanel = findViewById(R.id.assistantHistoryPanel)
        assistantHistoryTitle = findViewById(R.id.assistantHistoryTitle)
        assistantHistoryEmptyText = findViewById(R.id.assistantHistoryEmptyText)
        assistantHistoryContainer = findViewById(R.id.assistantHistoryContainer)
        assistantHistoryScroll = findViewById(R.id.assistantHistoryScroll)
        btnNewAssistantConversation = findViewById(R.id.btnNewAssistantConversation)
        btnCloseAssistantHistory = findViewById(R.id.btnCloseAssistantHistory)
        btnEditProfile = findViewById(R.id.btnEditProfile)

        layoutDashboard = findViewById(R.id.layout_dashboard)
        layoutRegistration = findViewById(R.id.layout_registration)
        etFullName = findViewById(R.id.etFullName)
        etBirthDate = findViewById(R.id.etBirthDate)
        spinnerCity = findViewById(R.id.spinnerCity)
        spinnerDistrict = findViewById(R.id.spinnerDistrict)
        etAddress = findViewById(R.id.etAddress)
        spinnerGender = findViewById(R.id.spinnerGender)
        spinnerBloodType = findViewById(R.id.spinnerBloodType)
        btnRegister = findViewById(R.id.btnRegister)
        btnCancelEdit = findViewById(R.id.btnCancelEdit)
        btnDeleteAccount = findViewById(R.id.btnDeleteAccount)

        etBirthDate.setOnClickListener {
            val parts = etBirthDate.text.toString().split("-")
            val year = if (parts.size == 3) parts[0].toIntOrNull() ?: 1985 else 1985
            val month = if (parts.size == 3) (parts[1].toIntOrNull() ?: 1) - 1 else 0
            val day = if (parts.size == 3) parts[2].toIntOrNull() ?: 1 else 1

            val datePickerDialog = android.app.DatePickerDialog(
                this,
                @Suppress("DEPRECATION")
                android.app.AlertDialog.THEME_HOLO_DARK,
                { _, selectedYear, selectedMonth, selectedDay ->
                    val formattedMonth = (selectedMonth + 1).toString().padStart(2, '0')
                    val formattedDay = selectedDay.toString().padStart(2, '0')
                    etBirthDate.setText("$selectedYear-$formattedMonth-$formattedDay")
                },
                year,
                month,
                day
            )
            datePickerDialog.show()
        }

        val wifiFilter = IntentFilter().apply {
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction(WifiManager.RSSI_CHANGED_ACTION)
        }
        registerReceiver(wifiStateReceiver, wifiFilter)
        ContextCompat.registerReceiver(
            this,
            transmissionStatusReceiver,
            IntentFilter(TransmissionStatusTracker.ACTION_STATUS_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        layoutDashboard.visibility = View.GONE
        layoutRegistration.visibility = View.GONE

        // Check and request permissions on startup
        checkAndRequestPermissions()

        btnRegister.setOnClickListener { registerUser() }
        assistantVoiceButton.setOnClickListener { toggleAssistantListening() }
        btnAssistantVoiceReply.setOnClickListener { toggleVoiceReplies() }
        btnAssistantHistory.setOnClickListener { toggleAssistantHistoryPanel() }
        btnNewAssistantConversation.setOnClickListener { startNewAssistantConversation() }
        btnCloseAssistantHistory.setOnClickListener { hideAssistantHistoryPanel() }
        assistantHistoryScrim.setOnClickListener { hideAssistantHistoryPanel() }
        assistantSendButton.setOnClickListener { submitAssistantCommand() }
        assistantInput.setOnEditorActionListener { _, actionId, event ->
            val imeSend = actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND
            val enterPressed = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (imeSend || enterPressed) {
                submitAssistantCommand()
                true
            } else {
                false
            }
        }
        updateVoiceReplyButton()
        
        btnEditProfile.setOnClickListener {
            Toast.makeText(this@MainActivity, "Sunucudan bilgileriniz çekiliyor...", Toast.LENGTH_SHORT).show()
            
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val response = AdsApiClient.apiService.getProfile(sharedPrefsHelper.getDeviceId())
                    withContext(Dispatchers.Main) {
                        val profile = sharedPrefsHelper.getUserProfile()
                        
                        if (response.isSuccessful && response.body() != null) {
                            val apiData = response.body()!!
                            etFullName.setText(apiData.fullName ?: profile.fullName)
                            if (!apiData.birthDate.isNullOrEmpty()) {
                                etBirthDate.setText(apiData.birthDate)
                            } else if (profile.birthDate.isNotEmpty()) {
                                etBirthDate.setText(profile.birthDate)
                            }
                            if (!apiData.address.isNullOrEmpty()) {
                                etAddress.setText(apiData.address)
                            } else if (profile.address.isNotEmpty()) {
                                etAddress.setText(profile.address)
                            }
                            
                            val genderArray = resources.getStringArray(R.array.gender_array)
                            if (!apiData.gender.isNullOrEmpty()) {
                                val gIndex = genderArray.indexOf(apiData.gender)
                                if (gIndex >= 0) spinnerGender.setSelection(gIndex)
                            }
                            
                            val bloodTypeArray = resources.getStringArray(R.array.blood_type_array)
                            if (!apiData.bloodType.isNullOrEmpty()) {
                                val bIndex = bloodTypeArray.indexOf(apiData.bloodType)
                                if (bIndex >= 0) spinnerBloodType.setSelection(bIndex)
                            }

                            sharedPrefsHelper.saveUserProfile(
                                etFullName.text.toString(),
                                etBirthDate.text.toString(),
                                etAddress.text.toString()
                            )
                        } else {
                            Toast.makeText(this@MainActivity, "Sunucuya ulaşılamadı. Yerel veriler gösteriliyor.", Toast.LENGTH_SHORT).show()
                            if (profile.fullName.isNotEmpty()) etFullName.setText(profile.fullName)
                            if (profile.birthDate.isNotEmpty()) etBirthDate.setText(profile.birthDate)
                            if (profile.address.isNotEmpty()) etAddress.setText(profile.address)
                        }
                        
                        layoutDashboard.visibility = View.GONE
                        layoutRegistration.visibility = View.VISIBLE
                        btnRegister.text = "Güncelle"
                        btnCancelEdit.visibility = View.VISIBLE
                        btnDeleteAccount.visibility = View.VISIBLE

                        if (provincesList.isEmpty()) {
                            loadProvinces()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Bağlantı hatası. Yerel veriler gösteriliyor.", Toast.LENGTH_SHORT).show()
                        val profile = sharedPrefsHelper.getUserProfile()
                        if (profile.fullName.isNotEmpty()) etFullName.setText(profile.fullName)
                        if (profile.birthDate.isNotEmpty()) etBirthDate.setText(profile.birthDate)
                        if (profile.address.isNotEmpty()) etAddress.setText(profile.address)
                        
                        layoutDashboard.visibility = View.GONE
                        layoutRegistration.visibility = View.VISIBLE
                        btnRegister.text = "Güncelle"
                        btnCancelEdit.visibility = View.VISIBLE
                        btnDeleteAccount.visibility = View.VISIBLE

                        if (provincesList.isEmpty()) {
                            loadProvinces()
                        }
                    }
                }
            }
        }

        btnCancelEdit.setOnClickListener {
            handleInitialFlow()
        }

        btnDeleteAccount.setOnClickListener {
            Toast.makeText(this, "Hesap silme akisi henuz baglanmadi.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::statusText.isInitialized) {
            updateSystemStatus()
        }
        updateWifiStatus()
        updateTransmissionStatus()
    }

    override fun onStop() {
        super.onStop()
        stopAssistantListening(resetField = false)
    }

    override fun onDestroy() {
        LocalSpeechRecognizer.shutdown()
        LocalSpeechOutput.shutdown()
        super.onDestroy()
        try {
            unregisterReceiver(wifiStateReceiver)
        } catch (e: Exception) { }
        try {
            unregisterReceiver(transmissionStatusReceiver)
        } catch (e: Exception) { }
    }

    @Suppress("DEPRECATION")
    private fun updateWifiStatus() {
        if (!::wifiStatusText.isInitialized) return

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        if (!wifiManager.isWifiEnabled) {
            wifiStatusText.text = "Wi-Fi kapalı"
            wifiStatusText.setTextColor(Color.parseColor("#FF5252"))
            wifiIndicatorDot.backgroundTintList =
                android.content.res.ColorStateList.valueOf(Color.parseColor("#FF5252"))
            return
        }

        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = if (activeNetwork != null) connectivityManager.getNetworkCapabilities(activeNetwork) else null
        val isWifiConnected = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        if (!isWifiConnected) {
            wifiStatusText.text = "Bağlantı aranıyor"
            wifiStatusText.setTextColor(Color.parseColor("#FFB300"))
            wifiIndicatorDot.backgroundTintList =
                android.content.res.ColorStateList.valueOf(Color.parseColor("#FFB300"))
            return
        }

        // Direct RSSI threshold — more reliable than calculateSignalLevel
        val rssi = wifiManager.connectionInfo?.rssi ?: -100
        val quality = when {
            rssi >= -55 -> "Mükemmel"
            rssi >= -65 -> "İyi"
            rssi >= -75 -> "Orta"
            else        -> "Zayıf"
        }
        val dotColor = when {
            rssi >= -55 -> "#00E676"
            rssi >= -65 -> "#69F0AE"
            rssi >= -75 -> "#FFB300"
            else        -> "#FF5252"
        }

        val rawSsid = wifiManager.connectionInfo?.ssid?.replace("\"", "") ?: ""
        val displayName = if (rawSsid.isNotEmpty() && rawSsid != "<unknown ssid>") "$rawSsid · $quality" else "Bağlı · $quality"

        wifiStatusText.text = displayName
        wifiStatusText.setTextColor(Color.parseColor(dotColor))
        wifiIndicatorDot.backgroundTintList =
            android.content.res.ColorStateList.valueOf(Color.parseColor(dotColor))
    }

    private fun updateTransmissionStatus() {
        if (!::transmissionStatusText.isInitialized) return

        val status = TransmissionStatusTracker.getCurrentStatus(this)
        val colorHex = when (status.level) {
            TransmissionStatusLevel.INFO -> "#4FC3F7"
            TransmissionStatusLevel.SUCCESS -> "#00E676"
            TransmissionStatusLevel.WARNING -> "#FFB300"
            TransmissionStatusLevel.ERROR -> "#FF5252"
        }

        transmissionStatusText.text = status.message
        transmissionStatusText.setTextColor(Color.parseColor(colorHex))
        transmissionIndicatorDot.backgroundTintList =
            android.content.res.ColorStateList.valueOf(Color.parseColor(colorHex))
    }

    private fun checkAndRequestPermissions() {
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            updateSystemStatus()
            handleInitialFlow()
        }
    }

    private fun updateSystemStatus() {
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        val hasAllPermissions = missingPermissions.isEmpty()
        val hasAccessibility = checkAccessibilityService()

        when {
            hasAllPermissions && hasAccessibility -> {
                statusText.text = "Uygulama hazır"
                statusText.setTextColor(Color.parseColor("#00E676"))
                systemIndicatorDot.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#00E676"))
            }
            hasAllPermissions && !hasAccessibility -> {
                statusText.text = "Tam koruma için ek izin gerekiyor"
                statusText.setTextColor(Color.parseColor("#FFB300"))
                systemIndicatorDot.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FFB300"))
            }
            else -> {
                statusText.text = "Devam etmek için izin ver"
                statusText.setTextColor(Color.parseColor("#FF5252"))
                systemIndicatorDot.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FF5252"))
            }
        }
    }

    private fun handleInitialFlow() {
        refreshCachedLocationOncePerLaunch()
        DataTransmissionManager.initializeRelay(this)
        startEarthquakeDetectionService()
        promptIgnoreBatteryOptimizationsIfNeeded()

        if (sharedPrefsHelper.isRegistered()) {
            layoutRegistration.visibility = View.GONE
            layoutDashboard.visibility = View.VISIBLE
            btnDeleteAccount.visibility = View.GONE
            hideAssistantHistoryPanel()
            restoreAssistantConversation()
            promptAccessibilityService()
        } else {
            layoutDashboard.visibility = View.GONE
            layoutRegistration.visibility = View.VISIBLE
            btnRegister.text = "Kaydet ve Sisteme Katıl"
            btnCancelEdit.visibility = View.GONE
            btnDeleteAccount.visibility = View.GONE
            hideAssistantHistoryPanel()
            if (provincesList.isEmpty()) {
                loadProvinces()
            }
        }
    }

    private fun refreshCachedLocationOncePerLaunch() {
        if (locationRefreshRequestedThisLaunch) return
        locationRefreshRequestedThisLaunch = true
        LocationCacheManager.refreshNow(this)
    }

    private fun startEarthquakeDetectionService() {
        try {
            val intent = Intent(this, EarthquakeDetectionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "Could not start EarthquakeDetectionService: ${e.message}")
        }
    }

    private fun promptIgnoreBatteryOptimizationsIfNeeded() {
        if (batteryOptimizationPromptShownThisLaunch || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (powerManager.isIgnoringBatteryOptimizations(packageName)) return

        batteryOptimizationPromptShownThisLaunch = true
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            Toast.makeText(
                this,
                "Arka planda mesh alimi icin pil optimizasyonunu kapatin.",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Log.w("MainActivity", "Could not request battery optimization exemption: ${e.message}")
        }
    }

    private fun restoreAssistantConversation(syncBotHistory: Boolean = true) {
        val conversation = assistantConversationStore.getActiveConversation()
        val messages = conversation.messages
        if (syncBotHistory) {
            EarthquakeCommandBot.restoreConversationHistory(messages)
        }
        assistantMessagesContainer.removeAllViews()

        if (messages.isEmpty()) {
            appendAssistantMessageBubble(
                "Deprem asistanı hazır. Yazabilir ya da konuş düğmesiyle yerel sesli giriş kullanabilirsin. Örnek komutlar: komutlar, konum, sarsıntı, son deprem, iyiyim, yaralıyım, enkaz altındayım, yardım çağır.",
                isUser = false
            )
            return
        }

        messages.forEach { message ->
            appendAssistantMessageBubble(message.text, message.isUser)
        }
    }

    private fun submitAssistantCommand() {
        val rawInput = assistantInput.text?.toString()?.trim().orEmpty()
        if (rawInput.isBlank()) return

        appendAssistantMessage(rawInput, isUser = true)
        assistantInput.text?.clear()
        assistantSendButton.isEnabled = false
        assistantVoiceButton.isEnabled = false
        assistantInput.isEnabled = false

        lifecycleScope.launch {
            val reply = withContext(Dispatchers.IO) {
                EarthquakeCommandBot.handle(this@MainActivity, rawInput) { progressMessage ->
                    runOnUiThread {
                        appendAssistantMessage(progressMessage, isUser = false, persist = false)
                    }
                }
            }
            appendAssistantMessage(reply, isUser = false)
            LocalSpeechOutput.speak(this@MainActivity, reply)
            assistantSendButton.isEnabled = true
            assistantVoiceButton.isEnabled = true
            assistantInput.isEnabled = true
            assistantInput.requestFocus()
        }
    }

    private fun toggleAssistantListening() {
        if (LocalSpeechRecognizer.isListening()) {
            assistantVoiceButton.isEnabled = false
            LocalSpeechRecognizer.stopListening()
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Konuşmak için mikrofon izni gerekiyor.", Toast.LENGTH_SHORT).show()
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            return
        }

        LocalSpeechOutput.stop()
        assistantInput.setText("")
        assistantInput.isEnabled = false
        assistantInput.hint = "Dinleniyor..."
        assistantVoiceButton.isEnabled = false

        lifecycleScope.launch {
            val startResult = withContext(Dispatchers.IO) {
                LocalSpeechRecognizer.startListening(
                    this@MainActivity,
                    object : LocalSpeechRecognizer.Listener {
                        override fun onListeningStarted() {
                            runOnUiThread {
                                assistantVoiceButton.text = "Dur"
                                assistantVoiceButton.isEnabled = true
                            }
                        }

                        override fun onPartialText(text: String) {
                            runOnUiThread {
                                if (!pendingVoiceSubmission) {
                                    assistantInput.setText(text)
                                    assistantInput.setSelection(assistantInput.text?.length ?: 0)
                                }
                            }
                        }

                        override fun onFinalText(text: String) {
                            runOnUiThread {
                                pendingVoiceSubmission = true
                                stopAssistantListening(resetField = false)
                                assistantInput.setText(text)
                                assistantInput.setSelection(assistantInput.text?.length ?: 0)
                                submitAssistantCommand()
                                pendingVoiceSubmission = false
                            }
                        }

                        override fun onError(message: String) {
                            runOnUiThread {
                                stopAssistantListening(resetField = true)
                                appendAssistantMessage(message, isUser = false, persist = false)
                            }
                        }

                        override fun onStopped() {
                            runOnUiThread {
                                if (!pendingVoiceSubmission) {
                                    stopAssistantListening(resetField = false)
                                }
                            }
                        }
                    }
                )
            }

            if (startResult is LocalSpeechRecognizer.StartResult.Unavailable) {
                stopAssistantListening(resetField = true)
                appendAssistantMessage(startResult.message, isUser = false, persist = false)
            }
        }
    }

    private fun stopAssistantListening(resetField: Boolean) {
        assistantVoiceButton.text = "Konuş"
        assistantVoiceButton.isEnabled = true
        assistantInput.isEnabled = true
        assistantInput.hint = "Deprem komutu yaz"
        if (resetField) {
            assistantInput.text?.clear()
        }
    }

    private fun toggleVoiceReplies() {
        val nextEnabled = !LocalSpeechOutput.isEnabled(this)
        LocalSpeechOutput.setEnabled(this, nextEnabled)
        updateVoiceReplyButton()
        val message = if (nextEnabled) {
            "Sesli cevap açıldı."
        } else {
            "Sesli cevap kapatıldı."
        }
        appendAssistantMessage(message, isUser = false, persist = false)
    }

    private fun updateVoiceReplyButton() {
        btnAssistantVoiceReply.text = if (LocalSpeechOutput.isEnabled(this)) "Ses Açık" else "Ses Kapalı"
    }

    private fun appendAssistantMessage(message: String, isUser: Boolean, persist: Boolean = true) {
        if (persist) {
            assistantConversationStore.appendMessageToActive(
                AssistantConversationMessage.create(
                    text = message,
                    isUser = isUser
                )
            )
            restoreAssistantConversation(syncBotHistory = false)
            return
        }

        appendAssistantMessageBubble(message, isUser)
    }

    private fun startNewAssistantConversation() {
        assistantConversationStore.createConversation()
        EarthquakeCommandBot.clearConversationHistory()
        restoreAssistantConversation(syncBotHistory = true)
        hideAssistantHistoryPanel()
        assistantInput.requestFocus()
    }

    private fun appendAssistantMessageBubble(message: String, isUser: Boolean) {
        val bubble = TextView(this).apply {
            text = message
            setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Medium)
            setLineSpacing(0f, 1.12f)
            setTextColor(if (isUser) Color.WHITE else Color.parseColor("#E2E8F0"))
            background = ContextCompat.getDrawable(
                this@MainActivity,
                if (isUser) R.drawable.bg_assistant_user_message else R.drawable.bg_assistant_bot_message
            )
            setPadding(dp(14))
            maxWidth = (resources.displayMetrics.widthPixels * 0.78f).roundToInt()
        }

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = if (isUser) Gravity.END else Gravity.START
            topMargin = if (assistantMessagesContainer.childCount == 0) 0 else dp(12)
        }

        assistantMessagesContainer.addView(bubble, params)
        assistantMessagesScroll.post {
            assistantMessagesScroll.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun toggleAssistantHistoryPanel() {
        if (assistantHistoryPanel.visibility == View.VISIBLE) {
            hideAssistantHistoryPanel()
        } else {
            showAssistantHistoryPanel()
        }
    }

    private fun showAssistantHistoryPanel() {
        renderAssistantHistoryPanel()
        assistantHistoryScrim.visibility = View.VISIBLE
        assistantHistoryPanel.visibility = View.VISIBLE
    }

    private fun hideAssistantHistoryPanel() {
        assistantHistoryScrim.visibility = View.GONE
        assistantHistoryPanel.visibility = View.GONE
    }

    private fun renderAssistantHistoryPanel() {
        val conversations = assistantConversationStore.getConversationSummaries()
        val activeConversationId = assistantConversationStore.getActiveConversationId()

        assistantHistoryTitle.text = "Konuşma Geçmişi (${conversations.size})"
        assistantHistoryContainer.removeAllViews()
        assistantHistoryEmptyText.visibility = if (conversations.isEmpty()) View.VISIBLE else View.GONE
        assistantHistoryScroll.visibility = if (conversations.isEmpty()) View.GONE else View.VISIBLE

        conversations.forEachIndexed { index, conversation ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14))
                background = ContextCompat.getDrawable(
                    this@MainActivity,
                    R.drawable.bg_assistant_bot_message
                )
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    assistantConversationStore.selectConversation(conversation.conversationId)
                    restoreAssistantConversation(syncBotHistory = true)
                    hideAssistantHistoryPanel()
                }
            }

            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            header.addView(TextView(this).apply {
                text = "${index + 1}. ${conversation.title}"
                setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Small)
                setTextColor(
                    if (conversation.conversationId == activeConversationId) {
                        Color.parseColor("#7DD3FC")
                    } else {
                        Color.parseColor("#E2E8F0")
                    }
                )
            }, LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ))

            header.addView(Button(this).apply {
                text = "Sil"
                isAllCaps = false
                setOnClickListener {
                    assistantConversationStore.deleteConversation(conversation.conversationId)
                    restoreAssistantConversation(syncBotHistory = true)
                    renderAssistantHistoryPanel()
                }
            })

            row.addView(header)
            row.addView(TextView(this).apply {
                text = "${formatAssistantMessageTime(conversation.updatedAtEpochMs)} · ${conversation.messages.size} mesaj"
                setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Small)
                setTextColor(Color.parseColor("#B0BEC5"))
                setPadding(0, dp(8), 0, 0)
            })
            row.addView(TextView(this).apply {
                text = buildConversationPreview(conversation)
                setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Medium)
                setTextColor(Color.parseColor("#E2E8F0"))
                setLineSpacing(0f, 1.12f)
                setPadding(0, dp(8), 0, 0)
                maxLines = 2
            })

            assistantHistoryContainer.addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (index > 0) {
                        topMargin = dp(12)
                    }
                }
            )
        }

        assistantHistoryScroll.post {
            assistantHistoryScroll.fullScroll(View.FOCUS_UP)
        }
    }

    private fun buildConversationPreview(conversation: AssistantConversationSession): String {
        return conversation.messages.lastOrNull()
            ?.text
            ?.trim()
            ?.replace(Regex("\\s+"), " ")
            ?.take(96)
            .orEmpty()
    }

    private fun formatAssistantMessageTime(timestampMs: Long): String {
        return SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("tr", "TR"))
            .format(Date(timestampMs))
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).roundToInt()
    }

    private fun loadProvinces() {
        lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                val loadingAdapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, listOf("İller Yükleniyor..."))
                loadingAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerCity.adapter = loadingAdapter
                spinnerCity.isEnabled = false
                spinnerDistrict.isEnabled = false
            }
            try {
                val response = TurkiyeApiClient.apiService.getProvinces()
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && response.body() != null) {
                        provincesList = response.body()!!.data.sortedBy { it.name }
                        val cityNames = provincesList.map { it.name }
                        
                        val cityAdapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, cityNames)
                        cityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        spinnerCity.adapter = cityAdapter
                        spinnerCity.isEnabled = true
                        spinnerDistrict.isEnabled = true

                        spinnerCity.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                                val selectedProvince = provincesList[position]
                                val districtNames = selectedProvince.districts.map { it.name }.sorted()
                                val districtAdapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, districtNames)
                                districtAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                                spinnerDistrict.adapter = districtAdapter
                            }
                            override fun onNothingSelected(parent: AdapterView<*>?) {}
                        }
                    } else {
                        Toast.makeText(this@MainActivity, "İller API'den çekilemedi", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Bağlantı hatası: İller yüklenemedi", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun registerUser() {
        val fullName = etFullName.text.toString().trim()
        val birthDate = etBirthDate.text.toString().trim()

        if (fullName.isEmpty() || birthDate.isEmpty()) {
            Toast.makeText(this, "Lütfen Ad Soyad ve Doğum Tarihi alanlarını doldurun.", Toast.LENGTH_SHORT).show()
            return
        }

        val dateRegex = Regex("^\\d{4}-\\d{2}-\\d{2}$")
        if (!dateRegex.matches(birthDate)) {
            etBirthDate.error = "Format YYYY-AA-GG olmalıdır"
            Toast.makeText(this, "Doğum Tarihi formatı hatalı!", Toast.LENGTH_SHORT).show()
            return
        }
        etBirthDate.error = null

        btnRegister.isEnabled = false
        btnRegister.text = "Kaydediliyor..."

        val deviceId = sharedPrefsHelper.getDeviceId()
        val request = RegisterRequest(
            deviceId = deviceId,
            fullName = fullName,
            birthDate = birthDate,
            address = etAddress.text.toString().trim(),
            city = spinnerCity.selectedItem?.toString() ?: "",
            district = spinnerDistrict.selectedItem?.toString() ?: "",
            gender = spinnerGender.selectedItem?.toString() ?: "",
            bloodType = spinnerBloodType.selectedItem?.toString() ?: ""
        )

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = AdsApiClient.apiService.registerUser(request)
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@MainActivity, "Güncelleme Başarılı!", Toast.LENGTH_SHORT).show()
                        sharedPrefsHelper.setRegistered(true)
                        sharedPrefsHelper.saveUserProfile(fullName, birthDate, etAddress.text.toString().trim())
                        btnRegister.isEnabled = true
                        btnRegister.text = "Güncelle"
                        handleInitialFlow()
                    } else {
                        btnRegister.isEnabled = true
                        btnRegister.text = if (sharedPrefsHelper.isRegistered()) "Güncelle" else "Kaydet ve Sisteme Katıl"
                        val errorBody = response.errorBody()?.string() ?: "Bilinmeyen hata"
                        Log.e("API_TEST", "Register Error: ${response.code()} - $errorBody")
                        Toast.makeText(this@MainActivity, "Hata (${response.code()}): $errorBody", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    btnRegister.isEnabled = true
                    btnRegister.text = if (sharedPrefsHelper.isRegistered()) "Güncelle" else "Kaydet ve Sisteme Katıl"
                    Toast.makeText(this@MainActivity, "Bağlantı Hatası: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun checkAccessibilityService(): Boolean {
        var accessibilityEnabled = 0
        val service = "${packageName}/${AdsAccessibilityService::class.java.canonicalName}"
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                applicationContext.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
        } catch (e: Settings.SettingNotFoundException) {
            Log.e("API_TEST", "Accessibility setting not found", e)
        }
        
        val splitter = TextUtils.SimpleStringSplitter(':')
        if (accessibilityEnabled == 1) {
            val settingValue = Settings.Secure.getString(
                applicationContext.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            if (settingValue != null) {
                splitter.setString(settingValue)
                while (splitter.hasNext()) {
                    val accessibilityService = splitter.next()
                    if (accessibilityService.equals(service, ignoreCase = true)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun promptAccessibilityService() {
        if (!checkAccessibilityService()) {
            Toast.makeText(this, "Otomatik Wi-Fi özelliği için 'ADS Auto-Clicker' servisine izin verin.", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }
    }
}
