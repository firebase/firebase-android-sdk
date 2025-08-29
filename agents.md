# Agents

This guide provides essential information for working within the `firebase-android-sdk` repository.

## Environment Setup

To work with this repository, the Android SDK must be installed. Use the `sdkmanager` command-line
tool for this purpose.

1.  **Install Android SDK Command-Line Tools**:

    - If not already installed, download the command-line tools from the
      [Android Studio page](https://developer.android.com/studio#command-line-tools-only).
    - Create a directory for the Android SDK, e.g., `android_sdk`.
    - Unzip the downloaded package. This will create a `cmdline-tools` directory. Move this
      directory to `android_sdk/cmdline-tools/latest`.
    - The final structure should be `android_sdk/cmdline-tools/latest/`.

2.  **Install required SDK packages**:

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

3.  **Configure for integration tests**:

    - To run integration tests, a `google-services.json` file is required.
    - Place this file in the root of the repository.

4.  **Install NDK for specific projects**:
    - Some projects, like `firebase-crashlytics-ndk`, require a specific version of the Android NDK.
      You can install it using `sdkmanager`. For example, to install NDK version 21.4.7075529, you
      would run `sdkmanager "ndk;21.4.7075529"`. Always refer to the project's `README.md` for the
      exact version required.

---

## Testing

This repository uses two main types of tests:

1.  **Unit Tests**:

    - These tests run on the local JVM.
    - To execute unit tests for a specific project, run:
      ```bash
      ./gradlew :<firebase-project>:check
      ```

2.  **Integration Tests**:
    - These tests run on a hardware device or emulator.
    - Ensure a `google-services.json` file is present in the repository root.
    - To execute integration tests for a specific project, run:
      ```bash
      ./gradlew :<firebase-project>:connectedCheck
      ```

---

## API Surface

The public API of the Firebase SDKs is managed using a set of annotations:

- `@PublicApi`: Marks APIs that are intended for public consumption by developers.
- `@KeepForSdk`: Marks APIs that are intended for use by other Firebase SDKs. These APIs will
  trigger a linter error if used by developers outside of a Firebase package.
- `@Keep`: Marks APIs that need to be preserved at runtime, usually due to reflection. This
  annotation should be used sparingly as it prevents Proguard from removing or renaming the code.

---

## Best Practices

- **Code Formatting**: The repository uses `spotless` for code formatting. To format the code in a
  specific project, run:
  ```bash
  ./gradlew :<firebase-project>:spotlessApply
  ```
- **Dependency Management**: Dependencies are managed using Gradle. Be mindful of the impact of new
  dependencies on the size of the SDKs.

---

## Common Patterns

This repository uses a combination of dependency injection frameworks:

- **`firebase-components`**: This is a custom dependency injection framework used for discovery and
  dependency injection between different Firebase SDKs. It allows SDKs to register their components
  and declare dependencies on other components. The initialization is managed by `FirebaseApp`.

- **Dagger**: Dagger is used for internal dependency injection within individual SDKs. This helps to
  create more testable and maintainable code. Dagger components are typically instantiated within
  the `ComponentRegistrar` of an SDK, which allows for the injection of dependencies from
  `firebase-components` into the Dagger graph.

---

## External Dependencies

Do not add new external dependencies to the project unless explicitly asked to do so. The Firebase
SDKs are designed to be lightweight, and adding new dependencies can increase the size of the final
artifacts.

---

## Updating this Guide

If new patterns or conventions are discovered, update this guide to ensure it remains a useful
resource.
