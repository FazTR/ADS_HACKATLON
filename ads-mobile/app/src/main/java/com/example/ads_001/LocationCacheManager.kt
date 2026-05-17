package com.example.ads_001

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

object LocationCacheManager {
    private const val TAG = "LocationCache"
    private const val REFRESH_INTERVAL_MS = 20L * 60L * 1000L
    private const val LOCATION_TIMEOUT_MS = 20_000L
    private const val LOCATION_ENABLE_TIMEOUT_MS = 12_000L
    private const val LOCATION_DISABLE_TIMEOUT_MS = 8_000L
    private const val LOCATION_STATE_POLL_MS = 400L
    private const val MAX_ACCEPTED_ACCURACY_METERS = 100f
    private const val ZERO_COORDINATE = 0.0
    private const val MIN_REFRESH_GAP_MS = 2L * 60L * 1000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val refreshInFlight = AtomicBoolean(false)

    @Volatile
    private var periodicRefreshStarted = false

    private val periodicRefreshRunnable = object : Runnable {
        override fun run() {
            scope.launch {
                refreshCachedLocationInternal(appContext = currentContext ?: return@launch, force = false)
            }
            mainHandler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    @Volatile
    private var currentContext: Context? = null

    fun startPeriodicRefresh(context: Context) {
        currentContext = context.applicationContext
        if (periodicRefreshStarted) return

        synchronized(this) {
            if (periodicRefreshStarted) return
            periodicRefreshStarted = true
            scope.launch {
                refreshCachedLocationInternal(currentContext ?: return@launch, force = false)
            }
            mainHandler.postDelayed(periodicRefreshRunnable, REFRESH_INTERVAL_MS)
        }
    }

    fun stopPeriodicRefresh() {
        periodicRefreshStarted = false
        mainHandler.removeCallbacks(periodicRefreshRunnable)
    }

    fun refreshNow(context: Context) {
        currentContext = context.applicationContext
        scope.launch {
            refreshCachedLocationInternal(currentContext ?: return@launch, force = true)
        }
    }

    fun getCachedLocation(context: Context): CachedLocation? {
        val cachedLocation = SharedPrefsHelper(context.applicationContext).getCachedLocation() ?: return null
        return if (isValidCoordinates(cachedLocation.latitude, cachedLocation.longitude)) {
            cachedLocation
        } else {
            null
        }
    }

    private suspend fun refreshCachedLocationInternal(appContext: Context, force: Boolean) {
        if (!refreshInFlight.compareAndSet(false, true)) return

        val prefs = SharedPrefsHelper(appContext)
        val currentLocation = prefs.getCachedLocation()
        val now = System.currentTimeMillis()
        if (!force && currentLocation != null && now - currentLocation.timestampMs < MIN_REFRESH_GAP_MS) {
            refreshInFlight.set(false)
            return
        }

        if (ActivityCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Skipping cache refresh: fine location permission not granted")
            refreshInFlight.set(false)
            return
        }

        val locationWasEnabled = isLocationEnabled(appContext)
        var enabledTemporarily = false

        try {
            if (!locationWasEnabled) {
                val enabled = requestLocationState(appContext, desiredEnabled = true)
                if (!enabled) {
                    Log.w(TAG, "GPS cache refresh aborted: could not enable location")
                    return
                }
                enabledTemporarily = true
            }

            val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val location = withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
                requestCurrentGpsLocation(locationManager)
            } ?: getLastKnownGpsLocation(locationManager)

            if (location != null && isUsableLocation(location)) {
                prefs.saveCachedLocation(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    timestampMs = System.currentTimeMillis(),
                    accuracyMeters = if (location.hasAccuracy()) location.accuracy else Float.MAX_VALUE
                )
                Log.d(
                    TAG,
                    "Cached GPS location updated lat=${location.latitude}, lon=${location.longitude}, accuracy=${location.accuracy}"
                )
                DataTransmissionManager.onCachedLocationUpdated(appContext)
            } else {
                Log.w(TAG, "GPS cache refresh failed: no usable GPS location")
            }
        } catch (e: Exception) {
            Log.e(TAG, "GPS cache refresh failed: ${e.message}")
        } finally {
            if (enabledTemporarily) {
                val disabled = requestLocationState(appContext, desiredEnabled = false)
                if (!disabled) {
                    Log.w(TAG, "Temporary GPS session ended but location could not be turned off")
                }
            }
            refreshInFlight.set(false)
        }
    }

    private fun isLocationEnabled(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            val mode = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.LOCATION_MODE,
                Settings.Secure.LOCATION_MODE_OFF
            )
            mode != Settings.Secure.LOCATION_MODE_OFF
        }
    }

    private suspend fun requestLocationState(context: Context, desiredEnabled: Boolean): Boolean {
        if (isLocationEnabled(context) == desiredEnabled) {
            return true
        }

        AdsAccessibilityService.desiredLocationEnabled = desiredEnabled
        val settingsIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            mainHandler.post {
                try {
                    context.startActivity(settingsIntent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to open location settings: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request location state change: ${e.message}")
            return false
        }

        val timeoutMs = if (desiredEnabled) LOCATION_ENABLE_TIMEOUT_MS else LOCATION_DISABLE_TIMEOUT_MS
        val stateChanged = waitForLocationState(context, desiredEnabled, timeoutMs)
        if (!stateChanged) {
            Log.w(TAG, "Timed out waiting for location state change to $desiredEnabled")
            AdsAccessibilityService.desiredLocationEnabled = null
        }
        return stateChanged
    }

    private suspend fun waitForLocationState(context: Context, desiredEnabled: Boolean, timeoutMs: Long): Boolean {
        val startedAt = System.currentTimeMillis()
        while (System.currentTimeMillis() - startedAt < timeoutMs) {
            if (isLocationEnabled(context) == desiredEnabled) {
                return true
            }
            delay(LOCATION_STATE_POLL_MS)
        }
        return isLocationEnabled(context) == desiredEnabled
    }

    private suspend fun requestCurrentGpsLocation(locationManager: LocationManager): Location? =
        suspendCancellableCoroutine { continuation ->
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                Log.w(TAG, "GPS provider is disabled")
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }

            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (continuation.isActive && isUsableLocation(location)) {
                        continuation.resume(location)
                        safeRemoveUpdates(locationManager, this)
                    }
                }
            }

            try {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    0L,
                    0f,
                    listener,
                    Looper.getMainLooper()
                )
                continuation.invokeOnCancellation {
                    safeRemoveUpdates(locationManager, listener)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Current GPS cache request failed: ${e.message}")
                safeRemoveUpdates(locationManager, listener)
                if (continuation.isActive) {
                    continuation.resume(null)
                }
            }
        }

    private fun getLastKnownGpsLocation(locationManager: LocationManager): Location? {
        return try {
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?.takeIf { isUsableLocation(it) }
        } catch (e: SecurityException) {
            Log.e(TAG, "No permission for GPS provider: ${e.message}")
            null
        }
    }

    private fun safeRemoveUpdates(locationManager: LocationManager, listener: LocationListener) {
        try {
            locationManager.removeUpdates(listener)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to remove location listener: ${e.message}")
        }
    }

    private fun isUsableLocation(location: Location): Boolean {
        val accuracy = if (location.hasAccuracy()) location.accuracy else Float.MAX_VALUE
        val isGps = location.provider == LocationManager.GPS_PROVIDER
        return isGps &&
            isValidCoordinates(location.latitude, location.longitude) &&
            accuracy <= MAX_ACCEPTED_ACCURACY_METERS
    }

    private fun isValidCoordinates(latitude: Double, longitude: Double): Boolean {
        if (latitude == ZERO_COORDINATE && longitude == ZERO_COORDINATE) return false
        return latitude in -90.0..90.0 && longitude in -180.0..180.0
    }
}
