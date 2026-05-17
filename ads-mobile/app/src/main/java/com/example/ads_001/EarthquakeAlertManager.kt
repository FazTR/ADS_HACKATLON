package com.example.ads_001

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.ads_001.network.models.SystemEarthquakeEvent

/**
 * Central manager for earthquake alert handling.
 * Called by both EarthquakeDetectionService (accelerometer)
 * and EarthquakeBroadcastReceiver (Google system).
 */
object EarthquakeAlertManager {

    private const val ALERT_CHANNEL_ID = "earthquake_alert_channel"
    private const val ALERT_NOTIFICATION_ID = 2001
    private const val TAG = "EarthquakeAlert"

    fun onLocalEarthquakeDetected(context: Context, source: String) {
        Log.w(TAG, "LOCAL EARTHQUAKE DETECTED via: $source")

        TransmissionStatusTracker.update(
            context,
            "Sarsıntı fark edildi, bilgin gönderiliyor",
            TransmissionStatusLevel.INFO
        )

        DataTransmissionManager.sendEarthquakeReport(context)
    }

    fun onSystemEarthquakeReceived(context: Context, event: SystemEarthquakeEvent) {
        val source = buildSystemAlertSource(event)
        val localIntensity = SeismicIntensityTracker.getCurrentStatus(context)
        Log.w(TAG, "SYSTEM EARTHQUAKE RECEIVED via API: $source")

        playDoubleBeepAndVibrate(context)
        showAlertNotification(
            context = context,
            title = "Deprem uyarısı",
            body = "Yeni uyarı: $source"
        )

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try {
                val alertIntent = Intent(context, EarthquakeAlertActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                    putExtra(EarthquakeAlertActivity.EXTRA_SOURCE, source)
                    putExtra(EarthquakeAlertActivity.EXTRA_LOCAL_INTENSITY_SCORE, localIntensity.score)
                    putExtra(EarthquakeAlertActivity.EXTRA_LOCAL_INTENSITY_LABEL, localIntensity.label)
                    putExtra(EarthquakeAlertActivity.EXTRA_LOCAL_INTENSITY_LEVEL, localIntensity.level.name)
                }
                context.startActivity(alertIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Could not launch alert activity: ${e.message}")
            }
        }, 2000L)
    }

    private fun playDoubleBeepAndVibrate(context: Context) {
        // Vibration: two short pulses [0ms delay, 400ms on, 200ms off, 400ms on]
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400), -1)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(longArrayOf(0, 400, 200, 400), -1)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibration failed: ${e.message}")
        }

        // Double beep at max alarm volume using ToneGenerator
        try {
            val tg = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            tg.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 300)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    tg.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 300)
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        tg.release()
                    }, 500)
                } catch (e: Exception) {
                    tg.release()
                }
            }, 500)
        } catch (e: Exception) {
            Log.w(TAG, "Beep failed: ${e.message}")
        }
    }

    private fun showAlertNotification(context: Context, title: String, body: String) {
        createAlertChannel(context)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, ALERT_NOTIFICATION_ID, openAppIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            else PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setFullScreenIntent(pendingIntent, true)
            .setVibrate(longArrayOf(0, 400, 200, 400))
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(ALERT_NOTIFICATION_ID, notification)
    }

    private fun createAlertChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Deprem Uyarıları",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Yeni deprem uyarıları"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(
                    android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildSystemAlertSource(event: SystemEarthquakeEvent): String {
        val magnitudeText = event.magnitude?.let { "M%.1f".format(it) } ?: "Deprem"
        val locationText = event.locationName?.takeIf { it.isNotBlank() } ?: "Konum bilinmiyor"
        val depthText = event.depthKm?.let { "Derinlik %.1f km".format(it) }
        return listOf(magnitudeText, locationText, depthText)
            .filterNotNull()
            .joinToString(" • ")
    }
}
