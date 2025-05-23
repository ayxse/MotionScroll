<p align="center">
  <a href="https://www.motionscroll.app" target="_blank">
    <img src="docs/images/motionscroll.png" alt="MotionScroll Logo" width="150">
  </a>
  <br>
<h2 align="center">MotionScroll</h2>
</p>

<p align="center">
  <!-- Installation Guide Tab/Button -->
  <a href="https://www.motionscroll.app/#/guide" target="_blank">
    <img src="https://img.shields.io/badge/Installation-Guide-%238E5FB6?style=for-the-badge" alt="Installation Guide"/>
  </a>
</p>

Scroll through your Android screen using simple head movements. Built with Jetpack Compose and ML Kit.

MotionScroll is an Android application that leverages the front camera and accessibility features to enable hands-free scrolling based on head tilt detection.

**Important Note:** To avoid unintended scrolling when navigating within the application settings or other parts of your device, it is **critically important** to use the shortcut overlay to disable camera-based head tracking **before** you start navigating. Head tracking remains active while the app is open, and attempting to navigate menus or settings with head tracking enabled and without the shortcut overlay to quickly disable it will likely result in unwanted scrolling. Always use the shortcut overlay to toggle tracking off when you need to use standard touch or navigation methods.

## Screenshots

Here's a glimpse of MotionScroll in action:

<img src="docs/images/1.png" alt="Screenshot 1" width="30%"> <img src="docs/images/2.png" alt="Screenshot 2" width="30%"> <img src="docs/images/3.png" alt="Screenshot 3" width="30%">
<br>
<img src="docs/images/4.png" alt="Screenshot 4" width="30%"> <img src="docs/images/5.png" alt="Screenshot 5" width="30%"> <img src="docs/images/6.png" alt="Screenshot 6" width="30%">

## Features

*   **Head-Controlled Scrolling:** Navigate content vertically using up/down head tilts.
*   **System-Wide Control:** Works in most scrollable apps via Android's Accessibility Service.
*   **Multiple Scroll Modes:**
    *   **Continuous:** Smooth scrolling while head is tilted.
    *   **Skip:** Jumps a set distance (adjustable) with a 1s cooldown.
    *   **Delay:** Jumps a set distance (adjustable) with a custom cooldown (seconds).
*   **Adjustable Skip Distance:** Fine-tune scroll amount for Skip/Delay modes via a slider.
*   **Camera Toggle:** Easily enable/disable head tracking.
*   **Theme Support:** Adapts to system Light/Dark themes.
*   **Privacy Focused:** Face detection processing happens on-device.

## Download

*   **Version:** 1.0.2
*   **Updated at:** 2025-04-27
*   **Min. Android version:** API 24 (Android 7.0 Nougat) or higher
*   **Download:** [APK on GitHub Releases](https://github.com/ayxse/MotionScroll/releases/tag/v1.0.2)


## Tech Stack

*   **Language:** Kotlin
*   **User Interface:** Jetpack Compose
*   **Camera:** CameraX
*   **Machine Learning:** Google ML Kit (Face Detection)
*   **Core:** Android Accessibility Service, Android SDK

## Building

1.  Ensure you have the latest stable version of Android Studio installed.
2.  Clone the repository: `git clone https://github.com/ayxse/MotionScroll.git`
3.  Open the project in Android Studio.
4.  Let Gradle sync the project dependencies.
5.  Run the `app` configuration on an emulator or physical device.

## License

Copyright 2025 Cristian <!-- Or your name/entity -->

Licensed under the Apache License, Version 2.0. See the [LICENSE](LICENSE) file for the full license text.
