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

## Build firebase-firestore for use in your application.

It is possible, and, indeed, quite easy, to compile the `firebase-firestore` Gradle artifact from
source and then use that self-compiled artifact in your Android application.

### Update gradle.properties with a custom version number

First, edit `firebase-firestore/gradle.properties` and change the "version" to something unique. It
can be especially helpful to append a "-something" suffix to the version name, replacing "something"
with some description of your change. Doing so will give you confidence that you are indeed using
your self-compiled version in your application and not accidentally using an officially-published
version.

For example, you can change the contents of `firebase-firestore/gradle.properties` from

```
version=26.0.2
latestReleasedVersion=26.0.1
```

to

```
version=99.99.99-MyFix1
latestReleasedVersion=26.0.1
```

### Build the `firebase-firestore` Gradle artifact

Then, build the `firebase-firestore` Gradle artifact by running:

```bash
./gradlew :firebase-firestore:publishToMavenLocal
```

### Add mavenLocal() repository to your Android app

In order to take a dependency on the self-compiled Gradle `firebase-firestore` artifact, first add
`mavenLocal()` to the
[`dependencyResolutionManagement.repositories`](https://docs.gradle.org/current/userguide/declaring_repositories.html)
section of your Android application's `settings.gradle` or `settings.gradle.kts` file.

For example, you would change something like

```kotlin
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
  }
}
```

to

```kotlin
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    mavenLocal() // Add this line to use the artifacts published by `publishToMavenLocal`
  }
}
```

### Change the firebase-firestore dependency in your Android app

Then, edit your Android application's `build.gradle` or `build.gradle.kts` to use the self-compiled
Gradle `firebase-firestore` artifact.

For example, you would change something like

```kotlin
dependencies {
  implementation(platform("com.google.firebase:firebase-bom:34.3.0"))
  implementation("com.google.firebase:firebase-firestore")
  // ... the rest of your application's dependencies
}
```

to

```kotlin
dependencies {
  implementation(platform("com.google.firebase:firebase-bom:34.3.0"))
  // Use the custom version you set in gradle.properties in the next line.
  implementation("com.google.firebase:firebase-firestore:99.99.99-MyFix1")
  // ... the rest of your application's dependencies
}
```

### (Optional) Log the firebase-firestore version to logcat

It can be helpful to see in the logs which exact version of the `firebase-firestore` Gradle artifact
your application is using. This increases the confidence that your application is indeed using the
custom, self-compiled artifact rather than an official artifact.

To do this, simply add a line like the following somewhere in your Android application:

```kotlin
android.util.Log.i("FirestoreVersion", com.google.firebase.firestore.BuildConfig.VERSION_NAME)
```

### (Rarely required) Self-compile dependencies of firebase-firestore

The `firebase-firestore` Gradle artifact includes dependencies on a few peer modules in the
`firebase-android-sdk` repository. Although rare, sometimes you want, or need, to include local
changes to these dependencies in your self-compiled build.

At the time of writing, the peer dependencies of `firebase-firestore` include:

- `firebase-common`
- `firebase-components`
- `firebase-database-collection`
- `protolite-well-known-types`

For purposes of example, suppose there is a local change to `firebase-common` that you want your
self-compiled `firebase-firestore` artifact to pick up. Follow the steps below to do this, adapting
the steps as appropriate if your specific case uses a _different_ dependency.

1. Edit `firebase-common/gradle.properties`, changing the verson to something like `99.99.99`.
2. Compile and publish the `firebase-common` Gradle artifact by running:
   `./gradlew :firebase-common:publishToMavenLocal`
3. Edit `firebase-firestore/firebase-firestore.gradle` to use a _project_ dependency on the
   artifact. For example, change `api(libs.firebase.common)` to `api(project(":firebase-common"))`
4. Compile and publish the `firebase-firestore` Gradle artifact as documented above, namely, by
   running `./gradlew :firebase-firestore:publishToMavenLocal`

## Misc

After importing the project into Android Studio and building successfully for the first time,
Android Studio will delete the run configuration xml files in `./idea/runConfigurations`. Undo these
changes with the command:

```
$ git checkout .idea/runConfigurations
```
