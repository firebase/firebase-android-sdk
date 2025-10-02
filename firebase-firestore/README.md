# firebase-firestore

This is the Cloud Firestore component of the Firebase Android SDK.

Cloud Firestore is a flexible, scalable database for mobile, web, and server development from
Firebase and Google Cloud Platform. Like Firebase Realtime Database, it keeps your data in sync
across client apps through realtime listeners and offers offline support for mobile and web so you
can build responsive apps that work regardless of network latency or Internet connectivity. Cloud
Firestore also offers seamless integration with other Firebase and Google Cloud Platform products,
including Cloud Functions.

## Building

All Gradle commands should be run from the source root (which is one level up from this folder). See
the README.md in the source root for instructions on publishing/testing Cloud Firestore.

To build Cloud Firestore, from the source root run:

```bash
./gradlew :firebase-firestore:assembleRelease
```

## Unit Testing

To run unit tests for Cloud Firestore, from the source root run:

```bash
./gradlew :firebase-firestore:check
```

## Integration Testing

Running integration tests requires a Firebase project because they would try to connect to the
Firestore backends.

See [here](../README.md#project-setup) for how to setup a project.

Once you setup the project, download `google-services.json` and place it in the source root.

Make sure you have created a Firestore instance for your project, before you proceed.

By default, integration tests run against the Firestore emulator.

### Setting up the Firestore Emulator

The integration tests require that the Firestore emulator is running on port 8080, which is default
when running it via CLI.

- [Install the Firebase CLI](https://firebase.google.com/docs/cli/).
  ```
  npm install -g firebase-tools
  ```
- [Install the Firestore emulator](https://firebase.google.com/docs/firestore/security/test-rules-emulator#install_the_emulator).
  ```
  firebase setup:emulators:firestore
  ```
- Run the emulator
  ```
  firebase emulators:start --only firestore
  ```
- Select the `Firestore Integration Tests (Firestore Emulator)` run configuration to run all
  integration tests.

To run the integration tests against prod, select `FirestoreProdIntegrationTest` run configuration.

### Run on Local Android Emulator

Then simply run:

```bash
./gradlew :firebase-firestore:connectedCheck
```

### Run on Firebase Test Lab

You can also test on Firebase Test Lab, which allow you to run the integration tests on devices
hosted in Google data center.

See [here](../README.md#running-integration-tests-on-firebase-test-lab) for instructions of how to
setup Firebase Test Lab for your project.

Run:

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

```
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

1.  In the dependency's directory (e.g., `firebase-common/`), edit `gradle.properties` to set a
    unique version.
2.  Publish the dependency to Maven Local:
    ```bash
    ./gradlew :firebase-common:publishToMavenLocal
    ```
3.  In `firebase-firestore/firebase-firestore.gradle`, ensure the dependency is a project
    dependency. For example, change `api(libs.firebase.common)` to
    `api(project(":firebase-common"))`.
4.  Build and publish the `firebase-firestore` artifact as described in step 2.

## Misc

After importing the project into Android Studio and building successfully for the first time,
Android Studio will delete the run configuration xml files in `./idea/runConfigurations`. Undo these
changes with the command:

```
$ git checkout .idea/runConfigurations
```
