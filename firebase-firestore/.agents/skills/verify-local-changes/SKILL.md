---
name: Verify Local Changes
description: Verifies local Firebase Android SDK changes.
---

# Verify Local Changes (Android)

This skill documents how to verify local code changes for the Firebase Android SDK before opening a Pull Request to ensure all GitHub CI checks pass.

All commands should be executed from the root of the `firebase-android-sdk` directory.

---

## Prerequisites

- **Java Development Kit (JDK):** Ensure you are using **JDK 17**.
- **Android SDK / Platform Tools:** Required for integration tests running on devices/emulators.

---

## Step 0: Format Code & License Headers

Run the Spotless auto-formatter to ensure code style and licensing guidelines pass the CI checks.

1. **Auto-format Code:**
   ```bash
   ./gradlew :firebase-firestore:spotlessApply
   ```

2. **Verify Format Compliance:**
   ```bash
   ./gradlew :firebase-firestore:spotlessCheck
   ```

3. **Verify Copyright Headers:**
   Ensure all files have valid copyright headers using `fireci`:
   ```bash
   # Install fireci locally if not already installed
   pip3 install -e "ci/fireci"
   
   # Run copyright check
   fireci copyright_check -e py -e gradle -e java -e kt -e groovy -e sh -e proto
   ```

---

## Step 1: Unit Testing & Static Analysis (ErrorProne)

Run unit tests and compile-time checks using ErrorProne to catch potential bugs (e.g., resource leaks, bad API patterns, invalid annotations).

```bash
./gradlew :firebase-firestore:check withErrorProne
```

> [!TIP]
> `withErrorProne` is a marker task that applies the `net.ltgt.errorprone` plugin. Always include it to match CI behavior.

---

## Step 2: Public API Compatibility (SemVer Check)

If you made any changes to the public API surface (classes, methods, parameters, fields), you must ensure they do not break semantic versioning.

```bash
./gradlew metalavaSemver
```

---

## Step 3: Integration / System Testing (Emulator)

The standard and fastest way to run system tests locally.

1. **Start the Firestore Emulator:**
   ```bash
   gcloud emulators firestore start --host-port=127.0.0.1:8080
   ```

2. **Run the System Tests targeting the emulator:**
   ```bash
   ./gradlew :firebase-firestore:connectedCheck withErrorProne -PtargetBackend="emulator" -PbackendEdition="enterprise"
   ```
   > [!NOTE]
   > Running integration tests requires an active/connected Android Emulator or physical Android device.

---

## Step 4: Integration / System Testing (Production/Nightly)

To run tests against standard production/nightly backends (requires GCP service account and project configurations):

* **Standard Production DB:**
  ```bash
  ./gradlew :firebase-firestore:connectedCheck withErrorProne -PtargetBackend="prod" -PbackendEdition="standard" -PtargetDatabaseId="(default)"
  ```

* **Enterprise Named DB:**
  ```bash
  ./gradlew :firebase-firestore:connectedCheck withErrorProne -PtargetBackend="prod" -PbackendEdition="enterprise" -PtargetDatabaseId="enterprise"
  ```

* **Nightly Backend:**
  ```bash
  ./gradlew :firebase-firestore:connectedCheck withErrorProne -PtargetBackend="nightly" -PbackendEdition="enterprise" -PtargetDatabaseId="enterprise"
  ```

---

## Pro Tips

> [!TIP]
> **Targeting Specific Test Classes:**
> To avoid waiting for the entire integration test suite to run, you can specify a single test class or test method using the instrumentation argument:
> ```bash
> ./gradlew :firebase-firestore:connectedCheck \
>   -Pandroid.testInstrumentationRunnerArguments.class=com.google.firebase.firestore.NumericTransformsTest
> ```
> This is the preferred approach to testing changes that have a narrow scope and when testing agains production or nightly, because integration tests are slow.
