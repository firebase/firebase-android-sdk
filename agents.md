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

1. **Install Android SDK Command-Line Tools**:
   - If not already installed, download the command-line tools from the
     [Android Studio page](https://developer.android.com/studio#command-line-tools-only).
   - Create a directory for the Android SDK, e.g., `android_sdk`.
   - Unzip the downloaded package. This will create a `cmdline-tools` directory. Move this
     directory to `android_sdk/cmdline-tools/latest`.
   - The final structure should be `android_sdk/cmdline-tools/latest/`.
2. **Install required SDK packages**:
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
3. **Configure for integration tests**:
   - To run integration tests, a `google-services.json` file is required.
   - Place this file in the root of the repository.
4. **Install NDK for specific projects**:
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

## External Dependencies

Do not add, under any circunstance, any new dependency to a SDK that does not already exists in the
`gradle/libs.versions.toml`, and even then, only do it if cxexplicitly asked to do so. The Firebase
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

## Firebase ai specific knowledge

## Overview

The Firebase Android AI Logic SDK is designed to support both cloud-hosted and on-device Large Language Models (LLMs). It utilizes a hybrid routing strategy to determine whether to use cloud or local inference based on connectivity, performance, and feature requirements.

## Subprojects

### 1. `firebase-ai`

*
**Role**: The primary SDK for cloud-based AI support.


* **Responsibilities**:
* Implements cloud support for AI features.


* Acts as the default entry point for "Cloud-only" inference modes.


* Maintains a `minSdk` of 23.




*
**Key Behavior**: Interfaces with state-of-the-art cloud-hosted models to provide the highest accuracy and performance.



### 2. `firebase-ai-ondevice`

*
**Role**: A user-visible SDK that provides on-device model logic.


* **Responsibilities**:
* Implements the logic required for on-device inference using the MLKit GenAI prompt SDK.


* Exposes `OnDeviceConfiguration` to allow developers to customize local model behavior.


* Manages "On-device only" and "Preferred" routing strategies (e.g., falling back to cloud if the device is offline or the request is too complex).




*
**Requirements**: Due to the hard dependency on MLKit, this artifact requires a `minSdk` of 26.



### 3. `firebase-ai-ondevice-interop`

*
**Role**: A non-user-visible internal artifact that facilitates communication between the cloud and on-device SDKs.


* **Responsibilities**:
* Enables a "soft dependency" between `firebase-ai` and `firebase-ai-ondevice` using the Firebase components system.


* Exposes a restricted API subset (strict subset of MLKit) to allow the regular SDK to interact with on-device features without requiring a direct dependency on MLKit.


* Provides methods for `firebase-ai` to check if an on-device model is available on the current hardware.





## Implementation Details

### Dependency Management

To prevent impacting cloud-only users with the larger footprint and higher `minSdk` requirements of on-device models, developers must explicitly add `firebase-ai-ondevice` to their build files:

```gradle
dependencies {
    implementation("com.google.firebase:firebase-ai:17.6.0")
    implementation("com.google.firebase:firebase-ai-ondevice:16.0.0-beta01")
}

```

### Hybrid Routing Strategies

The SDKs work together to support four main inference modes:

*
**Cloud-only**: Default mode using `firebase-ai`.


*
**On-device only**: Routes all queries through `firebase-ai-ondevice`.


*
**Cloud Preferred**: Uses cloud unless the device is offline.


*
**On-device Preferred**: Uses local inference unless the prompt exceeds token limits or contains unsupported features (e.g., multiple images).

## Updating this Guide

If new patterns or conventions are discovered, update this guide to ensure it remains a useful
resource.
