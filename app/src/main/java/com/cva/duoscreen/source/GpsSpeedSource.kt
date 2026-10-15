package com.cva.duoscreen.source

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

object GpsSpeedSource {
    private var locationManager: LocationManager? = null
    private var isTracking = false

    private var lastLocation: Location? = null
    private var lastLocationTime: Long = 0L

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
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
            SpeedAlertRepository.updateGpsData(speedKmh = kmh, hasGps = true)
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {
            SpeedAlertRepository.updateGpsData(speedKmh = SpeedAlertRepository.current.speedKmh, hasGps = true)
        }
        override fun onProviderDisabled(provider: String) {
            SpeedAlertRepository.updateGpsData(speedKmh = 0, hasGps = false)
        }
    }

    @SuppressLint("MissingPermission")
    fun start(context: Context) {
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
            locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            isTracking = true
            
            if (locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true) {
                locationManager?.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    500L,
                    0f,
                    locationListener,
                    Looper.getMainLooper()
                )
            }
            if (locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true) {
                locationManager?.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    1000L,
                    0f,
                    locationListener,
                    Looper.getMainLooper()
                )
            }
        } catch (e: Exception) {
            locationManager = null
            isTracking = false
            lastLocation = null
            lastLocationTime = 0L
            e.printStackTrace()
        }
    }

    fun stop() {
        if (!isTracking) return
        try {
            locationManager?.removeUpdates(locationListener)
        } catch (_: Exception) {
            // ignore
        }
        locationManager = null
        isTracking = false
        lastLocation = null
        lastLocationTime = 0L
        SpeedAlertRepository.updateGpsData(speedKmh = 0, hasGps = false)
    }
}