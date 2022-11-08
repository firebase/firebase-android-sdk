# Benchmark

This directory contains the benchmark test apps used for measuring latency for
initializing Firebase Android SDKs during app startup.

## Test app configurations

[config.yaml](config.yaml) contains a list of configuration blocks for
building a macrobenchmark test app for each of the Firebase Android SDKs.
If not all of them are required, comment out irrelevant ones for faster build
and test time.

## Run macrobenchmark tests

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

### Run tests locally

1. [Connect an Android device to the computer](https://d.android.com/studio/run/device)

1. Run below command in the repository root directory `firebase-android-sdk`:

   ```shell
   fireci macrobenchmark run --local
   ```

   **Note**: specify `--repeat <number>` to run the test multiple times. Run
   `fireci macrobenchmark run --help` to see more details.

Alternatively, developers can also create test apps with `fireci`, and run the
test from either CLI or Android Studio:

1. Run below command to build all test apps:

   ```shell
   fireci macrobenchmark run --build-only
   ```

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

### Run tests on Firebase Test Lab

Run below command to build and run all tests on FTL:

```shell
fireci macrobenchmark run --remote
```

**Note**: `--repeat <number>` is also supported to submit the test to FTL for
`<number>` times. All tests on FTL will run in parallel.

Alternatively, developers can still build test apps locally, and manually
[run tests on FTL with `gcloud` CLI](https://firebase.google.com/docs/test-lab/android/command-line#running_your_instrumentation_tests).

Aggregated benchmark results are displayed in the log. The log also
contains links to FTL result pages and result files on Google Cloud Storage.

## Analyze macrobenchmark results

Besides results from `*-benchmarkData.json` as descriped above, `fireci`
supports more in depth analysis, such as:

- calculating percentiles and visualizing distributions for one test run
- comparing two sets of results (with stats and graphs) from two different runs

To see more details, run

```shell
fireci macrobenchmark analyze --help
```

### Example usage

1. Analyzing local test results

   ```shell
   fireci macrobenchmark analyze --local-reports-dir <path-to-dir>
   ```

   `<path-to-dir>` is the directory containing the `*-benchmarkData.json` from
   the local test runs.

   **Note**: If the test is started:

   - with `fireci macrobenchmark run --local`, `fireci` copies all benchmark
     json files into a dir, which can be supplied here.
   - manually (CLI or Android Studio), `<path-to-dir>` shall be the directory
     that contains `*-benchmarkData.json` in the gradle build directory.

1. Analyzing remote test results

   ```shell
   fireci macrobenchmark analyze --ftl-results-dir <dir1> --ftl-results-dir <dir2> ...
   ```

   `<dir1>`, `<dir2>` are Firebase Test Lab results directory names, such as
   `2022-11-04_11:18:34.039437_OqZn`.

1. Comparing two sets of result from two different FTL runs

   ```shell
   fireci macrobenchmark analyze \
     --diff-mode \
     --ctl-ftl-results-dir <dir1-from-run1> \
     --ctl-ftl-results-dir <dir2-from-run1> \
     ...
     --exp-ftl-results-dir <dir1-from-run2> \
     --exp-ftl-results-dir <dir2-from-run2> \
     ...
   ```

   `ctl` and `exp` are short for "control group" and "experimental group".

1. Comparing a local test run against a FTL run

   ```shell
   fireci macrobenchmark analyze \
     --diff-mode \
     --ctl-ftl-results-dir <dir1-from-ftl-run> \
     --ctl-ftl-results-dir <dir2-from-ftl-run> \
     ...
     --exp-local-reports-dir <dir-from-local-run>
   ```

## Toolchains

- Gradle 7.5.1
- Android Gradle Plugin 7.2.2
