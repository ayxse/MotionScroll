<p align="center">
  <a href="https://ayxse.github.io/Motionscroll-website/" target="_blank">
    <img src="docs/images/motionscroll.png" alt="MotionScroll Logo" width="150">
  </a>
  <br> 
  <h2 align="center">Motion<span style="color:#905cb4;">Scroll</span></h2>
</p>

Scroll through your Android screen using simple head movements. Built with Jetpack Compose and ML Kit.

MotionScroll is an Android application that leverages the front camera and accessibility features to enable hands-free scrolling based on head tilt detection.

## Screenshots

Here's a glimpse of MotionScroll in action:

<img src="docs/images/1.png" alt="Screenshot 1" width="400"> <img src="docs/images/2.png" alt="Screenshot 2" width="400">
<br>
<img src="docs/images/3.png" alt="Screenshot 3" width="400"> <img src="docs/images/4.png" alt="Screenshot 4" width="400">
<br>
<img src="docs/images/5.png" alt="Screenshot 5" width="400"> <img src="docs/images/6.png" alt="Screenshot 6" width="400">


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

*   **Version:** 1.0.0
*   **Updated at:** 2025-04-21 
*   **Min. Android version:** API 24 (Android 7.0 Nougat) or higher 
*   **Download:** [APK on GitHub Releases](https://github.com/ayxse/MotionScroll/releases/tag/v1.0.0) 


## Tech Stack

*   **Language:** Kotlin
*   **User Interface:** Jetpack Compose
*   **Concurrency:** Coroutines
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

