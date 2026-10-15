package com.cva.duoscreen.source

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArrayList

object SpeedAlertRepository {
    private val listeners = CopyOnWriteArrayList<(SpeedAlert) -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lifecycleLock = Any()
    private var activeClients = 0

    @Volatile
    var current: SpeedAlert = SpeedAlert()
        private set

    fun start(context: Context) {
        synchronized(lifecycleLock) {
            activeClients++
            if (activeClients == 1) GpsSpeedSource.start(context)
        }
    }

    fun stop() {
        synchronized(lifecycleLock) {
            if (activeClients == 0) return
            activeClients--
            if (activeClients == 0) GpsSpeedSource.stop()
        }
    }

    fun addListener(l: (SpeedAlert) -> Unit) {
        if (listeners.addIfAbsent(l)) {
            mainHandler.post {
                if (listeners.contains(l)) l(current)
            }
        }
    }

    fun removeListener(l: (SpeedAlert) -> Unit) {
        listeners.remove(l)
    }

    fun updateGpsData(speedKmh: Int, hasGps: Boolean) {
        updateState { it.copy(speedKmh = speedKmh, hasGps = hasGps) }
    }

    fun updateVietmapData(speedLimit: Int?, alertText: String) {
        updateState { it.copy(speedLimit = speedLimit, alertText = alertText) }
    }
    fun reset() {
        updateState { SpeedAlert() }
    }

    @Synchronized
    private fun updateState(modifier: (SpeedAlert) -> SpeedAlert) {
        val oldState = current
        val newState = modifier(oldState)

        val isOver = if (newState.speedLimit != null && newState.speedLimit > 0) {
            newState.speedKmh > newState.speedLimit
        } else {
            false
        }

        val finalState = newState.copy(
            isOverLimit = isOver,
            updatedAt = System.currentTimeMillis()
        )

        if (oldState.speedKmh == finalState.speedKmh &&
            oldState.speedLimit == finalState.speedLimit &&
            oldState.alertText == finalState.alertText &&
            oldState.hasGps == finalState.hasGps &&
            oldState.isOverLimit == finalState.isOverLimit) {
            return // dedupe unchanged values
        }

        current = finalState
        mainHandler.post {
            for (l in listeners) {
                l(finalState)
            }
        }
    }
}