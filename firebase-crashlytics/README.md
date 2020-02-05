# Firebase Crashlytics SDK
[Firebase Crashlytics](https://firebase.google.com/docs/crashlytics/) is a lightweight, realtime
crash reporter that helps you track, prioritize, and fix stability issues that erode your app
quality.

The SDK captures crash reports automatically, and provides methods to annotate and manage them.

## Building

All Gradle commands should be run from the root of this repository.

`./gradlew :firebase-crashlytics:assemble`

## Running Tests
Unit tests:
`./gradlew :firebase-crashlytics:test`

Integration tests, requiring a running and connected device (emulator or real):
`./gradlew :firebase-crashlytics:connectedAndroidTest`
