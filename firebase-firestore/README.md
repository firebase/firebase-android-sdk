# firebase-firestore

This is the Cloud Firestore component of the Firebase Android SDK.

Cloud Firestore is a flexible, scalable database for mobile, web, and server
development from Firebase and Google Cloud Platform. Like Firebase Realtime
Database, it keeps your data in sync across client apps through realtime
listeners and offers offline support for mobile and web so you can build
responsive apps that work regardless of network latency or Internet
connectivity. Cloud Firestore also offers seamless integration with other
Firebase and Google Cloud Platform products, including Cloud Functions.

## Building

All Gradle commands should be run from the source root (which is one level up
from this folder). See the README.md in the source root for instructions on
publishing/testing Cloud Firestore.

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

Running integration tests requires a Firebase project because they would try
to connect to the Firestore backends.

See [here](../README.md#project-setup) for how to setup a project.

Once you setup the project, download `google-services.json` and place it in
the source root.

Make sure you have created a Firestore instance for your project, before
you proceed.

By default, integration tests run against the Firestore emulator.

### Setting up the Firestore Emulator

The integration tests require that the Firestore emulator is running on port
8080, which is default when running it via CLI.

  * [Install the Firebase CLI](https://firebase.google.com/docs/cli/).
    ```
    npm install -g firebase-tools
    ```
  * [Install the Firestore
    emulator](https://firebase.google.com/docs/firestore/security/test-rules-emulator#install_the_emulator).
    ```
    firebase setup:emulators:firestore
    ```
  * Run the emulator
    ```
    firebase emulators:start --only firestore
    ```
  * Select the `Firestore Integration Tests (Firestore Emulator)` run
    configuration to run all integration tests.

To run the integration tests against prod, select `FirestoreProdIntegrationTest`
run configuration.

### Run on Local Android Emulator

Then simply run:
```bash
./gradlew :firebase-firestore:connectedCheck
```

### Run on Firebase Test Lab

You can also test on Firebase Test Lab, which allow you to run the integration
tests on devices hosted in Google data center.

See [here](../README.md#running-integration-tests-on-firebase-test-lab) for
instructions of how to setup Firebase Test Lab for your project.

Run:
```bash
./gradlew :firebase-firestore:deviceCheck
```

## Code Formatting

Run below to format Java code:
```bash
./gradlew :firebase-firestore:googleJavaFormat
```

See [here](../README.md#code-formatting) if you want to be able to format code
from within Android Studio.

## Build Local Jar of Firestore SDK

Run:
```bash
./gradlew publishAllToLocal
```

This will publish firebase SDK at SNAPSHOT versions. All pom level dependencies
within the published artifacts will also point to SNAPSHOT versions that are
co-published. The results will be built into your local maven repo.

Developers may then take a dependency on these locally published versions by adding
the `mavenLocal()` repository to your [repositories
block](https://docs.gradle.org/current/userguide/declaring_repositories.html) in
your app module's build.gradle.

## Misc
After importing the project into Android Studio and building successfully
for the first time, Android Studio will delete the run configuration xml files
in `./idea/runConfigurations`. Undo these changes with the command:

```
$ git checkout .idea/runConfigurations
```
