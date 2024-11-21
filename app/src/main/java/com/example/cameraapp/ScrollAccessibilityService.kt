package com.example.cameraapp

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
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

class ScrollAccessibilityService : AccessibilityService(), LifecycleOwner {
    private val TAG = "ScrollAccessibilityService"
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var lifecycleRegistry: LifecycleRegistry
    private var lastScrollTime = 0L
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalyzer: ImageAnalysis? = null

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
        lifecycleRegistry = LifecycleRegistry(this)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Service connected")
        lifecycleRegistry.currentState = Lifecycle.State.STARTED

        val info = AccessibilityServiceInfo()
        info.flags = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS or
                    AccessibilityServiceInfo.DEFAULT
        info.eventTypes = AccessibilityEvent.TYPE_VIEW_SCROLLED or 
                         AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        serviceInfo = info

        isServiceEnabled = true
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
            ACTION_TOGGLE_CAMERA -> {
                if (isCameraRunning) {
                    stopCamera()
                } else {
                    startCamera()
                }
                Log.d(TAG, "Camera toggle completed. Running: $isCameraRunning")
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
        }
        return START_STICKY
    }

    private fun startCamera() {
        Log.d(TAG, "Starting camera...")
        
        if (!isServiceEnabled) {
            Log.e(TAG, "Service not enabled, cannot start camera")
            return
        }

        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                Log.d(TAG, "Setting up camera...")
                cameraProvider = cameraProviderFuture.get()
                cameraProvider?.unbindAll()

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
                // Broadcast the state change
                val broadcastIntent = Intent(ACTION_CAMERA_STATE_CHANGED)
                broadcastIntent.putExtra(EXTRA_CAMERA_STATE, true)
                sendBroadcast(broadcastIntent)
            } catch (exc: Exception) {
                Log.e(TAG, "Failed to start camera: ${exc.message}", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCamera() {
        Log.d(TAG, "Stopping camera...")
        try {
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
            cameraProvider?.unbindAll()
            imageAnalyzer = null
            isCameraRunning = false
            
            val broadcastIntent = Intent(ACTION_CAMERA_STATE_CHANGED)
            broadcastIntent.putExtra(EXTRA_CAMERA_STATE, false)
            sendBroadcast(broadcastIntent)
            
            Log.d(TAG, "Camera stopped successfully")
        } catch (exc: Exception) {
            Log.e(TAG, "Failed to stop camera: ${exc.message}")
        }
    }

    private fun handleGazeDirection(gazeDirection: GazeDirection) {
        if (!isServiceEnabled || !isCameraRunning) return

        val currentTime = System.currentTimeMillis()
        if (isAddedDelayEnabled && currentTime - lastDetectionTime < addedDelaySeconds * 1000L) {
            return
        }

        when (gazeDirection) {
            GazeDirection.UP, GazeDirection.DOWN -> {
                if (currentTime - lastScrollTime > 500) { // Prevent too frequent scrolling
                    performScroll(if (gazeDirection == GazeDirection.UP) -1f else 1f)
                    lastScrollTime = currentTime
                    if (isAddedDelayEnabled) {
                        lastDetectionTime = currentTime
                    }
                }
            }
            else -> {} // Do nothing for other gaze directions
        }
    }

    private fun performScroll(direction: Float) {
        if (!isServiceEnabled || !isCameraRunning) {
            Log.d(TAG, "Scroll ignored - service enabled: $isServiceEnabled, camera running: $isCameraRunning")
            return
        }

        Log.d(TAG, "Performing scroll: $direction")
        val scrollPath = Path()
        val screenHeight = resources.displayMetrics.heightPixels
        val startY = screenHeight / 2f
        
        scrollPath.moveTo(500f, startY)
        scrollPath.lineTo(500f, startY - (direction * currentScrollSpeed))

        val gestureBuilder = GestureDescription.Builder()
        val duration = if (isSmoothScrollEnabled) 300 else 100
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(scrollPath, 0, duration.toLong()))

        dispatchGesture(gestureBuilder.build(), object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                Log.d(TAG, "Scroll completed")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Log.e(TAG, "Scroll cancelled")
            }
        }, null)
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
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        cameraExecutor.shutdown()
        stopCamera()
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    private inner class FaceAnalyzer(
        private val onGazeDirectionChanged: (GazeDirection) -> Unit
    ) : ImageAnalysis.Analyzer {
        private val faceDetector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
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
                            val gazeDirection = when {
                                abs(face.headEulerAngleX) > currentSensitivity -> {
                                    if (face.headEulerAngleX > 0) GazeDirection.UP else GazeDirection.DOWN
                                }
                                else -> GazeDirection.CENTER
                            }
                            onGazeDirectionChanged(gazeDirection)
                        } else {
                            onGazeDirectionChanged(GazeDirection.CENTER)
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Face detection failed: $e")
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
}