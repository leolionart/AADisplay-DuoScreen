package com.cva.duoscreen.manager

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.roundToInt

data class SpeedState(
    val speedKmh: Int = 0,
    val speedLimit: Int? = null,
    val isOverSpeed: Boolean = false,
    val alertText: String = "",
    val hasGpsFix: Boolean = false
)

class SpeedometerManager(private val context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val listeners = CopyOnWriteArrayList<(SpeedState) -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var currentSpeed: Int = 0
    private var currentLimit: Int? = null
    private var currentAlert: String = ""
    private var hasGpsFix: Boolean = false
    private var lastLocation: Location? = null
    private var lastLocationTime: Long = 0L
    private var isTracking = false

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            hasGpsFix = true
            val now = System.currentTimeMillis()

            val speedMs = if (location.hasSpeed()) {
                location.speed
            } else {
                val prev = lastLocation
                if (prev != null && lastLocationTime > 0) {
                    val dt = (now - lastLocationTime) / 1000f
                    if (dt > 0.3f) prev.distanceTo(location) / dt else 0f
                } else {
                    0f
                }
            }

            lastLocation = location
            lastLocationTime = now

            val kmh = (speedMs * 3.6f).roundToInt().coerceAtLeast(0)
            currentSpeed = kmh
            emitState()
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {
            hasGpsFix = true
        }
        override fun onProviderDisabled(provider: String) {
            hasGpsFix = false
            currentSpeed = 0
            emitState()
        }
    }

    fun addListener(listener: (SpeedState) -> Unit) {
        listeners.add(listener)
        listener(getCurrentState())
    }

    fun removeListener(listener: (SpeedState) -> Unit) {
        listeners.remove(listener)
    }

    fun setSpeedLimit(limit: Int?, alert: String) {
        currentLimit = limit
        currentAlert = alert
        emitState()
    }

    fun getCurrentState(): SpeedState {
        val limit = currentLimit
        val isOver = limit != null && limit > 0 && currentSpeed > limit
        return SpeedState(
            speedKmh = currentSpeed,
            speedLimit = limit,
            isOverSpeed = isOver,
            alertText = currentAlert,
            hasGpsFix = hasGpsFix
        )
    }

    private fun emitState() {
        val state = getCurrentState()
        mainHandler.post {
            for (listener in listeners) {
                listener(state)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (isTracking) return
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) return

        try {
            isTracking = true
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    500L,
                    0f,
                    locationListener,
                    Looper.getMainLooper()
                )
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    1000L,
                    0f,
                    locationListener,
                    Looper.getMainLooper()
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stop() {
        if (!isTracking) return
        try {
            locationManager.removeUpdates(locationListener)
        } catch (e: Exception) {
            // ignore
        }
        isTracking = false
        hasGpsFix = false
        currentSpeed = 0
        emitState()
    }
}
