# Firebase Crashlytics NDK Component

This component enables NDK crash reporting with Crashlytics.

Requires NDK version r17c to build.

## Building

All Gradle commands should be run from the root of this repository.

`./gradlew :firebase-crashlytics-ndk:assemble`

## Running Tests

Integration tests, requiring a running and connected device (emulator or real):
`./gradlew :firebase-crashlytics-ndk:connectedAndroidTest`