# Firebase Sessions SDK

The Firebase Sessions SDK is used by Crashlytics and Performance internally to measure sessions.

## Building

All Gradle commands should be run from the root of this repository.

`./gradlew :firebase-sessions:publishToMavenLocal`

## Running Tests

Unit tests:

`./gradlew :firebase-sessions:test`

Integration tests, requiring a running and connected device (emulator or real):

`./gradlew :firebase-sessions:connectedAndroidTest`

## Code Formatting

Format Kotlin code in this SDK in Android Studio using
the [ktfmt plugin](https://plugins.jetbrains.com/plugin/14912-ktfmt) with code style to
**Google (internal)**, or by running:

`./gradlew :firebase-sessions:ktfmtFormat`
