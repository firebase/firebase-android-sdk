# Benchmark

This directory contains the benchmark test apps used for measuring latency for
initializing Firebase Android SDKs during app startup.

## Test app configurations

[config.yaml](config.yaml) contains a list of configuration blocks for
building a macrobenchmark test app for each of the Firebase Android SDKs.
If not all of them are required, comment out irrelevant ones for faster build
and test time.

## Run benchmark tests

### Prerequisite

1. `fireci` CLI tool

   Refer to its [readme](../../ci/fireci/README.md) for how to install it.

1. `google-services.json`

   Download it from the Firebase project
   [`fireescape-integ-tests`](https://firebase.corp.google.com/u/0/project/fireescape-integ-tests)
   to the directory `./template/app`.

1. Authentication to Google Cloud

   Authentication is required by Google Cloud SDK and Google Cloud Storage
   client library used in the benchmark tests.

   One simple way is to configure it is to set an environment variable
   `GOOGLE_APPLICATION_CREDENTIALS` to a service account key file. However,
   please refer to the official Google Cloud
   [doc](https://cloud.google.com/docs/authentication) for full guidance on
   authentication.

### Run benchmark tests locally

1. Build all test apps by running below command in the root
   directory `firebase-android-sdk`:

   ```shell
   fireci macrobenchmark --build-only
   ```

1. [Connect an Android device to the computer](https://d.android.com/studio/run/device)

1. Locate the temporary test apps directory from the log, for example:

   - on linux: `/tmp/benchmark-test-*/`
   - on macos: `/var/folders/**/benchmark-test-*/`

1. Start the benchmark tests from CLI or Android Studio:

   - CLI

     Run below command in the above test app project directory

     ```
     ./gradlew :macrobenchmark:connectedCheck
     ```

   - Android Studio

     1. Import the project (e.g. `**/benchmark-test-*/firestore`) into Android Studio
     1. Start the benchmark test by clicking gutter icons in the file `BenchmarkTest.kt`

1. Inspect the benchmark test results:

   - CLI

     Result files are created in `<test-app-dir>/macrobenchmark/build/outputs/`:

     - `*-benchmarkData.json` contains metric aggregates
     - `*.perfetto-trace` are the raw trace files

     Additionally, upload `.perfetto-trace` files to
     [Perfetto Trace Viewer](https://ui.perfetto.dev/) to visualize all traces.

   - Android Studio

     Test results are displayed directly in the "Run" tool window, including

     - macrobenchmark built-in metrics
     - duration of custom traces
     - links to trace files that can be visualized within the IDE

     Alternatively, same set of result files are produced at the same output
     location as invoking tests from CLI, which can be used for inspection.

### Run benchmark tests on Firebase Test Lab

Build and run all tests on FTL by running below command in the root
directory `firebase-android-sdk`:

```
fireci macrobenchmark
```

Alternatively, it is possible to build all test apps via steps described in
[Running benchmark tests locally](#running-benchmark-tests-locally)
and manually
[run tests on FTL with `gcloud` CLI ](https://firebase.google.com/docs/test-lab/android/command-line#running_your_instrumentation_tests).

Aggregated benchmark results are displayed in the log. The log also
contains links to FTL result pages and result files on Google Cloud Storage.

## Toolchains

- Gradle 7.5.1
- Android Gradle Plugin 7.2.2
