# firebase-firestore

This is the Cloud Firestore component of the Firebase Android SDK.

Cloud Firestore is a flexible, scalable database for mobile, web, and server development from
Firebase and Google Cloud Platform. Like Firebase Realtime Database, it keeps your data in sync
across client apps through realtime listeners and offers offline support for mobile and web so you
can build responsive apps that work regardless of network latency or Internet connectivity. Cloud
Firestore also offers seamless integration with other Firebase and Google Cloud Platform products,
including Cloud Functions.

> **Note**: All Gradle commands listed below must be run from the **monorepo source root** (one level up from this directory).

## Building

To build the Firestore component release archive:

```bash
./gradlew :firebase-firestore:assembleRelease
```

## Unit Testing

To run unit tests:

```bash
./gradlew :firebase-firestore:check
```

## Integration Testing

Running integration tests requires a Firebase project because they would try to connect to the
Firestore backends.

### 1. Prerequisites

Before running integration tests:

1. **Project Setup**: Ensure you have a Firebase project with a Firestore instance created.
2. **Credentials**: Download your `google-services.json` from the Firebase Console and place it in the `firebase-firestore/` directory.
3. **General Setup**: See the root [README](../README.md#project-setup) for broader project setup instructions.

### 2. Test Configuration Flags
You can configure which backend and database the tests run against using Gradle project properties (`-P`).

| Flag | Description | Options | Default |
| :--- | :--- | :--- | :--- |
| `targetBackend` | Which backend environment to hit. | `"emulator"`, `"qa"`, `"nightly"`, `"prod"` | `"emulator"` |
| `backendEdition` | The database edition to emulate/use. | `"enterprise"`, `"standard"` | *None* |
| `targetDatabaseId` | The specific database ID to test against. | *User defined string* | `(default)` |

### 3. Running against the Firestore Emulator (Default)

By default, integration tests expect the Firestore Emulator to be running on port `8080`.

**Step A: Setup & Start Emulator**

1.  **Install Firebase CLI:**
    ```bash
    npm install -g firebase-tools
    ```
2.  **Initialize Emulator:**
    ```bash
    firebase setup:emulators:firestore
    ```
3.  **Start Emulator:**
    ```bash
    firebase emulators:start --only firestore
    ```

**Step B: Run Tests**

Once the emulator is running, execute the tests on your local Android device/emulator:

```bash
./gradlew :firebase-firestore:connectedCheck
```

### 4. Running against Production/Nightly

To run tests against a live backend (skipping the local emulator), pass the specific target flags.

**Example:** Run against the "nightly" backend using the "enterprise" edition:

```bash
./gradlew :firebase-firestore:connectedCheck \
  -PtargetBackend="nightly" \
  -PbackendEdition="enterprise" \
  -PtargetDatabaseId="enterprise"
```

### 5. Execution Environments

#### Local Android Emulator / Device

The commands above use `connectedCheck`, which runs tests on a locally connected Android device or running Android Virtual Device (AVD).

#### Firebase Test Lab

To run the integration tests on physical devices hosted in the Google data center:

1.  Setup Firebase Test Lab for your project (See [Instructions](../README.md#running-integration-tests-on-firebase-test-lab)).
2.  Run the device check command:

```bash
./gradlew :firebase-firestore:deviceCheck
```

### Testing composite index query against production

#### Setting Up the Environment:

1. Create a `google-services.json` file in the root directory. This file should contain your target
   Firebase project's configuration.
2. If not already logged in, authenticate with your Google Cloud Platform (GCP) account using
   `gcloud auth application-default login`. You can check your logged-in accounts by running
   `gcloud auth list`.
3. Navigate to the `firebase-firestore` directory, create composite indexes by running:

```bash
terraform init
terraform apply -var-file=../google-services.json -auto-approve
```

Note: If the index creation encounters issues, such as concurrent operations, consider running the
index creation process again. Error messages indicating that indexes have already been created can
be safely disregarded.

#### Adding new composite index query tests

1. To create a new composite index for local development, click on the provided link in the test
   error message, which will direct you to the Firebase Console.
2. Add the newly created composite index to the `firestore_index_config.tf` file. The "**name**"
   field is not required to be explicitly added to the file, as the index creation will auto
   complete it on behalf.

## Code Formatting

Run below to format Java code:

```bash
./gradlew :firebase-firestore:spotlessApply
```

See [here](../README.md#code-formatting) if you want to be able to format code from within Android
Studio.

## Using a custom build in your application

To build `firebase-firestore` from source and use the resulting artifact in your Android
application, follow these steps.

### 1. Set a custom version

In `firebase-firestore/gradle.properties`, change the `version` to a unique value. Appending a
suffix makes it easy to identify your custom build.

For example, change:

```
version=26.0.2
latestReleasedVersion=26.0.1
```

To:

```
version=99.99.99-MyFix1
latestReleasedVersion=26.0.1
```

### 2. Build the artifact

Build and publish the artifact to your local Maven repository:

```bash
./gradlew :firebase-firestore:publishToMavenLocal
```

### 3. Update your app's repositories

In your application's `settings.gradle` or `settings.gradle.kts` file, add `mavenLocal()` to the
`repositories` block within `dependencyResolutionManagement`.

```kotlin
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    mavenLocal() // Add this line
  }
}
```

### 4. Update your app's dependencies

In your application's `build.gradle` or `build.gradle.kts` file, update the `firebase-firestore`
dependency to use the custom version you set in step 1.

```kotlin
dependencies {
  implementation(platform("com.google.firebase:firebase-bom:34.3.0"))
  // Use the custom version from gradle.properties
  implementation("com.google.firebase:firebase-firestore:99.99.99-MyFix1")
  // ... other dependencies
}
```

### Optional: Verify the version at runtime

To confirm that your application is using the custom artifact, you can log its version. Add the
following code to your application:

```kotlin
android.util.Log.i("FirestoreVersion", com.google.firebase.firestore.BuildConfig.VERSION_NAME)
```

### Building with local module dependencies

If your changes require building other modules in this repository (like `firebase-common`), you must
build and publish them locally as well.

1. In the dependency's directory (e.g., `firebase-common/`), edit `gradle.properties` to set a
   unique version.
2. Publish the dependency to Maven Local:

   ```bash
   ./gradlew :firebase-common:publishToMavenLocal
   ```
3. In `firebase-firestore/firebase-firestore.gradle`, ensure the dependency is a project
   dependency. For example, change `api(libs.firebase.common)` to
   `api(project(":firebase-common"))`.
4. Build and publish the `firebase-firestore` artifact as described in step 2.

## Misc

After importing the project into Android Studio and building successfully for the first time,
Android Studio will delete the run configuration xml files in `./idea/runConfigurations`. Undo these
changes with the command:

```
$ git checkout .idea/runConfigurations
```

