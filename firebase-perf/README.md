# Firebase Performance Monitoring Development Workflow
[Firebase Performance Monitoring](https://firebase.google.com/docs/perf-mon/get-started-android) is 
a free mobile app performance analytics service that helps you to gain insight into the performance 
characteristics of your app. 

For more information about app performance and many other cool mobile services, check out [Firebase](https://firebase.google.com/).

## Clone the repository

Follow the instruction in the [Root README](https://github.com/firebase/firebase-android-sdk#getting-started)
to clone the repository.

## Local Environment Setup

Add the following lines to your `~/.bash_profile`:

For Mac:
```
export ANDROID_HOME=~/Library/Android/sdk
export ANDROID_SDK_ROOT=~/Library/Android/sdk
```

For Linux:
```
export ANDROID_HOME=~/Android/Sdk
export ANDROID_SDK_ROOT=~/Android/Sdk
```

## Build Firebase Performance SDK

### Fetch main branch

```
firebase-android-sdk$ git fetch origin main:main
firebase-android-sdk$ git checkout main
```

### Build SDK

Build the SDK in `firebase-android-sdk` folder:

```
firebase-android-sdk$ ./gradlew :clean :firebase-perf:build --stacktrace
```

Please note that `:clean` and `--stacktrace` are optional. The build output will be generated in
 `firebase-android-sdk/firebase-perf/build/outputs/aar`.

## Run Unit Tests

### Running all the tests

```
firebase-android-sdk$ ./gradlew :clean :firebase-perf:test --stacktrace
```

### Running a specific test

```
firebase-android-sdk$ ./gradlew :clean :firebase-perf:testDebugUnitTest --tests "PackageName.ClassName.TestMethodName" --stacktrace
```

> `TestMethodName` is optional, if not provided, all tests under `PackageName.ClassName` will be run.
Similar mechanism applies to `ClassName` as well.

## Run Integration Tests

Follow the instructions [here](https://github.com/firebase/firebase-android-sdk/blob/main/README.md#integration-testing) 
for the initial one time setup.

### Running Integration Tests on Local Emulator

```
firebase-android-sdk$ ./gradlew :firebase-perf:connectedCheck
```

### Running Integration Tests on Firebase Test Lab (triggered locally)

```
firebase-android-sdk$ ./gradlew :firebase-perf:deviceCheck
```

## Integrate SDK with 3P App

You can publish the SDK directly to your [local maven](https://docs.gradle.org/current/userguide/declaring_repositories.html#sub:maven_local) 
repository like so:

```bash
./gradlew :firebase-perf:publishToMavenLocal
```

### Read SDK from mavenLocal()

Add **mavenLocal()** to your project-level `build.gradle` file or `settings.gradle` file based on your app's set up.

### Set Local SDK version

Add **implementation** dependency to your module-level `build.gradle` file, with the version as defined in `firebase-perf/gradle.properties`.

```
apply plugin: 'com.android.application'

# Applies the 'perf-plugin'
apply plugin: 'com.google.firebase.firebase-perf'

.       .       .

dependencies {
   implementation 'com.google.firebase:firebase-perf:x.y.z'
}
```

See [Reference](https://github.com/firebase/firebase-android-sdk#commands) for more details.

## Check Code Coverage

Generate code coverage:

```
firebase-android-sdk$ ./gradlew firebase-perf:checkCoverage --stacktrace
```

Open the report in:

```
firebase-android-sdk$ open firebase-perf/build/reports/jacoco/firebase-perf/html/com.google.firebase.perf/index.html
```

## Releasing Firebase Performance SDK

Checkout internal release guidelines at go/fireperf-android.
