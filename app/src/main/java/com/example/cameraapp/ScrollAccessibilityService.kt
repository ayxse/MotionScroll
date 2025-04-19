package com.example.cameraapp

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import android.Manifest
import android.content.pm.PackageManager


class ScrollAccessibilityService : AccessibilityService(), LifecycleOwner {
    private val TAG = "ScrollAccessibilityService"
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) } // Handler for Toasts
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var lifecycleRegistry: LifecycleRegistry
    private var lastScrollTime = 0L
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var isServiceDestroyed = false
    private var cameraToggleReceiver: BroadcastReceiver? = null

    companion object {
        const val DEFAULT_SENSITIVITY = 10f // Keep sensitivity for triggering
        var currentSensitivity = DEFAULT_SENSITIVITY
        const val DEFAULT_SCROLL_SPEED = 100f  // Speed for continuous mode (when delay=0)
        var currentScrollSpeed = DEFAULT_SCROLL_SPEED // Not used currently, but keep for potential future use
        const val DEFAULT_SKIP_MULTIPLIER = 0.09f // Default skip distance (Set to 9% screen height)
        var currentSkipDistanceMultiplier = DEFAULT_SKIP_MULTIPLIER // Variable for skip distance
        const val ACTION_TOGGLE_SERVICE = "com.example.cameraapp.ACTION_TOGGLE_SERVICE"
        const val ACTION_TOGGLE_SMOOTH_SCROLL = "com.example.cameraapp.ACTION_TOGGLE_SMOOTH_SCROLL"
        // const val ACTION_TOGGLE_SKIPPING = "com.example.cameraapp.ACTION_TOGGLE_SKIPPING" // Removed
        // const val ACTION_UPDATE_SKIP_LEVEL = "com.example.cameraapp.ACTION_UPDATE_SKIP_LEVEL" // Removed (unused)
        // const val EXTRA_SKIP_LEVEL = "com.example.cameraapp.EXTRA_SKIP_LEVEL" // Removed (unused)
        // const val ACTION_TOGGLE_ADDED_DELAY = "com.example.cameraapp.ACTION_TOGGLE_ADDED_DELAY" // Removed
        const val ACTION_UPDATE_DELAY = "com.example.cameraapp.ACTION_UPDATE_DELAY" // Keep this
        const val EXTRA_DELAY_SECONDS = "com.example.cameraapp.EXTRA_DELAY_SECONDS" // Keep this
        // const val ACTION_UPDATE_SENSITIVITY = "com.example.cameraapp.ACTION_UPDATE_SENSITIVITY" // Removed
        // const val EXTRA_SENSITIVITY = "com.example.cameraapp.EXTRA_SENSITIVITY" // Removed
        // const val ACTION_UPDATE_SCROLL_SPEED = "com.example.cameraapp.ACTION_UPDATE_SCROLL_SPEED" // Keep removed
        // const val EXTRA_SCROLL_SPEED = "com.example.cameraapp.EXTRA_SCROLL_SPEED" // Keep removed
        const val ACTION_UPDATE_SKIP_DISTANCE = "com.example.cameraapp.ACTION_UPDATE_SKIP_DISTANCE" // Action for skip distance slider
        const val EXTRA_SKIP_DISTANCE = "com.example.cameraapp.EXTRA_SKIP_DISTANCE" // Extra for skip distance slider
        const val ACTION_TOGGLE_CAMERA = "com.example.cameraapp.ACTION_TOGGLE_CAMERA"
        // var isSkippingEnabled = true // Keep removed
        // var skipLevel = 1 // Removed (unused)
        // var isAddedDelayEnabled = false // Removed - behavior now tied to delay value
        var addedDelaySeconds = 1 // Default to 1 second (Skip Mode)
        var isCameraRunning = false
        const val ACTION_CAMERA_STATE_CHANGED = "com.example.cameraapp.ACTION_CAMERA_STATE_CHANGED"
        const val EXTRA_CAMERA_STATE = "camera_state"
    }

    private var isServiceEnabled = false
    // private var isSkippingMode = false // No longer needed directly
    // private var skipLevel = 1 // No longer needed directly
    // private var lastDetectionTime = 0L // No longer needed directly

    override fun onCreate() {
        super.onCreate()
        try {
            lifecycleRegistry = LifecycleRegistry(this)
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
            cameraExecutor = Executors.newSingleThreadExecutor()
            Log.d(TAG, "Service created successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate: ${e.message}", e)
        }
    }

    override fun onServiceConnected() {
        try {
            super.onServiceConnected()
            Log.d(TAG, "Service connected")

            lifecycleRegistry.currentState = Lifecycle.State.STARTED

            val info = AccessibilityServiceInfo()
            info.flags = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS or
                        AccessibilityServiceInfo.DEFAULT
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC

            try {
                serviceInfo = info // Apply flags and feedback type
                isServiceEnabled = true
                Log.d(TAG, "Service info set successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error setting service info: ${e.message}", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onServiceConnected: ${e.message}", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand received with action: ${intent?.action}")

        when (intent?.action) {
            ACTION_TOGGLE_SERVICE -> {
                isServiceEnabled = !isServiceEnabled
                if (isServiceEnabled) {
                    startCamera()
                } else {
                    stopCamera()
                }
                Log.d(TAG, "Service ${if (isServiceEnabled) "enabled" else "disabled"}")
            }
            ACTION_UPDATE_DELAY -> { // Keep this
                // Ensure delay is not negative, default to 1 (Skip Mode) if invalid
                addedDelaySeconds = intent.getIntExtra(EXTRA_DELAY_SECONDS, 1).coerceAtLeast(0)
                Log.d(TAG, "Scroll mode/delay updated to $addedDelaySeconds (0=Cont, 1=Skip, >1=DelaySecs)")
            }
            ACTION_UPDATE_SKIP_DISTANCE -> { // Handle Skip Distance Update
                 currentSkipDistanceMultiplier = intent.getFloatExtra(EXTRA_SKIP_DISTANCE, DEFAULT_SKIP_MULTIPLIER)
                 Log.d(TAG, "Skip distance multiplier updated to $currentSkipDistanceMultiplier")
            }
            ACTION_TOGGLE_CAMERA -> {
                Log.d(TAG, "Received camera toggle command")
                if (!checkCameraPermission()) {
                    Log.e(TAG, "Camera permission not granted")
                    return START_STICKY
                }

                if (isCameraRunning) {
                    Log.d(TAG, "Stopping camera")
                    stopCamera()
                } else {
                    Log.d(TAG, "Starting camera")
                    startCamera()
                }
            }
        }
        return START_STICKY
    }

    private fun startCamera() {
        if (!isServiceEnabled) {
            Log.e(TAG, "Service not enabled, cannot start camera")
            return
        }

        try {
            if (isCameraRunning) {
                Log.d(TAG, "Camera is already running")
                return
            }

            lifecycleRegistry.currentState = Lifecycle.State.RESUMED

            val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
            cameraProviderFuture.addListener({
                try {
                    cameraProvider = cameraProviderFuture.get()
                    cameraProvider?.unbindAll() // Ensure no lingering bindings

                    if (!isServiceEnabled || isServiceDestroyed) { // Check if service stopped during setup
                        Log.e(TAG, "Service disabled or destroyed while starting camera")
                        return@addListener
                    }

                    imageAnalyzer = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(cameraExecutor, FaceAnalyzer { gazeDirection ->
                                handleGazeDirection(gazeDirection)
                            })
                        }

                    val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                    // Check if front camera is available before binding
                     try {
                        if (!cameraProvider!!.hasCamera(cameraSelector)) {
                             Log.e(TAG, "No front camera available.")
                             stopCamera() // Clean up
                             return@addListener
                        }
                        cameraProvider?.bindToLifecycle(
                            this,
                            cameraSelector,
                            imageAnalyzer
                        )
                     } catch (e: Exception) {
                         Log.e(TAG, "Error checking for front camera or binding: ${e.message}", e)
                         stopCamera() // Clean up
                         return@addListener
                     }


                    isCameraRunning = true
                    Log.d(TAG, "Camera started successfully")
                    sendCameraStateUpdate(true)
                } catch (exc: Exception) {
                    Log.e(TAG, "Failed to start camera within listener: ${exc.message}", exc)
                    stopCamera() // Clean up if binding fails
                }
            }, ContextCompat.getMainExecutor(this))
        } catch (e: Exception) {
            Log.e(TAG, "Error starting camera setup: ${e.message}", e)
            stopCamera() // Clean up
        }
    }

    // Helper function to show Toasts on the main thread (Keep for skip multiplier feedback)
    private fun showToast(message: String) {
        mainHandler.post {
            Toast.makeText(this@ScrollAccessibilityService, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopCamera() {
        Log.d(TAG, "Stopping camera...")
        try {
            cameraProvider?.unbindAll() // Unbind before setting state
            lifecycleRegistry.currentState = Lifecycle.State.CREATED // Move down lifecycle
            imageAnalyzer = null // Release analyzer
            isCameraRunning = false

            Log.d(TAG, "Camera stopped successfully")
            sendCameraStateUpdate(false)
        } catch (exc: Exception) {
            Log.e(TAG, "Failed to stop camera: ${exc.message}")
        }
    }

    private fun handleGazeDirection(gazeDirection: GazeDirection) {
        if (!isServiceEnabled || !isCameraRunning) {
            return // Don't process if service/camera off
        }

        val currentTime = System.currentTimeMillis()

        // Determine scroll interval based on addedDelaySeconds (0 = continuous, 1 = default skip, >1 = custom delay)
        val dynamicScrollInterval = when (addedDelaySeconds) {
            0 -> 100L // Faster interval (0.1s) for "continuous" mode
            1 -> 1000L // Default 1.0 second delay for skip mode
            else -> (addedDelaySeconds * 1000L).coerceAtLeast(100L) // Custom delay (min 100ms)
        }

        if (currentTime - lastScrollTime < dynamicScrollInterval) {
            return // Throttle scrolls
        }

        when (gazeDirection) {
            GazeDirection.UP, GazeDirection.DOWN -> {
                Log.d(TAG, "Processing gaze direction: $gazeDirection")
                val scrollDirection = when (gazeDirection) {
                    GazeDirection.UP -> -1f
                    GazeDirection.DOWN -> 1f
                    else -> 0f // Should not happen here
                }

                if (scrollDirection != 0f) {
                    performScroll(scrollDirection)
                    lastScrollTime = currentTime
                }
            }
            GazeDirection.CENTER -> {
                // No action needed for center gaze
            }
        }
    }

    private fun performScroll(direction: Float) {
        if (!isServiceEnabled || !isCameraRunning) {
            Log.d(TAG, "Scroll ignored - service enabled: $isServiceEnabled, camera running: $isCameraRunning")
            return
        }

        try {
            Log.d(TAG, "Performing scroll: $direction")
            val screenHeight = resources.displayMetrics.heightPixels
            if (screenHeight <= 0) {
                 Log.e(TAG, "Invalid screen height: $screenHeight")
                 return
            }

            val scrollPath = Path()
            val startX = resources.displayMetrics.widthPixels / 2f
            val startY = screenHeight * 0.5f // Start near center

            val endY: Float // Correct: Declare only once
            val scrollDuration: Long
            val scrollDistance: Float

            // Log the mode and multiplier being used for this scroll attempt (Removed)
            // Log.d(TAG, "performScroll - Mode(Delay): $addedDelaySeconds, SkipMultiplier: $currentSkipDistanceMultiplier")

            when (addedDelaySeconds) {
                0 -> { // Continuous Mode
                    scrollDistance = screenHeight * 0.08f // Smaller distance per step (8%)
                    scrollDuration = 50L // Short duration for quick steps
                    endY = startY + (direction * scrollDistance)
                }
                else -> { // Skip Mode (Default or Custom Delay)
                    // Show toast with the multiplier being used in skip mode (Removed)
                    // showToast("Skip Multiplier: ${String.format("%.2f", currentSkipDistanceMultiplier)}")
                    scrollDistance = screenHeight * currentSkipDistanceMultiplier // Use the variable multiplier
                    scrollDuration = 50L // Short duration for jump feel
                    endY = startY + (direction * scrollDistance)
                }
            }

            val clampedEndY = endY.coerceIn(screenHeight * 0.1f, screenHeight * 0.9f)

            if (abs(startY - clampedEndY) < 1.0f) {
                Log.w(TAG, "Scroll distance too small, skipping gesture.")
                return
            }

            scrollPath.moveTo(startX, startY)
            scrollPath.lineTo(startX, clampedEndY)

            val gestureBuilder = GestureDescription.Builder()
            val gesture = GestureDescription.StrokeDescription(
                scrollPath,
                0, // startTime
                scrollDuration // duration
            )
            gestureBuilder.addStroke(gesture)

            val gestureDescription = gestureBuilder.build()
            val result = dispatchGesture(
                gestureDescription,
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        super.onCompleted(gestureDescription)
                        Log.d(TAG, "Scroll completed successfully")
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        super.onCancelled(gestureDescription)
                        Log.e(TAG, "Scroll was cancelled")
                    }
                },
                null // Handler (optional)
            )

            if (!result) {
                Log.e(TAG, "Failed to dispatch scroll gesture")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error performing scroll: ${e.message}", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not needed for this implementation
    }

    override fun onInterrupt() {
        Log.w(TAG, "Service interrupted")
        stopCamera()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        isServiceDestroyed = true // Mark as destroyed
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        cameraExecutor.shutdown()
        stopCamera() // Ensure camera is stopped

        try {
            cameraToggleReceiver?.let {
                unregisterReceiver(it)
                cameraToggleReceiver = null // Clear reference
            }
        } catch (e: IllegalArgumentException) {
             Log.w(TAG, "Receiver not registered or already unregistered: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering camera toggle receiver: ${e.message}")
        }
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    // --- Inner Class: FaceAnalyzer ---
    private inner class FaceAnalyzer(
        private val onGazeDirectionChanged: (GazeDirection) -> Unit
    ) : ImageAnalysis.Analyzer {
        private val faceDetector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL) // Needed for head angle
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL) // Optional but potentially useful
                .build()
        )

        @androidx.camera.core.ExperimentalGetImage
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

                faceDetector.process(image)
                    .addOnSuccessListener { faces ->
                        if (faces.isNotEmpty()) {
                            val face = faces[0] // Process the first detected face
                            val angle = face.headEulerAngleX // Head tilt up/down

                            val gazeDirection = when {
                                angle < -currentSensitivity -> GazeDirection.UP
                                angle > currentSensitivity -> GazeDirection.DOWN
                                else -> GazeDirection.CENTER
                            }
                            onGazeDirectionChanged(gazeDirection)
                        } else {
                            onGazeDirectionChanged(GazeDirection.CENTER) // Default to center if no face
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Face detection failed: ${e.message}")
                        onGazeDirectionChanged(GazeDirection.CENTER) // Default to center on failure
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                 imageProxy.close()
            }
        } // End of analyze method
    } // End of FaceAnalyzer inner class

    // --- Enum for Gaze Direction ---
    enum class GazeDirection {
        UP, DOWN, CENTER
    }

    // --- Utility Methods ---
    private fun checkCameraAvailability(): Boolean {
        return try {
            val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cameraManager.cameraIdList.isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking camera availability: ${e.message}")
            false
        }
    }

    private fun sendCameraStateUpdate(isRunning: Boolean) {
        try {
            val broadcastIntent = Intent(ACTION_CAMERA_STATE_CHANGED).apply {
                `package` = packageName  // Make the broadcast explicit for security
                putExtra(EXTRA_CAMERA_STATE, isRunning)
            }
            sendBroadcast(broadcastIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending camera state update: ${e.message}", e)
        }
    }

    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }
} // End of ScrollAccessibilityService class
