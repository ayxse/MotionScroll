package com.example.cameraapp

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.net.Uri
import android.util.Log
import android.view.MotionEvent
import kotlin.math.abs
import android.content.IntentFilter
import android.content.BroadcastReceiver
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager // Import if using LocalBroadcastManager

class CameraOverlayService : Service() {

    private val TAG = "CameraOverlayService"
    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View


    private lateinit var cameraToggleButton: Button 

    // Define the BroadcastReceiver
    private val cameraStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ScrollAccessibilityService.ACTION_CAMERA_STATE_CHANGED) {
                val isRunning = intent.getBooleanExtra(ScrollAccessibilityService.EXTRA_CAMERA_STATE, false)
                Log.d(TAG, "Received camera state update via broadcast: isRunning = $isRunning")
                updateButtonBackground(isRunning)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Overlay Service created")

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // Inflate the overlay layout
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView = inflater.inflate(R.layout.overlay_camera_button, null)

        // Add the overlay view to the window manager
        setupOverlay()

        // Find the button and set a click listener
        cameraToggleButton = overlayView.findViewById<Button>(R.id.cameraToggleButton)
        cameraToggleButton.setOnClickListener {
            // Send intent to toggle camera in ScrollAccessibilityService
            val toggleCameraIntent = Intent(this, ScrollAccessibilityService::class.java).apply {
                action = ScrollAccessibilityService.ACTION_TOGGLE_CAMERA
            }
            startService(toggleCameraIntent)
            Log.d(TAG, "Sent ACTION_TOGGLE_CAMERA intent to ScrollAccessibilityService")
        }

        // Set initial button background based on camera state
        updateButtonBackground(ScrollAccessibilityService.isCameraRunning)

        // Add touch listener for moving the overlay
        cameraToggleButton.setOnTouchListener(object : View.OnTouchListener {
            private var initialX: Int = 0
            private var initialY: Int = 0
            private var initialTouchX: Float = 0f
            private var initialTouchY: Float = 0f
            private val CLICK_THRESHOLD = 10 // Threshold to distinguish click from drag

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                val layoutParams = overlayView.layoutParams as WindowManager.LayoutParams
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = layoutParams.x
                        initialY = layoutParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                        layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(overlayView, layoutParams)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val deltaX = event.rawX - initialTouchX
                        val deltaY = event.rawY - initialTouchY
                        // If the movement was less than the threshold, treat it as a click
                        if (abs(deltaX) < CLICK_THRESHOLD && abs(deltaY) < CLICK_THRESHOLD) {
                            v.performClick() // Trigger the click listener
                        }
                        return true
                    }
                }
                return false
            }
        })

        // Register the BroadcastReceiver
        val filter = IntentFilter(ScrollAccessibilityService.ACTION_CAMERA_STATE_CHANGED)
        try {
             ContextCompat.registerReceiver(this, cameraStateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED) // Use RECEIVER_NOT_EXPORTED for security
             Log.d(TAG, "Registered camera state receiver")
        } catch (e: Exception) {
            Log.e(TAG, "Error registering camera state receiver: ${e.message}", e)
        }


    }

    private fun updateButtonBackground(isCameraRunning: Boolean) {
        val drawableId = if (isCameraRunning) {
            R.drawable.circle_button_background_on // Transparent grey when camera is on
        } else {
            R.drawable.circle_button_background_off // Red when camera is off
        }
        cameraToggleButton.background = ContextCompat.getDrawable(this, drawableId)
        Log.d(TAG, "Updated button background, camera is running: $isCameraRunning")
    }

    private fun setupOverlay() {
        // Check if the SYSTEM_ALERT_WINDOW permission is granted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.e(TAG, "SYSTEM_ALERT_WINDOW permission not granted")
            // We should ideally prompt the user to grant this permission
            // This will be handled in the MainActivity when starting the service
            return
        }

        val layoutParamsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutParamsType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 100 // Initial position

        try {
            windowManager.addView(overlayView, params)
            Log.d(TAG, "Overlay view added to window manager")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding overlay view: ${e.message}", e)
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Overlay Service destroyed")
        if (::overlayView.isInitialized) {
            try {
                windowManager.removeView(overlayView)
                Log.d(TAG, "Overlay view removed from window manager")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing overlay view: ${e.message}", e)
            }
        }
        // Unregister the broadcast receiver
        try {
            // LocalBroadcastManager.getInstance(this).unregisterReceiver(cameraStateReceiver)
            // For system-wide broadcast: 
            unregisterReceiver(cameraStateReceiver)
            Log.d(TAG, "Unregistered camera state receiver")
        } catch (e: IllegalArgumentException) {
            // This can happen if the receiver wasn't registered (e.g., due to permission issues in onCreate)
            Log.w(TAG, "Camera state receiver not registered or already unregistered: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering camera state receiver: ${e.message}", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // This is a started service, not a bound service
    }
}
