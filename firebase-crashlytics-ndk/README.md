# Firebase Crashlytics NDK Component

This component enables NDK crash reporting with Crashlytics.

## Prerequisites

This project depends on two submodules in the `third_party` directory.
Initialize them by running the following commands:

`git submodule init && git submodule update`

In addition, **this project requires NDK version r17c to build.**

### Setting up NDK r17c

1. Follow the instructions
[here](https://developer.android.com/studio/projects/install-ndk#specific-version)
to install NDK version 17.2.4988734.

## Building

All Gradle commands should be run from the root of this repository.

`./gradlew :firebase-crashlytics-ndk:assemble`

## Running Tests

Integration tests, requiring a running and connected device (emulator or real):
`./gradlew :firebase-crashlytics-ndk:connectedAndroidTest`