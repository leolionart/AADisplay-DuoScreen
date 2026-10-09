package com.cva.duoscreen

import android.graphics.SurfaceTexture
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.TextureView
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.cva.duoscreen.car.DuoVirtualDisplayManager
import com.cva.duoscreen.shizuku.ShizukuHelper
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private lateinit var displayManager: DuoVirtualDisplayManager

    private var topTexture: SurfaceTexture? = null
    private var bottomLeftTexture: SurfaceTexture? = null
    private var bottomRightTexture: SurfaceTexture? = null

    private lateinit var tvStatus: TextView
    private lateinit var tvTopDisplay: TextureView
    private lateinit var tvBottomLeftDisplay: TextureView
    private lateinit var tvBottomRightDisplay: TextureView

    private lateinit var btnFloatingTrigger: View
    private lateinit var panelControlOverlay: View

    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable {
        panelControlOverlay.visibility = View.GONE
        btnFloatingTrigger.alpha = 0.2f
    }

    private var hasAutoCreated = false

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        updateStatus()
        tryAutoStartDisplays()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        updateStatus()
    }

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { _, _ ->
        updateStatus()
        tryAutoStartDisplays()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Make truly fullscreen immersive (hide status bar and nav bar)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }

        displayManager = DuoVirtualDisplayManager(this)

        tvStatus = findViewById(R.id.tvStatus)
        tvTopDisplay = findViewById(R.id.tvTopDisplay)
        tvBottomLeftDisplay = findViewById(R.id.tvBottomLeftDisplay)
        tvBottomRightDisplay = findViewById(R.id.tvBottomRightDisplay)

        btnFloatingTrigger = findViewById(R.id.btnFloatingTrigger)
        panelControlOverlay = findViewById(R.id.panelControlOverlay)

        val btnClose = findViewById<Button>(R.id.btnCloseMenu)
        val btnTop = findViewById<Button>(R.id.btnLaunchTop)
        val btnBottomLeft = findViewById<Button>(R.id.btnLaunchBottomLeft)
        val btnBottomRight = findViewById<Button>(R.id.btnLaunchBottomRight)

        // 1. Texture Listeners with auto-init once all 3 surfaces are available
        tvTopDisplay.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                topTexture = surface
                tryAutoStartDisplays()
            }
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }

        tvBottomLeftDisplay.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                bottomLeftTexture = surface
                tryAutoStartDisplays()
            }
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }

        tvBottomRightDisplay.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                bottomRightTexture = surface
                tryAutoStartDisplays()
            }
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }

        // 2. Touch Forwarding
        tvTopDisplay.setOnTouchListener { _, event ->
            displayManager.handleTouch(
                displayManager.topDisplayId,
                event,
                tvTopDisplay.width,
                tvTopDisplay.height,
                tvTopDisplay.width,
                tvTopDisplay.height
            )
            true
        }

        tvBottomLeftDisplay.setOnTouchListener { _, event ->
            displayManager.handleTouch(
                displayManager.bottomLeftDisplayId,
                event,
                tvBottomLeftDisplay.width,
                tvBottomLeftDisplay.height,
                tvBottomLeftDisplay.width,
                tvBottomLeftDisplay.height
            )
            true
        }

        tvBottomRightDisplay.setOnTouchListener { _, event ->
            displayManager.handleTouch(
                displayManager.bottomRightDisplayId,
                event,
                tvBottomRightDisplay.width,
                tvBottomRightDisplay.height,
                tvBottomRightDisplay.width,
                tvBottomRightDisplay.height
            )
            true
        }

        // 3. UI/UX: Floating Button Toggle
        btnFloatingTrigger.alpha = 0.3f
        btnFloatingTrigger.setOnClickListener {
            toggleMenu()
        }

        btnClose.setOnClickListener {
            hideMenu()
        }

        btnTop.setOnClickListener {
            displayManager.launchTopApp("vn.vietmap.live")
            resetAutoHide()
        }

        btnBottomLeft.setOnClickListener {
            displayManager.launchBottomLeftApp("com.google.android.apps.youtube.music")
            resetAutoHide()
        }

        btnBottomRight.setOnClickListener {
            displayManager.launchBottomRightApp("com.android.chrome")
            resetAutoHide()
        }

        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)
        updateStatus()
    }

    private fun tryAutoStartDisplays() {
        if (hasAutoCreated) return
        val tTex = topTexture ?: return
        val blTex = bottomLeftTexture ?: return
        val brTex = bottomRightTexture ?: return

        tvTopDisplay.post {
            val tw = tvTopDisplay.width
            val th = tvTopDisplay.height
            val blw = tvBottomLeftDisplay.width
            val blh = tvBottomLeftDisplay.height
            val brw = tvBottomRightDisplay.width
            val brh = tvBottomRightDisplay.height

            if (tw > 0 && th > 0 && blw > 0 && blh > 0 && brw > 0 && brh > 0) {
                hasAutoCreated = true
                displayManager.createThreeDisplays(
                    tTex, tw, th,
                    blTex, blw, blh,
                    brTex, brw, brh,
                    240
                )
                // Auto launch default apps once
                displayManager.launchTopApp("vn.vietmap.live")
                displayManager.launchBottomLeftApp("com.google.android.apps.youtube.music")
                displayManager.launchBottomRightApp("com.android.chrome")
            }
        }
    }

    private fun toggleMenu() {
        if (panelControlOverlay.visibility == View.VISIBLE) {
            hideMenu()
        } else {
            showMenu()
        }
    }

    private fun showMenu() {
        panelControlOverlay.visibility = View.VISIBLE
        btnFloatingTrigger.alpha = 1.0f
        resetAutoHide()
    }

    private fun hideMenu() {
        panelControlOverlay.visibility = View.GONE
        btnFloatingTrigger.alpha = 0.25f
        hideHandler.removeCallbacks(hideRunnable)
    }

    private fun resetAutoHide() {
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, 5000) // Tự động ẩn sau 5 giây
    }

    private fun updateStatus() {
        runOnUiThread {
            val isAvail = ShizukuHelper.isShizukuAvailable()
            val hasPerm = ShizukuHelper.hasPermission()
            tvStatus.text = if (isAvail && hasPerm) {
                "Shizuku: Sẵn sàng & Đã kết nối"
            } else {
                "Shizuku: Đang chờ kết nối"
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hideHandler.removeCallbacks(hideRunnable)
        displayManager.release()
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
    }
}
