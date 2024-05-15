# Firebase Vertex AI SDK

**Preview**: Vertex AI for Firebase is in Public Preview, which means that the product is
not subject to any SLA or deprecation policy and could change in backwards-incompatible
ways.

For developer documentation, please visit https://firebase.google.com/docs/vertex-ai.
This README is for contributors building and running tests for the SDK.

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
