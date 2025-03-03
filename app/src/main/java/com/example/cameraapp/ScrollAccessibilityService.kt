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
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var lifecycleRegistry: LifecycleRegistry
    private var lastScrollTime = 0L
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var isServiceDestroyed = false
    private var cameraToggleReceiver: BroadcastReceiver? = null

    companion object {
        const val DEFAULT_SENSITIVITY = 10f
        var currentSensitivity = DEFAULT_SENSITIVITY
        const val DEFAULT_SCROLL_SPEED = 300f  // Changed from 500f to 300f
        var currentScrollSpeed = DEFAULT_SCROLL_SPEED
        const val ACTION_TOGGLE_SERVICE = "com.example.cameraapp.ACTION_TOGGLE_SERVICE"
        const val ACTION_TOGGLE_SMOOTH_SCROLL = "com.example.cameraapp.ACTION_TOGGLE_SMOOTH_SCROLL"
        const val ACTION_TOGGLE_SKIPPING = "com.example.cameraapp.ACTION_TOGGLE_SKIPPING"
        const val ACTION_UPDATE_SKIP_LEVEL = "com.example.cameraapp.ACTION_UPDATE_SKIP_LEVEL"
        const val EXTRA_SKIP_LEVEL = "com.example.cameraapp.EXTRA_SKIP_LEVEL"
        const val ACTION_TOGGLE_ADDED_DELAY = "com.example.cameraapp.ACTION_TOGGLE_ADDED_DELAY"
        const val ACTION_UPDATE_DELAY = "com.example.cameraapp.ACTION_UPDATE_DELAY"
        const val EXTRA_DELAY_SECONDS = "com.example.cameraapp.EXTRA_DELAY_SECONDS"
        const val ACTION_TOGGLE_CAMERA = "com.example.cameraapp.ACTION_TOGGLE_CAMERA"
        var isSmoothScrollEnabled = false
        var isSkippingEnabled = false
        var skipLevel = 1
        var isAddedDelayEnabled = false
        var addedDelaySeconds = 3
        var isCameraRunning = false
        const val ACTION_CAMERA_STATE_CHANGED = "com.example.cameraapp.ACTION_CAMERA_STATE_CHANGED"
        const val EXTRA_CAMERA_STATE = "camera_state"
    }

    private var isServiceEnabled = false
    private var isSkippingMode = false
    private var skipLevel = 1
    private var lastDetectionTime = 0L
    private val addedDelay = 5000L // 5 seconds

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
            info.eventTypes = AccessibilityEvent.TYPE_VIEW_SCROLLED or 
                             AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            
            try {
                serviceInfo = info
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
            ACTION_TOGGLE_SMOOTH_SCROLL -> {
                isSmoothScrollEnabled = !isSmoothScrollEnabled
                Log.d(TAG, "Smooth scroll ${if (isSmoothScrollEnabled) "enabled" else "disabled"}")
            }
            ACTION_TOGGLE_SKIPPING -> {
                isSkippingEnabled = !isSkippingEnabled
                Log.d(TAG, "Skipping mode ${if (isSkippingEnabled) "enabled" else "disabled"}")
            }
            ACTION_UPDATE_SKIP_LEVEL -> {
                skipLevel = intent.getIntExtra(EXTRA_SKIP_LEVEL, 1)
                Log.d(TAG, "Skip level updated to $skipLevel")
            }
            ACTION_TOGGLE_ADDED_DELAY -> {
                isAddedDelayEnabled = !isAddedDelayEnabled
                Log.d(TAG, "Added delay ${if (isAddedDelayEnabled) "enabled" else "disabled"}")
            }
            ACTION_UPDATE_DELAY -> {
                addedDelaySeconds = intent.getIntExtra(EXTRA_DELAY_SECONDS, 3)
                Log.d(TAG, "Added delay updated to $addedDelaySeconds seconds")
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
                    cameraProvider?.unbindAll()

                    if (!isServiceEnabled) {
                        Log.e(TAG, "Service disabled while starting camera")
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

                    cameraProvider?.bindToLifecycle(
                        this,
                        cameraSelector,
                        imageAnalyzer
                    )
                    
                    isCameraRunning = true
                    Log.d(TAG, "Camera started successfully")
                    sendCameraStateUpdate(true)
                } catch (exc: Exception) {
                    Log.e(TAG, "Failed to start camera: ${exc.message}", exc)
                    stopCamera()
                }
            }, ContextCompat.getMainExecutor(this))
        } catch (e: Exception) {
            Log.e(TAG, "Error starting camera: ${e.message}", e)
            stopCamera()
        }
    }

    private fun stopCamera() {
        Log.d(TAG, "Stopping camera...")
        try {
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
            cameraProvider?.unbindAll()
            imageAnalyzer = null
            isCameraRunning = false
            
            Log.d(TAG, "Camera stopped successfully")
            sendCameraStateUpdate(false)
        } catch (exc: Exception) {
            Log.e(TAG, "Failed to stop camera: ${exc.message}")
        }
    }

    private fun handleGazeDirection(gazeDirection: GazeDirection) {
        if (!isServiceEnabled || !isCameraRunning) {
            Log.d(TAG, "Ignoring gaze direction - service enabled: $isServiceEnabled, camera running: $isCameraRunning")
            return
        }

        val currentTime = System.currentTimeMillis()
        
        // Reduce the minimum time between scrolls for better responsiveness
        val minScrollInterval = 200L // 200ms between scrolls
        
        if (currentTime - lastScrollTime < minScrollInterval) {
            return
        }

        when (gazeDirection) {
            GazeDirection.UP, GazeDirection.DOWN -> {
                Log.d(TAG, "Processing gaze direction: $gazeDirection")
                // Adjust scroll direction and magnitude
                val scrollDirection = when (gazeDirection) {
                    GazeDirection.UP -> -1f
                    GazeDirection.DOWN -> 1f
                    else -> 0f
                }
                
                if (scrollDirection != 0f) {
                    performScroll(scrollDirection)
                    lastScrollTime = currentTime
                    if (isAddedDelayEnabled) {
                        lastDetectionTime = currentTime
                    }
                }
            }
            GazeDirection.CENTER -> {
                Log.d(TAG, "Center position detected")
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
            val scrollPath = Path()
            
            // Adjust scroll coordinates
            val startX = screenHeight / 2f
            val startY = screenHeight / 2f
            val endY = startY + (direction * currentScrollSpeed)
            
            scrollPath.moveTo(startX, startY)
            scrollPath.lineTo(startX, endY)

            val gestureBuilder = GestureDescription.Builder()
            val scrollDuration = if (isSmoothScrollEnabled) 300L else 100L
            val gesture = GestureDescription.StrokeDescription(
                scrollPath,
                0,
                scrollDuration
            )
            
            gestureBuilder.addStroke(gesture)
            
            val gestureDescription = gestureBuilder.build()
            val result = dispatchGesture(
                gestureDescription,
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        Log.d(TAG, "Scroll completed successfully")
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        Log.e(TAG, "Scroll was cancelled")
                    }
                },
                null
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
        Log.d(TAG, "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        isServiceDestroyed = true
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        cameraExecutor.shutdown()
        stopCamera()
        
        try {
            cameraToggleReceiver?.let {
                unregisterReceiver(it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering camera toggle receiver: ${e.message}")
        }
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    private inner class FaceAnalyzer(
        private val onGazeDirectionChanged: (GazeDirection) -> Unit
    ) : ImageAnalysis.Analyzer {
        private val faceDetector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
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
                            val face = faces[0]
                            val angle = face.headEulerAngleX
                            Log.d(TAG, "Face detected with angle X: $angle")
                            
                            // Adjust sensitivity thresholds
                            val gazeDirection = when {
                                angle < -currentSensitivity -> GazeDirection.UP
                                angle > currentSensitivity -> GazeDirection.DOWN
                                else -> GazeDirection.CENTER
                            }
                            onGazeDirectionChanged(gazeDirection)
                        } else {
                            Log.d(TAG, "No faces detected")
                            onGazeDirectionChanged(GazeDirection.CENTER)
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Face detection failed: ${e.message}")
                        onGazeDirectionChanged(GazeDirection.CENTER)
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }
    }

    enum class GazeDirection {
        UP, DOWN, CENTER
    }

    private fun checkCameraAvailability(): Boolean {
        return try {
            val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
            cameraManager.cameraIdList.isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking camera availability: ${e.message}")
            false
        }
    }

    private fun sendCameraStateUpdate(isRunning: Boolean) {
        try {
            val broadcastIntent = Intent(ACTION_CAMERA_STATE_CHANGED).apply {
                `package` = packageName  // Make the broadcast explicit
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
}