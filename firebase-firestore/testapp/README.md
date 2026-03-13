# Firestore Test App

This directory contains a minimal Android application that uses the Firestore API.

## Key Features

* Linkage: It links against the `firebase-firestore` module located in the parent directory.
* Testing: It is useful for testing out new features and verifying behavior in a real Android environment.
* Performance Profiling: It can be used for performance profiling. Note that profiling should always be performed on **release** builds and on-device to reflect the performance of a "normal" Android application.

## Background Testing

The application is structured to run tests in the background to survive activity restarts (like screen rotations).
* Test logic is separated into `Tester.kt`.
* `MainActivity.kt` uses Kotlin Coroutines with a single-thread dispatcher stored in a static `companion object` to ensure the test survives lifecycle events and runs idempotently (preventing duplicate test executions).

## Getting Started

1. **Download Test Data:** Download the SQLite databases from [GitHub Issue #7905](https://github.com/firebase/firebase-android-sdk/issues/7905).
2. **Host Databases Locally:**
   * Navigate to the directory containing the downloaded SQLite databases.
   * Start a local HTTP server on port 8000 (e.g., `python -m http.server 8000`).
3. **Configure Firebase:** Copy your `google-services.json` file into this directory (`testapp/`).
4. **Run the Application:**
   * Launch the application in an Android emulator or on a physical device.
   * The `Tester.kt` logic will automatically download the hosted SQLite databases and place them in the Firestore cache directory.
   * The application will execute a query and log received snapshots.
5. **Monitor Logs:**
   * View the output in the Android Studio Logcat window.
   * **Filter:** `package:mine tag:FirestoreTestApp`

> **Note:** The `Tester.kt` file currently calls `disableNetwork()` to force the use of local cache. Comment out this line if you want to test online behavior.

