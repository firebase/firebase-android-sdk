# Benchmark

This directory contains the benchmark test apps used for measuring latency for
different Firebase Android SDKs during app startup.

## Usage

### Prerequisite

- `fireci` CLI tool

  Refer to the [readme](../../ci/fireci/README.md) for how to install it.

- `google-services.json`

  Download it from Firebase project
  [`fireescape-integ-tests`](https://firebase.corp.google.com/u/0/project/fireescape-integ-tests)
  to the directory `firebase-android-sdk/health-metrics/benchmark/template/app`.

### Running benchmark tests locally

1. Under the root directory `firebase-android-sdk`, run

   ```
   fireci macrobenchmark --build-only
   ```

   to create all benchmark test apps based on the [configuration](config.yaml).

1. [Connect an Android device to the computer](https://d.android.com/studio/run/device)

1.
