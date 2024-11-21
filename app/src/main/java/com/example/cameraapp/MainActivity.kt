package com.example.cameraapp

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.roundToInt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.widget.Toast

val Purple8e5fb6 = Color(0xFF8e5fb6)

class MainActivity : ComponentActivity() {
    private val CAMERA_PERMISSION_REQUEST = 100
    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.FOREGROUND_SERVICE,
        Manifest.permission.FOREGROUND_SERVICE_CAMERA,
        Manifest.permission.POST_NOTIFICATIONS
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, CAMERA_PERMISSION_REQUEST)
        }

        setContent {
            val darkTheme = isSystemInDarkTheme()

            // Set status bar color based on theme
            window.statusBarColor = if (darkTheme) {
                android.graphics.Color.BLACK
            } else {
                android.graphics.Color.WHITE
            }

            // Set status bar icons color
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !darkTheme
            }

            AIScrollTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        onOpenAccessibilitySettings = { openAccessibilitySettings() }
                    )
                }
            }
        }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    private fun toggleCamera() {
        if (!checkCameraPermission()) {
            requestCameraPermission()
            return
        }

        val intent = Intent(ScrollAccessibilityService.ACTION_TOGGLE_CAMERA)
        sendBroadcast(intent)
    }

    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_REQUEST
        )
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (!allPermissionsGranted()) {
                Toast.makeText(this, "Permissions not granted.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
fun MainScreen(onOpenAccessibilitySettings: () -> Unit) {
    val context = LocalContext.current
    val isDarkTheme = isSystemInDarkTheme()
    
    // Create a mutable state for camera status
    var isCameraRunning by remember { mutableStateOf(ScrollAccessibilityService.isCameraRunning) }

    // Listen for camera state changes
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    ScrollAccessibilityService.ACTION_CAMERA_STATE_CHANGED -> {
                        val newState = intent.getBooleanExtra(ScrollAccessibilityService.EXTRA_CAMERA_STATE, false)
                        isCameraRunning = newState
                    }
                }
            }
        }

        val filter = IntentFilter(ScrollAccessibilityService.ACTION_CAMERA_STATE_CHANGED)
        context.registerReceiver(receiver, filter)

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    // State variables
    var sensitivity by remember { mutableStateOf(ScrollAccessibilityService.currentSensitivity) }
    var scrollSpeed by remember { mutableStateOf(ScrollAccessibilityService.currentScrollSpeed) }
    var isSmoothScrollEnabled by remember { mutableStateOf(ScrollAccessibilityService.isSmoothScrollEnabled) }
    var isAddedDelayEnabled by remember { mutableStateOf(ScrollAccessibilityService.isAddedDelayEnabled) }
    var delaySeconds by remember { mutableStateOf(ScrollAccessibilityService.addedDelaySeconds.toString()) }

    // Add scroll state
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Logo Image
        Image(
            painter = painterResource(
                id = if (isDarkTheme) R.drawable.ic_logo_white else R.drawable.ic_logo_purple
            ),
            contentDescription = "Header Logo",
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .padding(vertical = 24.dp),
            contentScale = ContentScale.Fit
        )

        // Enable Accessibility Service Button
        AIButton(
            onClick = onOpenAccessibilitySettings,
            text = "Enable Accessibility Service"
        )

        // Updated Camera Toggle Button
        AIButton(
            onClick = {
                val intent = Intent(context, ScrollAccessibilityService::class.java).apply {
                    action = ScrollAccessibilityService.ACTION_TOGGLE_CAMERA
                }
                context.startService(intent)
            },
            text = if (isCameraRunning) "Turn Camera OFF" else "Turn Camera ON"
        )

        Text("Adjust Sensitivity", style = MaterialTheme.typography.headlineSmall)

        AISlider(
            value = sensitivity,
            onValueChange = { newValue ->
                sensitivity = newValue
                ScrollAccessibilityService.currentSensitivity = sensitivity
            },
            valueRange = 5f..15f,
            steps = 9,
            title = "Sensitivity",
            startLabel = "More",
            endLabel = "Less"
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Adjust Scrolling Speed", style = MaterialTheme.typography.headlineSmall)

        AISlider(
            value = scrollSpeed,
            onValueChange = { newValue ->
                scrollSpeed = newValue
                ScrollAccessibilityService.currentScrollSpeed = scrollSpeed
            },
            valueRange = 100f..500f,
            steps = 8,
            title = "Scrolling Speed",
            startLabel = "Slow",
            endLabel = "Fast"
        )

        Spacer(modifier = Modifier.weight(1f))

        AIButton(
            onClick = {
                isSmoothScrollEnabled = !isSmoothScrollEnabled
                ScrollAccessibilityService.isSmoothScrollEnabled = isSmoothScrollEnabled
                sendBroadcast(context, ScrollAccessibilityService.ACTION_TOGGLE_SMOOTH_SCROLL)
            },
            text = if (isSmoothScrollEnabled) "Switch to Skipping Mode" else "Switch to Smooth Scrolling"
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AIButton(
                onClick = {
                    isAddedDelayEnabled = !isAddedDelayEnabled
                    ScrollAccessibilityService.isAddedDelayEnabled = isAddedDelayEnabled
                    sendBroadcast(context, ScrollAccessibilityService.ACTION_TOGGLE_ADDED_DELAY)
                },
                text = if (isAddedDelayEnabled) "Added Delay ON" else "Added Delay OFF",
                modifier = Modifier.weight(1f)
            )

            if (isAddedDelayEnabled) {
                OutlinedTextField(
                    value = delaySeconds,
                    onValueChange = { newValue ->
                        if (newValue.isEmpty() || newValue.toIntOrNull() != null) {
                            delaySeconds = newValue
                        }
                    },
                    label = { Text("Delay (seconds)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(120.dp)
                )

                Button(
                    onClick = {
                        val delay = delaySeconds.toIntOrNull() ?: 3
                        ScrollAccessibilityService.addedDelaySeconds = delay
                        val intent = Intent(ScrollAccessibilityService.ACTION_UPDATE_DELAY)
                        intent.putExtra(ScrollAccessibilityService.EXTRA_DELAY_SECONDS, delay)
                        context.sendBroadcast(intent)
                    },
                    modifier = Modifier.height(56.dp)
                ) {
                    Text("Enter")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// Helper function to send broadcasts
private fun sendBroadcast(context: Context, action: String) {
    val intent = Intent(action)
    context.sendBroadcast(intent)
}

@Composable
fun AIButton(onClick: () -> Unit, text: String, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Purple8e5fb6,
            contentColor = Color.White
        )
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun AISlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    title: String,
    startLabel: String,
    endLabel: String
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = Purple8e5fb6,
                activeTrackColor = Purple8e5fb6,
                inactiveTrackColor = Purple8e5fb6.copy(alpha = 0.5f)
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = startLabel, style = MaterialTheme.typography.bodySmall)
            Text(text = endLabel, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun AIScrollTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val robotoFontFamily = FontFamily(
        Font(resId = R.font.roboto_medium, weight = FontWeight.Medium)
    )

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = Purple8e5fb6,
            secondary = Purple8e5fb6,
            background = Color(0xFF121212),
            surface = Color(0xFF121212),
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = Color.White,
            onSurface = Color.White
        )
    } else {
        lightColorScheme(
            primary = Purple8e5fb6,
            secondary = Purple8e5fb6,
            background = Color.White,
            surface = Color.White,
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = Color.Black,
            onSurface = Color.Black
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(
            headlineLarge = MaterialTheme.typography.headlineLarge.copy(fontFamily = robotoFontFamily, fontSize = 32.sp),
            bodyLarge = MaterialTheme.typography.bodyLarge.copy(fontFamily = robotoFontFamily, fontSize = 18.sp),
            bodyMedium = MaterialTheme.typography.bodyMedium.copy(fontFamily = robotoFontFamily, fontSize = 16.sp),
            bodySmall = MaterialTheme.typography.bodySmall.copy(fontFamily = robotoFontFamily, fontSize = 14.sp)
        ),
        content = content
    )
}