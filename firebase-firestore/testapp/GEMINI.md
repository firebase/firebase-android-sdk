# Firestore Test App - Gemini Context

This file contains contextual information and useful commands for working with the Firestore test application.

See [README.md](README.md) for a description of this application.

## Critical Instructions for AI Agents

If any files are written to you MUST do the following before declaring a test complete:
1. Compile the code using the "compileDebugSources" Gradle task.
2. Format the code using the "spotlessApply" Gradle task.

## Gradle Commands

This project must be built using the standard Android Gradle command.

The "Gradle wrapper" at `../../gradlew` (or `../../gradlew.bat` on Windows) MUST be used to run Gradle tasks.

When running the Gradle wrapper, always specify `--configure-on-demand` to improve the performance of builds by eliding unnecessary Gradle submodule configuration.

Always use the directory containing this file, the `testapp` directory, as the current directory when running the Gradle wrapper.

* **Compile Code (Fast):** Compiles the debug sources without the overhead of creating an APK.

  ```bash
  ../../gradlew --configure-on-demand compileDebugSources
  ```
* **Format Code:** Formats the codebase using Spotless to ensure compliance with project conventions.

  ```bash
  ../../gradlew --configure-on-demand spotlessApply
  ```

## Known Warnings

* 
* You must IGNORE the warning `Please apply google-services plugin at the bottom of the build file` and not try to fix it.

