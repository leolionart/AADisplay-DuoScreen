package com.cva.duoscreen.car

import android.graphics.Rect
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate

class DuoMainScreen(carContext: CarContext) : Screen(carContext), SurfaceCallback {

    private var displayManager: DuoVirtualDisplayManager? = null

    init {
        val appManager = carContext.getCarService(androidx.car.app.AppManager::class.java)
        appManager.setSurfaceCallback(this)
    }

    override fun onGetTemplate(): Template {
        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle("Top: Vietmap")
                    .setOnClickListener {
                        displayManager?.launchTopApp("vn.vietmap.live")
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("Bottom: YouTube")
                    .setOnClickListener {
                        displayManager?.launchBottomLeftApp("com.google.android.apps.youtube.music")
                    }
                    .build()
            )
            .build()

        return NavigationTemplate.Builder()
            .setActionStrip(actionStrip)
            .build()
    }

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {}

    override fun onVisibleAreaChanged(visibleArea: Rect) {}

    override fun onStableAreaChanged(stableArea: Rect) {}

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        displayManager?.release()
        displayManager = null
    }
}
