# Benchmark

This directory contains the benchmark test apps used for measuring latency for
different Firebase Android SDKs during app startup.

## Usage

### Prerequisite

1. `fireci` CLI tool

   Refer to the [readme](../../ci/fireci/README.md) for how to install it.

1. `google-services.json`

   Download it from Firebase project
   [`fireescape-integ-tests`](https://firebase.corp.google.com/u/0/project/fireescape-integ-tests)
   to the directory `firebase-android-sdk/health-metrics/benchmark/template/app`.

1. Authentication to Google Cloud

   Authentication is required by Google Cloud SDK and Cloud Storage client
   library used in the benchmark tests.

   One simple way is to configure it is to set an environment variable
   `GOOGLE_APPLICATION_CREDENTIALS` to a service account key file. However,
   please refer to the official Google Cloud
   [doc](https://cloud.google.com/docs/authentication) for full guidance on
   authentication.

### Running benchmark tests locally

1. Build all test apps by running below command in the root
   directory `firebase-android-sdk`:

   ```shell
   fireci macrobenchmark --build-only
   ```

1. [Connect an Android device to the computer](https://d.android.com/studio/run/device)

1. Locate the temporary test apps directory from the log, for example:

   - on linux: `/tmp/benchmark-test-run-*/`
   - on macos: `/var/folders/**/benchmark-test-run-*/`

1. Start the benchmark tests from Android Studio or CLI:

   - Android Studio

     1. Import the project (e.g. `**/benchmark-test-run-*/firestore`) into Android Studio
     1. Start the benchmark test by clicking gutter icon in the file `BenchmarkTest.kt`

   - CLI

     1. Run below command in the test app project directory

        ```
        ../gradlew :macrobenchmark:connectedCheck
        ```

### Running benchmark tests on Firebase Test Lab

Build and run all tests on FTL by running below command in the root
directory `firebase-android-sdk`:

```
fireci macrobenchmark
```

### Examining benchmark test results
