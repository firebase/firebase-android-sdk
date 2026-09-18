# Firebase Crashlytics Telemetry SDK

The Firebase Crashlytics Telemetry SDK (`firebase-crashlytics-telemetry`) provides OpenTelemetry-based tracing and log correlation for [Firebase Crashlytics](https://firebase.google.com/docs/crashlytics/) on Android, backed by native on-disk span persistence via [`firebase-telemetry-persistence`](https://github.com/firebase/firebase-telemetry-persistence).

## Native Persistence Dependency (`firebase-telemetry-persistence`)

By default, CMake resolves [`firebase/firebase-telemetry-persistence`](https://github.com/firebase/firebase-telemetry-persistence) automatically via `FetchContent` if a local checkout is not provided.

For local cross-repo development against an unsubmitted or sibling checkout of `firebase-telemetry-persistence`, you can override the path using either:

- **Environment Variable**: `export FIREBASE_PERSISTENCE_DIR=/path/to/firebase-telemetry-persistence`
- **Gradle Property**: Add `firebase.persistence.dir=/path/to/firebase-telemetry-persistence` in `gradle.properties` (or `~/.gradle/gradle.properties`).

## Building

All Gradle commands should be run from the root of this repository.

`./gradlew :firebase-crashlytics-telemetry:assemble`

## Running Tests

Unit tests: `./gradlew :firebase-crashlytics-telemetry:test`

Integration tests, requiring a running and connected device (emulator or real):
`./gradlew :firebase-crashlytics-telemetry:connectedAndroidTest`

