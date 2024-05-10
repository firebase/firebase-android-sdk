# [Firebase Performance](https://firebase.google.com/docs/perf-mon/get-started-android) End-to-End Test App

## Setup

Download the `google-services.json` file from [Firebase Console](https://console.firebase.google.com/) 
(for whatever Firebase project you have or want to integrate the `e2e-app`) and store it under the 
current directory.

<p align="center">
  <img src="https://i.stack.imgur.com/BFmz5.png">
</p>

> **Note:** The [Package name](https://firebase.google.com/docs/android/setup#register-app) for your 
app created on the Firebase Console (for which the `google-services.json` is downloaded) must match 
the [applicationId](https://developer.android.com/studio/build/application-id.html) declared in the 
`e2e-app/e2e-app.gradle` for the app to link to Firebase.

## Build

### Build app for `autopush` environment

```
firebase-android-sdk$ ./gradlew :clean :firebase-perf:e2e-app:build -PfireperfBuildForAutopush
```

> **Note:** Builds with HEAD version of `firebase-perf` SDK.

### Build app for `prod` environment

```
firebase-android-sdk$ ./gradlew :clean :firebase-perf:e2e-app:build
```

> **Note:** Builds with latest public version of `firebase-perf` SDK.

> **Tip:** The above command will build all the configured variants which may slow down the build.
To fasten local development replace `build` with `assembleRelease` task. To view the complete list 
of all available tasks run `./gradlew :clean :firebase-perf:e2e-app:tasks --all`.

After the build is successful, you can [bring up emulator/physical device](https://developer.android.com/studio/run/emulator) 
which can run the binary however, the purpose of this `e2e-app` is to solely run the [instrumentation tests](https://developer.android.com/training/testing/unit-testing/instrumented-unit-tests).

## Instrumentation Test

### Run tests for `autopush` environment

To run tests on local connected device/emulator:

```
firebase-android-sdk$ ./gradlew :firebase-perf:e2e-app:connectedCheck -PfireperfBuildForAutopush
```

To run tests on Firebase Test Lab:

```
firebase-android-sdk$ ./gradlew :firebase-perf:e2e-app:devicecheck -PfireperfBuildForAutopush
```

### Run tests for `prod` environment

To run tests on local connected device/emulator:

```
firebase-android-release$ ./gradlew :firebase-perf:e2e-app:connectedCheck
```

To run tests on Firebase Test Lab:

```
firebase-android-sdk$ ./gradlew :firebase-perf:e2e-app:devicecheck
```

#### Note

There are differences in terms of Firebase projects when running this command in different scenarios.

1. **CI Run**: These tests are run under Firebase Test Lab of the unified Firebase project 
(according to [this](https://github.com/firebase/firebase-android-sdk/blob/master/buildSrc/src/main/java/com/google/firebase/gradle/plugins/ci/device/FirebaseTestServer.java)) 
but the performance events are sent to a different project with which apps are configured with 
(see `copyRootGoogleServices` task) 
and the Prow Configuration in tg/831643). 

1. **Local run**: When running locally both the tests and the events will happen on the same locally 
integrated Firebase project.

## Logs

To monitor device logging: 

```
firebase-android-sdk$ adb logcat -s FirebasePerformance
```

Alternatively you can also use Android Studio [Logcat](https://developer.android.com/studio/debug/am-logcat).
