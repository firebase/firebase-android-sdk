# Firebase SQL Android SDK

The Firebase SQL Android SDK (`firebase-sql`) lets an Android app read and write the Cloud SQL for
PostgreSQL database of a Firebase SQL service through its
[PostgREST](https://docs.postgrest.org/en/stable/) API, with a fluent Kotlin API. Each request
carries the Firebase Authentication and Firebase App Check tokens of the app.

This README is for contributors who build and test the SDK.

- To write app code with the SDK, use the
  [`firebase-sql-android` skill](skills/firebase-sql-android/SKILL.md), which is written for
  developers and AI agents, and the KDoc of each public class and method.
- To change the SDK, read [AGENTS.md](AGENTS.md).

## Building

All Gradle commands should be run from the root of this repository.

`./gradlew -PprojectsToPublish="firebase-sql" publishReleasingLibrariesToMavenLocal`

## Running tests

Unit tests:

`./gradlew :firebase-sql:check`

Integration tests, which run against a live Firebase SQL service on a connected device or emulator:
see [Run the tests against a live service](AGENTS.md#run-the-tests-against-a-live-service).

## Code formatting

`./gradlew :firebase-sql:spotlessApply`
