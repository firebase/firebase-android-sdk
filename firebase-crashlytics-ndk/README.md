# Firebase Crashlytics NDK Component

This component enables NDK crash reporting with Crashlytics.

## Prerequisites

This project depends on three submodules in the `third_party` directory.
Initialize them by running the following commands:

`git submodule init && git submodule update`

## Building

All Gradle commands should be run from the root of this repository.

`./gradlew :firebase-crashlytics-ndk:assemble`

## Running Tests

Integration tests, requiring a running and connected device (emulator or real):
`./gradlew :firebase-crashlytics-ndk:connectedAndroidTest`