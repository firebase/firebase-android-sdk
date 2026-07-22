# Agents

This guide provides essential information for working within the `firebase-android-sdk` repository.

## Project Overview

This repository contains the source code for the Firebase Android SDKs. It is a large, multi-module
Gradle project. The project is written in a mix of Java and Kotlin.

The project is structured as a collection of libraries, each representing a Firebase product or a
shared component. These libraries are published as Maven artifacts to Google's Maven Repository.

## Project Structure

The `subprojects.cfg` file lists all the subprojects in this repository. Each line in this file
follows the format `<project-path> # <project-type>`, where `project-type` can be one of the
following:

- `sdk`: A public-facing SDK that is published.
- `test`: A test application or a test-only module.
- `util`: A utility module that is not part of the public API.
- `directory`: A directory containing other subprojects.

This file is useful for understanding the role of each subproject in the repository.

## Environment Setup

To work with this repository, the Android SDK must be installed. Use the `sdkmanager` command-line
tool for this purpose.

1. **Install Java 17**
   - All SDKs require Java 17 to build and run. Earlier or later versions will not suffice.
     If multiple versions of java are installed, the `JAVA_HOME` environment variable can be set without modifying system configuration.
2. **Install Android SDK Command-Line Tools**:
   - If not already installed, download the command-line tools from the
     [Android Studio page](https://developer.android.com/studio#command-line-tools-only).
   - Create a directory for the Android SDK, e.g., `android_sdk`.
   - Unzip the downloaded package. This will create a `cmdline-tools` directory. Move this
     directory to `android_sdk/cmdline-tools/latest`.
   - The final structure should be `android_sdk/cmdline-tools/latest/`.
3. **Install required SDK packages**:
   - Use `sdkmanager` to install the necessary platforms, build tools, and other packages. For
     example:

     ```bash
     # List all available packages
     sdkmanager --list

     # Install platform tools and the SDK for API level 33
     sdkmanager "platform-tools" "platforms;android-33"

     # Accept all licenses
     yes | sdkmanager --licenses
     ```
   - Refer to the specific requirements of the project to determine which packages to install.
4. **Configure for integration tests**:
   - To run integration tests, a `google-services.json` file is required.
   - Place this file in the root of the repository.
5. **Install NDK for specific projects**:
   - Some projects, like `firebase-crashlytics-ndk`, require a specific version of the Android NDK.
     You can install it using `sdkmanager`. For example, to install NDK version 21.4.7075529, you
     would run `sdkmanager "ndk;21.4.7075529"`. Always refer to the project's `README.md` for the
     exact version required.

## Building and Running

The project is built using Gradle. The `gradlew` script is provided in the root directory.

### Building

To build the entire project, you can run the following command:

```bash
./gradlew build
```

To build a specific project, you can run:

```bash
./gradlew :<firebase-project>:build
```

### Running Tests

The project has three types of tests: unit tests, integration tests, and smoke tests.

#### Unit Tests

Unit tests run on the local JVM. They can be executed with the following command:

```bash
./gradlew :<firebase-project>:check
```

#### Running Specific Unit Tests

To run a specific test class or method, use the `--tests` filter with the appropriate test task (usually `testDebugUnitTest` for Android   
library modules, or `test` for Java library modules):

```bash
./gradlew :<firebase-project>:testDebugUnitTest --tests "com.google.firebase.package.ClassName"
./gradlew :<firebase-project>:testDebugUnitTest --tests "com.google.firebase.package.ClassName.methodName"
```

#### Integration Tests

Integration tests run on a hardware device or emulator. Before running integration tests, you need
to add a `google-services.json` file to the root of the project.

To run integration tests on a local emulator, use the following command:

```bash
./gradlew :<firebase-project>:connectedCheck
```

To run integration tests on Firebase Test Lab, use the following command:

```bash
./gradlew :<firebase-project>:deviceCheck
```

### Publishing

To publish a project locally, you can use the following command:

```bash
./gradlew -PprojectsToPublish="<firebase-project>" publishReleasingLibrariesToMavenLocal
```

By default, the `release` variant is published. To publish the `debug` variant instead, use the
`publishDebugVariant` property:

```bash
./gradlew -PprojectsToPublish="<firebase-project>" -PpublishDebugVariant=true publishReleasingLibrariesToMavenLocal
```

Using the `debug` variant has the advantage of including additional logging information. For example,
the `ai-logic/firebase-ai` SDK logs the request/response information when the debug variant is used.

## Development Conventions

### Code Formatting

The project uses Spotless for code formatting. To format the code, run the following command:

```bash
./gradlew spotlessApply
```

To format a specific project, run:

```bash
./gradlew :<firebase-project>:spotlessApply
```

### API Surface

The public API of the Firebase SDKs is managed using a set of annotations:

- `@PublicApi`: Marks APIs that are intended for public consumption by developers.
- `@KeepForSdk`: Marks APIs that are intended for use by other Firebase SDKs. These APIs will
  trigger a linter error if used by developers outside of a Firebase package.
- `@Keep`: Marks APIs that need to be preserved at runtime, usually due to reflection. This
  annotation should be used sparingly as it prevents Proguard from removing or renaming the code.

### Common Patterns

This repository uses a combination of dependency injection frameworks:

- **`firebase-components`**: This is a custom dependency injection framework used for discovery and
  dependency injection between different Firebase SDKs. It allows SDKs to register their components
  and declare dependencies on other components. The initialization is managed by `FirebaseApp`.

- **Dagger**: Dagger is used for internal dependency injection within individual SDKs. This helps to
  create more testable and maintainable code. Dagger components are typically instantiated within
  the `ComponentRegistrar` of an SDK, which allows for the injection of dependencies from
  `firebase-components` into the Dagger graph.

### Proguarding

The project supports Proguarding. Proguard rules are defined in `proguard.txt` files within each
project.

## Code Review

When reviewing code or preparing changes for review, ensure the following requirements are met:

- **No Latin Abbreviations**: Do not use Latin abbreviations (such as `e.g.`, `i.e.`, or `etc.`) in code comments or strings. Use plain English alternatives instead (such as "for example", "that is", or "and so on").
- **API File Updates**: When code modifies the public API, the corresponding `api.txt` file must be updated.
- **Changelog Updates**: When changes to a subproject are non-trivial and user-visible, the corresponding `CHANGELOG.md` file must be updated.
- **API Documentation**: All public APIs must be documented properly using standard doc comments (Javadoc or KDoc).

## External Dependencies

Do not add, under any circumstance, any new dependency to an SDK that does not already exist in the
`gradle/libs.versions.toml`, and even then, only do it if explicitly asked to do so. The Firebase
SDKs are designed to be lightweight, and adding new dependencies can increase the size of the final
artifacts.

## Contributing

Contributions are welcome. Please read the [contribution guidelines](/CONTRIBUTING.md) to get
started.

## Iteration Loop

After you make a change, here's the flow you should follow:

- Format the code using `spotless`. It can be run with:

  ```bash
  ./gradlew :<firebase-project>:spotlessApply
  ```
- Run unit tests:

  ```bash
  ./gradlew :<firebase-project>:check
  ```
- If necessary, run integration tests based on the instructions above.

## Updating this Guide

If new patterns or conventions are discovered, update this guide to ensure it remains a useful
resource.
