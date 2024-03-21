# Firebase Vertex SDK

TODO

## Building

All Gradle commands should be run from the root of this repository.

`./gradlew :firebase-vertex:publishToMavenLocal`

## Running Tests

Unit tests:

`./gradlew :firebase-vertex:check`

Integration tests, requiring a running and connected device (emulator or real):

`./gradlew :firebase-vertex:deviceCheck`

## Code Formatting

Format Kotlin code in this SDK in Android Studio using
the [ktfmt plugin](https://plugins.jetbrains.com/plugin/14912-ktfmt) with code style to
**Google (internal)**, or by running:

`./gradlew :firebase-vertex:ktfmtFormat`
