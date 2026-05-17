package com.example.ads_001

import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlin.math.abs
import kotlin.math.sqrt

class EarthquakeDetectionService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    // Low-pass filter for gravity
    private val gravity = FloatArray(3) { 0f }
    private val alpha = 0.8f

    // STA/LTA buffers
    private val staBuffer = ArrayDeque<Float>() // ~0.75s at SENSOR_DELAY_GAME
    private val ltaBuffer = ArrayDeque<Float>() // ~6s at SENSOR_DELAY_GAME
    private val STA_WINDOW = 15
    private val LTA_WINDOW = 120
    private val LTA_MIN_SAMPLES = 45
    private val STA_LTA_THRESHOLD = 2.5f
    private val ABS_THRESHOLD = 1.0f       // m/s²

    // Time-based trigger thresholds
    private var firstTriggerTime = -1L
    private var lastAboveThresholdTime = -1L
    private val TRIGGER_DURATION_MS = 350L
    private val RESET_GRACE_MS = 800L

    // Cooldown disabled (set to 0 for testing)
    private var lastAlertTime = 0L
    private val COOLDOWN_MS = 0L
    private var lastPublishedIntensityAt = 0L
    private var lastPublishedIntensityScore = -1f
    private var lastPublishedIntensityLevel = SeismicIntensityLevel.CALM
    private var smoothedIntensityScore = 0f
    private var lastSustainedIntensityAt = 0L

    companion object {
        const val CHANNEL_ID = "earthquake_service_channel"
        const val NOTIFICATION_ID = 1001
        const val TAG = "EarthquakeDetection"
        private const val RESTART_DELAY_MS = 1_500L
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        DataTransmissionManager.initializeRelay(this)
        SystemEarthquakeMonitor.ensureRunning(this)
        SeismicIntensityTracker.reset(this)
        LocationCacheManager.startPeriodicRefresh(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildServiceNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            Log.d(TAG, "Accelerometer registered with SENSOR_DELAY_GAME.")
        } ?: Log.w(TAG, "No accelerometer found!")
        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        // Low-pass filter to isolate gravity
        gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0]
        gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1]
        gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2]

        // High-pass filter: linear acceleration (gravity removed)
        val lx = event.values[0] - gravity[0]
        val ly = event.values[1] - gravity[1]
        val lz = event.values[2] - gravity[2]

        val magnitude = sqrt(lx * lx + ly * ly + lz * lz)

        staBuffer.addLast(magnitude)
        if (staBuffer.size > STA_WINDOW) staBuffer.removeFirst()

        ltaBuffer.addLast(magnitude)
        if (ltaBuffer.size > LTA_WINDOW) ltaBuffer.removeFirst()

        if (staBuffer.size < STA_WINDOW || ltaBuffer.size < LTA_MIN_SAMPLES) return

        val sta = staBuffer.average().toFloat()
        val lta = ltaBuffer.average().toFloat()
        if (lta < 0.01f) return

        val ratio = sta / lta
        val now = System.currentTimeMillis()
        publishSeismicIntensityIfNeeded(now, ratio, sta)

        if (ratio > STA_LTA_THRESHOLD && sta > ABS_THRESHOLD) {
            // Above threshold — update last-seen time
            lastAboveThresholdTime = now
            if (firstTriggerTime < 0) {
                firstTriggerTime = now
                Log.d(TAG, "Shaking started — STA/LTA=$ratio STA=$sta")
            }

            val elapsed = now - firstTriggerTime
            Log.d(TAG, "STA/LTA=$ratio STA=$sta elapsed=${elapsed}ms / ${TRIGGER_DURATION_MS}ms")

            if (elapsed >= TRIGGER_DURATION_MS) {
                val cooldownPassed = now - lastAlertTime > COOLDOWN_MS
                if (cooldownPassed) {
                    lastAlertTime = now
                    resetTriggerState(clearBaseline = true)
                    Log.w(TAG, "EARTHQUAKE TRIGGERED after ${elapsed}ms of shaking!")
                    EarthquakeAlertManager.onLocalEarthquakeDetected(this, "Sismik Hareket Algılandı (Sensör)")
                }
            }
        } else {
            // Below threshold — reset only if grace period expired
            if (lastAboveThresholdTime > 0 && now - lastAboveThresholdTime > RESET_GRACE_MS) {
                if (firstTriggerTime > 0) Log.d(TAG, "Shaking stopped — resetting timer")
                resetTriggerState(clearBaseline = false)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "App swiped away — scheduling service restart")
        scheduleRestart()
    }

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        SystemEarthquakeMonitor.stop()
        SeismicIntensityTracker.reset(this)
        LocationCacheManager.stopPeriodicRefresh()
        Log.d(TAG, "Service destroyed — scheduling restart")
        scheduleRestart()
        super.onDestroy()
    }

    /**
     * Schedules a restart via AlarmManager so the service survives
     * being killed by the OS or by the user swiping the app away.
     */
    private fun scheduleRestart() {
        try {
            val restartIntent = Intent(this, EarthquakeDetectionService::class.java)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            else
                PendingIntent.FLAG_ONE_SHOT
            val pendingIntent = PendingIntent.getService(this, 1, restartIntent, flags)

            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val triggerAt = System.currentTimeMillis() + RESTART_DELAY_MS

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent
                )
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
            Log.d(TAG, "Service restart scheduled in ${RESTART_DELAY_MS}ms")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule restart: ${e.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Deprem İzleme Servisi",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "ADS deprem algılama arka plan servisi"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildServiceNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, 0, intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_IMMUTABLE else 0)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ADS hazır")
            .setContentText("Sarsıntılar izleniyor")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun resetTriggerState(clearBaseline: Boolean) {
        firstTriggerTime = -1L
        lastAboveThresholdTime = -1L
        if (clearBaseline) {
            staBuffer.clear()
            ltaBuffer.clear()
        }
    }

    private fun publishSeismicIntensityIfNeeded(now: Long, ratio: Float, sta: Float) {
        val rawScore = computeSeismicIntensityScore(ratio, sta)
        val score = smoothSeismicIntensity(now, rawScore)
        val level = when {
            score < 1.5f -> SeismicIntensityLevel.CALM
            score < 3.5f -> SeismicIntensityLevel.LIGHT
            score < 5.5f -> SeismicIntensityLevel.MODERATE
            score < 7.5f -> SeismicIntensityLevel.STRONG
            else -> SeismicIntensityLevel.SEVERE
        }

        val shouldPublish =
            now - lastPublishedIntensityAt >= 250L ||
                abs(score - lastPublishedIntensityScore) >= 0.25f ||
                level != lastPublishedIntensityLevel

        if (!shouldPublish) {
            return
        }

        SeismicIntensityTracker.update(this, score, level)
        lastPublishedIntensityAt = now
        lastPublishedIntensityScore = score
        lastPublishedIntensityLevel = level
    }

    private fun computeSeismicIntensityScore(ratio: Float, sta: Float): Float {
        // Compute UI intensity combining STA/LTA and absolute magnitude
        val absoluteContribution = (sta / 2.4f).coerceIn(0f, 1f) * 7.5f
        val ratioContribution = (((ratio - 1f) / 2.5f).coerceIn(0f, 1f)) * 2.5f
        return (ratioContribution + absoluteContribution).coerceIn(0f, 10f)
    }

    private fun smoothSeismicIntensity(now: Long, rawScore: Float): Float {
        if (rawScore >= smoothedIntensityScore) {
            smoothedIntensityScore = rawScore
            lastSustainedIntensityAt = now
            return smoothedIntensityScore
        }

        val stillShaking = rawScore >= smoothedIntensityScore * 0.72f
        if (stillShaking) {
            lastSustainedIntensityAt = now
            smoothedIntensityScore = (smoothedIntensityScore * 0.82f) + (rawScore * 0.18f)
            return smoothedIntensityScore.coerceIn(0f, 10f)
        }

        val holdActive = now - lastSustainedIntensityAt <= 900L
        val decayStep = if (holdActive) 0.06f else 0.22f
        smoothedIntensityScore = maxOf(rawScore, smoothedIntensityScore - decayStep)
        return smoothedIntensityScore.coerceIn(0f, 10f)
    }
}
