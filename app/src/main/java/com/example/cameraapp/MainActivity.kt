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
import android.util.Log
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import android.content.ActivityNotFoundException
import androidx.preference.PreferenceManager
import androidx.compose.runtime.mutableFloatStateOf // Added import


val Purple8e5fb6 = Color(0xFF8e5fb6)

class MainActivity : ComponentActivity() {
    private val cameraPermissionRequest = 100 
    private val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.FOREGROUND_SERVICE,
        Manifest.permission.FOREGROUND_SERVICE_CAMERA,
        Manifest.permission.POST_NOTIFICATIONS
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(this, requiredPermissions, cameraPermissionRequest) 
        }

        try {
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
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onCreate: ${e.message}", e)
            Toast.makeText(this, "Error initializing app", Toast.LENGTH_LONG).show()
        }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    private fun allPermissionsGranted() = requiredPermissions.all { 
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    @Suppress("DEPRECATION") 
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == cameraPermissionRequest) { 
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

    // SharedPreferences to track if the dialog has been shown
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    var showWelcomeDialog by remember { mutableStateOf(!prefs.getBoolean("has_shown_welcome_dialog", false)) }

    LaunchedEffect(showWelcomeDialog) {
        // Show dialog only if it hasn't been shown before
        if (showWelcomeDialog) {
            // Mark dialog as shown
            prefs.edit().putBoolean("has_shown_welcome_dialog", true).apply()
        }
    }

    if (showWelcomeDialog) {
        AlertDialog(
            onDismissRequest = { showWelcomeDialog = false },
            title = { Text("Welcome to MotionScroll!") },
            text = {
                val annotatedString = buildAnnotatedString {
                    append("For a smooth and functional experience, please read the ")
                    pushStringAnnotation(tag = "URL", annotation = "https://www.motionscroll.app/")
                    withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)) {
                        append("Installation Guide")
                    }
                    pop()
                    append(" on the website to properly configure the application, including setting up the floating overlay shortcut. It is critically important to enable this shortcut as it allows you to quickly toggle head tracking on/off, which is essential for navigating within applications or settings without unintended scrolling.")
                }

                ClickableText(
                    text = annotatedString,
                    onClick = { offset ->
                        annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                            .firstOrNull()?.let { annotation ->
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item))
                                    context.startActivity(intent)
                                } catch (e: ActivityNotFoundException) {
                                    Toast.makeText(context, "No browser found to open the link.", Toast.LENGTH_SHORT).show()
                                }
                            }
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface)
                )
            },
            confirmButton = {
                TextButton(onClick = { showWelcomeDialog = false }) {
                    Text("Got It")
                }
            }
        )
    }

    // Create a mutable state for camera status
    var isCameraRunning by remember { mutableStateOf(ScrollAccessibilityService.isCameraRunning) }

    // Listen for camera state changes
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                try {
                    when (intent?.action) {
                        ScrollAccessibilityService.ACTION_CAMERA_STATE_CHANGED -> {
                            val newState = intent.getBooleanExtra(ScrollAccessibilityService.EXTRA_CAMERA_STATE, false)
                            isCameraRunning = newState
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MainScreen", "Error in broadcast receiver: ${e.message}", e)
                }
            }
        }

        try {
            val filter = IntentFilter(ScrollAccessibilityService.ACTION_CAMERA_STATE_CHANGED)
            @Suppress("UnspecifiedRegisterReceiverFlag") // Suppress the warning
            context.registerReceiver(receiver, filter)
        } catch (e: Exception) {
            Log.e("MainScreen", "Error registering receiver: ${e.message}", e)
            Toast.makeText(context, "Error registering camera state receiver", Toast.LENGTH_SHORT).show()
        }

        // Return the dispose effect
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                Log.e("MainScreen", "Error unregistering receiver: ${e.message}", e)
            }
        }
    }

    // State variables
    // Initialize UI state with the new default value from the companion object
    var skipDistanceMultiplier by remember { mutableFloatStateOf(ScrollAccessibilityService.DEFAULT_SKIP_MULTIPLIER) } // State for skip distance (default 0.09f) // Changed to mutableFloatStateOf
    var delaySeconds by remember { mutableStateOf(ScrollAccessibilityService.addedDelaySeconds.toString()) } // Keep state for delay input

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
            text = "Accessibility Services"
        )

        // Camera Toggle Switch
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Camera", style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = isCameraRunning,
                onCheckedChange = { checked -> // Use the 'checked' parameter from lambda
                    // Optimistically update UI state immediately
                    isCameraRunning = checked // Update local state based on switch action

                    // Send command to service
                    try {
                        Log.d("MainScreen", "Sending camera toggle command via Switch (new state: $checked)")
                    val intent = Intent(context, ScrollAccessibilityService::class.java).apply {
                        action = ScrollAccessibilityService.ACTION_TOGGLE_CAMERA
                    }
                    context.startService(intent)
                } catch (e: Exception) {
                    Log.e("MainScreen", "Error toggling camera: ${e.message}", e)
                    Toast.makeText(
                        context,
                        "Error toggling camera. Please check accessibility service is enabled.",
                        Toast.LENGTH_SHORT
                    ).show()
                    // The BroadcastReceiver will handle the definitive state update from the service
                }
            }
            )
        }

        // Custom Overlay Toggle Switch
        var isOverlayRunning by remember { mutableStateOf(false) } // State for overlay service

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Floating button", style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = isOverlayRunning,
                onCheckedChange = { checked ->
                    if (checked) {
                        // Check for SYSTEM_ALERT_WINDOW permission
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                            // Permission not granted, prompt user
                            Toast.makeText(context, "Please grant Draw Over Other Apps permission", Toast.LENGTH_LONG).show()
                            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName))
                            context.startActivity(intent)
                            // Do not start the service yet, wait for the user to grant permission
                            isOverlayRunning = false // Revert the switch state
                        } else {
                            // Permission granted, start the custom overlay service
                            isOverlayRunning = checked // Update the switch state
                            val intent = Intent(context, CameraOverlayService::class.java)
                            context.startService(intent)
                            Log.d("MainScreen", "Started CameraOverlayService")
                        }
                    } else {
                        // Stop the custom overlay service
                        isOverlayRunning = checked // Update the switch state
                        val intent = Intent(context, CameraOverlayService::class.java)
                        context.stopService(intent)
                        Log.d("MainScreen", "Stopped CameraOverlayService")
                    }
                }
            )
        }

        // Add Skip Distance Slider
        Text("Adjust Skip Distance", style = MaterialTheme.typography.headlineSmall)
        AISlider(
            value = skipDistanceMultiplier,
            onValueChange = { newValue ->
                skipDistanceMultiplier = newValue
                // Send Intent to update service using startService
                val intent = Intent(context, ScrollAccessibilityService::class.java).apply { // Explicitly target service
                    action = ScrollAccessibilityService.ACTION_UPDATE_SKIP_DISTANCE
                    putExtra(ScrollAccessibilityService.EXTRA_SKIP_DISTANCE, newValue)
                }
                context.startService(intent) // Use startService to ensure delivery to onStartCommand
            },
            valueRange = 0.03f..0.15f, // Correct Range: 3% to 15%
            //steps = 4, // Correct Steps: 5 steps total (0.03 increments: 0.03, 0.06, 0.09, 0.12, 0.15) -> 0.09 is center
            title = "Skip Distance",
            startLabel = "Less",
            endLabel = "More"
        )

        Spacer(modifier = Modifier.weight(1f)) // Keep spacer to push delay to bottom

        // Simplified Delay / Scroll Mode Input
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = delaySeconds,
                onValueChange = { newValue ->
                    // Allow only digits, ensure it's not empty before trying to parse
                    if (newValue.all { it.isDigit() }) {
                         delaySeconds = newValue
                    }
                },
                label = { Text("Scroll Mode/Delay") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f) // Take available space
            )
            Button(
                onClick = {
                    // Default to 1 (Skip Mode) if empty or invalid
                    val delayValue = delaySeconds.toIntOrNull()?.coerceAtLeast(0) ?: 1
                    delaySeconds = delayValue.toString() // Update state to reflect parsed value
                    // Send Intent to update service using startService
                    val intent = Intent(context, ScrollAccessibilityService::class.java).apply { // Explicitly target service
                        action = ScrollAccessibilityService.ACTION_UPDATE_DELAY
                        putExtra(ScrollAccessibilityService.EXTRA_DELAY_SECONDS, delayValue)
                    }
                    context.startService(intent) // Use startService
                    Log.d("MainScreen", "Sent delay update: $delayValue")
                },
                modifier = Modifier.height(56.dp) // Match TextField height
            ) {
                Text("Set")
            }
        }
        // Info Text for Delay Input
        Row(
             modifier = Modifier.fillMaxWidth().padding(start = 4.dp), // Align with TextField
             verticalAlignment = Alignment.CenterVertically,
             horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
             Icon(
                 painter = painterResource(id = android.R.drawable.ic_dialog_info), // Standard info icon
                 contentDescription = "Info",
                 modifier = Modifier.size(16.dp),
                 tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f) // Adjust color/alpha
             )
             Text(
                 text = "0 = Continuous, 1 = Skip (Default), >1 = Delay (seconds)",
                 style = MaterialTheme.typography.bodySmall,
                 color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
             )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun AIButton(onClick: () -> Unit, text: String, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(50), // Increase rounding for pill shape
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
    // steps: Int, // Remove steps parameter for continuous slider
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
            // steps = steps, // Remove steps from internal Slider call
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
