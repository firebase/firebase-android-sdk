# firebase-dataconnect

This is the Firebase Data Connect component of the Firebase Android SDK.

## Building

All Gradle commands should be run from the source root (which is one level up
from this folder). See the README.md in the source root for instructions on
publishing/testing Firebase Data Connect.

To build Firebase Data Connect, from the source root run:
```bash
./gradlew :firebase-dataconnect:assembleRelease
```

## Unit Testing

To run unit tests for Firebase Data Connect, from the source root run:
```bash
./gradlew :firebase-dataconnect:check
```

## Integration Testing

Running integration tests requires a Firebase project because they would try
to connect to the Firebase Data Connect backends.

See [here](../README.md#project-setup) for how to setup a project.

Once you setup the project, download `google-services.json` and place it in
the source root.

Make sure you have created a Firebase Data Connect instance for your project,
before you proceed.

By default, integration tests run against the Firebase Data Connect emulator.

### Setting up the Firebase Data Connect Emulator

The integration tests require that the Firebase Data Connect emulator is running
on port NNNN (TODO: fill in correct value), which is default when running it via
CLI.

  * [Install the Firebase CLI](https://firebase.google.com/docs/cli/).
    ```
    npm install -g firebase-tools
    ```
  * [Install the Firebase Data Connect
    emulator](https://firebase.google.com/docs/FIX_URL/security/test-rules-emulator#install_the_emulator).
    ```
    firebase setup:emulators:dataconnect
    ```
  * Run the emulator
    ```
    firebase emulators:start --only dataconnect
    ```
  * Select the `Firebase Data Connect Integration Tests (Firebase Data Connect
    Emulator)` run configuration to run all integration tests.

To run the integration tests against prod, select
`DataConnectProdIntegrationTest` run configuration.

### Run on Local Android Emulator

Then simply run:
```bash
./gradlew :firebase-dataconnect:connectedCheck
```

### Run on Firebase Test Lab

You can also test on Firebase Test Lab, which allow you to run the integration
tests on devices hosted in Google data center.

See [here](../README.md#running-integration-tests-on-firebase-test-lab) for
instructions of how to setup Firebase Test Lab for your project.

Run:
```bash
./gradlew :firebase-dataconnect:deviceCheck
```

## Code Formatting

Run below to format Kotlin code:
```bash
./gradlew :firebase-dataconnect:ktfmtFormat
```

Run below to format Java code:
```bash
./gradlew :firebase-dataconnect:googleJavaFormat
```

See [here](../README.md#code-formatting) if you want to be able to format code
from within Android Studio.

## Build Local Jar of Firebase Data Connect SDK

```bash
./gradlew -PprojectsToPublish="firebase-dataconnect" publishReleasingLibrariesToMavenLocal
```

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
