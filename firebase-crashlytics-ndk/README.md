# Firebase Crashlytics NDK Component

This component enables NDK crash reporting with Crashlytics.

## Prerequisites

This project depends on three submodules in the `third_party` directory.
Initialize them by running the following commands:

`git submodule init && git submodule update`

## Building

* `firebase-crashlytics-ndk` must be built with NDK 21. Use Android Studio's
  SDK Manager to ensure you have the appropriate NDK version installed, and
  edit `../local.properties` to specify which NDK version to use when building
  this project. For example:
  `ndk.dir=$USER_HOME/Library/Android/sdk/ndk/21.4.7075529`
* All Gradle commands should be run from the root of this repository:
  `./gradlew :firebase-crashlytics-ndk:assemble`

## Running Tests

Integration tests, requiring a running and connected device (emulator or real):
`./gradlew :firebase-crashlytics-ndk:connectedAndroidTest`