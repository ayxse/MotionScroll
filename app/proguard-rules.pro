# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep CameraX classes (Recommended by Google)
-keep public class androidx.camera.core.** { <fields>; <methods>; }
-keep public class androidx.camera.camera2.** { <fields>; <methods>; }
-keep public class androidx.camera.lifecycle.** { <fields>; <methods>; }
-keep public class androidx.camera.view.** { <fields>; <methods>; }

# Keep ML Kit Face Detection classes (Commonly needed)
-keep class com.google.mlkit.vision.face.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_face.** { *; }

# Keep Accessibility Service (Ensure it's not removed)
-keep class com.example.cameraapp.ScrollAccessibilityService { *; }
