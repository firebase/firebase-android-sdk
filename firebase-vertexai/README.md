# Firebase Vertex AI SDK

TODO

## Building

All Gradle commands should be run from the root of this repository.

`./gradlew :firebase-vertexai:publishToMavenLocal`

## Running Tests

Unit tests:

`./gradlew :firebase-vertexai:check`

Integration tests, requiring a running and connected device (emulator or real):

`./gradlew :firebase-vertexai:deviceCheck`

## Code Formatting

Format Kotlin code in this SDK in Android Studio using
the [ktfmt plugin](https://plugins.jetbrains.com/plugin/14912-ktfmt) with code style to
**Google (internal)**, or by running:

`./gradlew :firebase-vertexai:ktfmtFormat`
