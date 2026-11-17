# Firebase SQL Android SDK

The Firebase SQL Android SDK (`firebase-sql`) is for Android apps that read and write the Cloud SQL
for PostgreSQL database of a Firebase SQL service through its
[PostgREST](https://docs.postgrest.org/en/stable/) API.

This README is for contributors who build and test the SDK.

- To write app code with the SDK, use the KDoc of each public class and method.

## Building

All Gradle commands should be run from the root of this repository.

`./gradlew -PprojectsToPublish="firebase-sql" publishReleasingLibrariesToMavenLocal`

## Running tests

Unit tests:

`./gradlew :firebase-sql:check`

## Code formatting

`./gradlew :firebase-sql:spotlessApply`
