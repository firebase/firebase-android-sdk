# Firebase Sessions SDK

## Building

All Gradle commands should be run from the root of this repository.

`./gradlew -PprojectsToPublish=":firebase-sessions" publishProjectsToMavenLocal`

## Running Tests

Unit tests:

`./gradlew :firebase-sessions:test`

Integration tests, requiring a running and connected device (emulator or real):

`./gradlew :firebase-sessions:connectedAndroidTest`
