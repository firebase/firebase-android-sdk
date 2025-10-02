# Firebase AI SDK

For developer documentation, please visit https://firebase.google.com/docs/vertex-ai. This README is
for contributors building and running tests for the SDK.

## Building

All Gradle commands should be run from the root of this repository.

`./gradlew :firebase-ai:publishToMavenLocal`

## Running Tests

> [!IMPORTANT] These unit tests require mock response files, which can be downloaded by running
> `./firebase-ai/update_responses.sh` from the root of this repository.

Unit tests:

`./gradlew :firebase-ai:check`

Integration tests, requiring a running and connected device (emulator or real):

`./gradlew :firebase-ai:deviceCheck`

## Code Formatting

Format Kotlin code in this SDK in Android Studio using the [spotless
plugin]([https://plugins.jetbrains.com/plugin/14912-ktfmt](https://github.com/diffplug/spotless) by
running:

`./gradlew firebase-ai:spotlessApply`
