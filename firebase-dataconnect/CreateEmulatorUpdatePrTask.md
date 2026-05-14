# CreateEmulatorUpdatePrTask

## Overview

The `CreateEmulatorUpdatePrTask` is a custom Gradle task within the `firebase-dataconnect` module. Its purpose is to automate the routine, multi-step process of updating the Data Connect emulator versions and the `firebase-tools` dependencies used in GitHub Actions.

When executed, it performs the following lifecycle:
1. Fetches the latest Data Connect emulator executable versions from Google Cloud Storage (GCS).
2. Updates the local registry JSON file (`DataConnectExecutableVersions.json`).
3. Fetches the latest release version of the `Firebase/firebase-tools` repository using the GitHub CLI (`gh`).
4. Updates the version numbers in the CI workflow YAML files.
5. Automatically creates a local Git branch, commits the changes, pushes to the remote origin, and opens a GitHub Pull Request with the correct labels and formatting.

## Context and Design Decisions

Historically, this update process was done manually or semi-manually using a combination of Zsh scripts (`./scripts/update_versions_json.zsh`), shell commands, and manual Git/GitHub interactions. 

When automating this process end-to-end, several approaches were considered, including standalone Zsh scripts, Python scripts, or GitHub Actions. The decision to use a **Custom Kotlin Gradle Task** was made for the following reasons:

### 1. Reusability of Existing Code
The repository already contained robust logic for parsing the JSON registry and fetching binaries from GCS within `UpdateDataConnectExecutableVersionsTask.kt` and `DataConnectExecutableVersionRegistry.kt`. By writing the automation as a Gradle task in the same module, we could directly reuse these Kotlin classes (and their underlying `kotlinx.serialization` configurations) without needing to reimplement JSON parsing using `jq`, `sed`, or an external Python script.

### 2. Avoidance of Nested Processes
An initial thought was to have the automation script simply call the existing Gradle task (`./gradlew updateJson`) via a shell command. However, this spins up a nested Gradle Daemon process, which is slow and resource-intensive. By refactoring the update logic into an `internal companion object`, the `CreateEmulatorUpdatePrTask` can execute the update logic entirely in-memory within the host Gradle process.

### 3. Cross-Platform Reliability
Shell scripts relying heavily on `sed` for inline file replacements can be brittle due to differences between GNU `sed` (Linux) and BSD `sed` (macOS). Using Kotlin's native `java.io.File` and `Regex` APIs ensures the YAML file modifications work consistently regardless of the developer's operating system.

### 4. Ecosystem Alignment
Because the task is written in Kotlin and executed via the Gradle Wrapper, developers do not need to install additional scripting environments (like a standalone Kotlin compiler or specific Python versions). The only external requirement is that the developer has the standard `git` and `gh` (GitHub CLI) tools installed and authenticated on their `PATH`.

## Implementation Details

### Refactoring `UpdateDataConnectExecutableVersionsTask.kt`
To allow code reuse without nested execution, `UpdateDataConnectExecutableVersionsTask` was refactored. The core logic that iterates over the GCS bucket and compares it against the JSON registry was moved into `internal companion object { fun updateRegistry(...) }`. Furthermore, the `CloudStorageVersionInfo` data class was promoted from `private` to `internal` so it could be utilized by the companion object.

### The Task Class (`CreateEmulatorUpdatePrTask.kt`)
The task is defined in `gradleplugin/plugin/src/main/kotlin/com/google/firebase/dataconnect/gradle/plugin/CreateEmulatorUpdatePrTask.kt`.

Key operational steps in the `@TaskAction`:
*   **JSON Handling:** It uses `DataConnectExecutableVersionsRegistry.load()` and `save()` to manage the JSON state. It compares the `oldDefault` and `newDefault` versions to determine if an update is actually required, cleanly aborting if no new emulator versions exist.
*   **Git Automation:** It uses Gradle's `execOperations.exec { ... }` API to safely execute shell commands. This is preferred over standard Kotlin `Runtime.getRuntime().exec()` because Gradle handles standard output logging, error stream redirection, and exception throwing automatically.
*   **YAML Updates:** It reads `.github/workflows/dataconnect.yml` and `.github/workflows/dataconnect_demo_app.yml`, using a regular expression (`FDC_FIREBASE_TOOLS_VERSION:\s*\$\{\{\s*inputs\.firebaseToolsVersion\s*\|\|\s*'([^']+)'\s*\}\}`) to dynamically find the *old* `firebase-tools` version and replace it with the new one fetched via `gh release view`.

### Registration
The task is registered in the module's build script: `firebase-dataconnect.gradle.kts`.

```kotlin
tasks.register<com.google.firebase.dataconnect.gradle.plugin.CreateEmulatorUpdatePrTask>("createEmulatorUpdatePr") {
  jsonFile.set(
    file("gradleplugin/plugin/src/main/resources/${DataConnectExecutableVersionsRegistry.PATH}")
  )
  workDirectory.set(project.layout.buildDirectory.dir("updateJson"))
}
```

## Usage

To execute the automation, ensure you have `git` and `gh` installed, and run:

```bash
./gradlew :firebase-dataconnect:createEmulatorUpdatePr
```

If any shell command (like `git push`) fails, the Gradle task will throw an exception and abort. No automated rollback is performed; the developer is expected to manually clean up their local Git state if an error occurs mid-flight.
