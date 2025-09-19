# firebase-dataconnect

This is the Firebase Android Data Connect SDK.

## Building

All Gradle commands should be run from the source root (which is one level up from this folder). See
the README.md in the source root for instructions on publishing/testing Firebase Data Connect.

To build Firebase Data Connect, from the source root run:

```bash
./gradlew :firebase-dataconnect:assembleRelease
```

## Unit testing

To run unit tests for Firebase Data Connect, from the source root run:

```bash
./gradlew :firebase-dataconnect:check
```

## Integration testing

Running integration tests requires a Firebase project because they connect to the Firebase Data
Connect backend.

See [here](../README.md#project-setup) for how to setup a project.

Once you setup the project, download `google-services.json` and place it in the source root.

Make sure you have created a Firebase Data Connect instance for your project, before you proceed.

By default, integration tests run against the Firebase Data Connect emulator.

### Setting up the Firebase Data Connect emulator

The integration tests require that the Firebase Data Connect emulator is running on port 9399, which
is default when running it via the Data Connect Toolkit.

- [Install the Firebase CLI](https://firebase.google.com/docs/cli/).
  ```
  npm install -g firebase-tools
  ```
- [Install the Firebase Data Connect emulator](https://firebase.google.com/docs/FIX_URL/security/test-rules-emulator#install_the_emulator).
  ```
  firebase setup:emulators:dataconnect
  ```
- Run the emulator
  ```
  firebase emulators:start --only dataconnect
  ```
- Select the `Firebase Data Connect Integration Tests (Firebase Data Connect Emulator)` run
  configuration to run all integration tests.

To run the integration tests against prod, select `DataConnectProdIntegrationTest` run
configuration.

### Run on local Android emulator

Then run:

```bash
./gradlew :firebase-dataconnect:connectedCheck
```

### Run on Firebase Test Lab

You can also test on Firebase Test Lab, which allow you to run the integration tests on devices
hosted in a Google data center.

See [here](../README.md#running-integration-tests-on-firebase-test-lab) for instructions of how to
setup Firebase Test Lab for your project.

Run:

```bash
./gradlew :firebase-dataconnect:deviceCheck
```

## Code formatting

Run below to format Kotlin and Java code:

```bash
./gradlew :firebase-dataconnect:spotlessApply
```

See [here](../README.md#code-formatting) if you want to be able to format code from within Android
Studio.

## Build local jar of Firebase Data Connect SDK

```bash
./gradlew -PprojectsToPublish="firebase-dataconnect" publishReleasingLibrariesToMavenLocal
```

Developers may then take a dependency on these locally published versions by adding the
`mavenLocal()` repository to your
[repositories block](https://docs.gradle.org/current/userguide/declaring_repositories.html) in your
app module's build.gradle.

## Misc

After importing the project into Android Studio and building successfully for the first time,
Android Studio will delete the run configuration xml files in `./idea/runConfigurations`. Undo these
changes with the command:

```
$ git checkout .idea/runConfigurations
```
