# Firebase Android SDK

This repository contains the source code for the Firebase Android SDK. It is a monorepo containing the various Firebase libraries for Android.

## Directory Structure

The repository is organized into several top-level directories:

- **`<library-name>/`**: Each Firebase library resides in its own directory (e.g., `firebase-ai/`, `firebase-firestore/`). These directories contain the source code, build scripts, and tests for that specific library.
- **`ci/`**: Contains scripts and configurations for Continuous Integration.
- **`contributor-docs/`**: Documentation for contributors to the SDK.
- **`docs/`**: General documentation for the SDK.
- **`integ-testing/`**: Integration tests that span multiple libraries.
- **`plugins/`**: Gradle plugins used in the build process.

## Building and Testing

The project uses Gradle for building and managing dependencies. The following are common commands for working with individual library modules.

These examples use the `firebase-ai` library. Replace `:firebase-ai` with the desired library module path (e.g., `:firebase-firestore`).

### Build a Library

To build a specific library and its dependencies:

```bash
./gradlew :firebase-ai:build
```

### Run Linter

To run the linter on a specific library to check for code style and quality issues:

```bash
./gradlew :firebase-ai:lint
```

### Run Unit Tests

To run the unit tests for a specific library:

```bash
./gradlew :firebase-ai:test
```
