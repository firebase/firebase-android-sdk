# Firebase Crashlytics NDK Component

This component enables NDK crash reporting with Crashlytics.

## Prerequisites

This project depends on two submodules in the `third_party` directory.
Initialize them by running the following commands:

`git submodule init && git submodule update`

In addition, **this project requires NDK version r17c to build.**

### Setting up NDK r17c

1. Download the appropriate zip for your build environment
[here](https://developer.android.com/ndk/downloads/older_releases.html).
2. Unzip the package into an accessible directory.
3. In your `local.properties` file in the root of this project, add
`ndk.dir=/path/to/android-ndk-r17c` with the path to the unzipped package.

## Building

All Gradle commands should be run from the root of this repository.

`./gradlew :firebase-crashlytics-ndk:assemble`

## Running Tests

Integration tests, requiring a running and connected device (emulator or real):
`./gradlew :firebase-crashlytics-ndk:connectedAndroidTest`