package com.example.cameraapp

import android.app.Service
import android.content.Intent
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.IBinder
import android.util.Log
import android.os.Handler
import android.os.HandlerThread

class CameraService : Service() {
    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null
    private var cameraDevice: CameraDevice? = null
    private lateinit var backgroundHandler: Handler
    private lateinit var backgroundThread: HandlerThread

    override fun onCreate() {
        super.onCreate()
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        cameraId = getFrontCameraId()
        startBackgroundThread()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        openCamera()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        closeCamera()
        stopBackgroundThread()
    }

    private fun getFrontCameraId(): String? {
        try {
            for (cameraId in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val cameraDirection = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (cameraDirection != null && cameraDirection == CameraCharacteristics.LENS_FACING_FRONT) {
                    return cameraId
                }
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Error accessing camera: ${e.message}")
        }
        return null
    }

    private fun openCamera() {
        try {
            cameraId?.let { id ->
                cameraManager.openCamera(id, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        cameraDevice = camera
                        Log.d(TAG, "Camera opened successfully")
                        // Here you would typically start a capture session
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        Log.d(TAG, "Camera disconnected")
                        closeCamera()
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        Log.e(TAG, "Camera open error: $error")
                        closeCamera()
                    }
                }, backgroundHandler)
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Error opening camera: ${e.message}")
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception opening camera: ${e.message}")
        }
    }

    private fun closeCamera() {
        cameraDevice?.close()
        cameraDevice = null
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground")
        backgroundThread.start()
        backgroundHandler = Handler(backgroundThread.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread.quitSafely()
        try {
            backgroundThread.join()
        } catch (e: InterruptedException) {
            Log.e(TAG, "Error stopping background thread: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "CameraService"
    }
}