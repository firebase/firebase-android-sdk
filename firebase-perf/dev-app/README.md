# [Firebase Performance](https://firebase.google.com/docs/perf-mon/get-started-android) Development App

## Setup

Download the `google-services.json` file from [Firebase Console](https://console.firebase.google.com/) 
(for whatever Firebase project you have or want to integrate the `dev-app`) and store it under the 
current directory.

<p align="center">
  <img src="https://i.stack.imgur.com/BFmz5.png">
</p>

> **Note:** The [Package name](https://firebase.google.com/docs/android/setup#register-app) for your 
app created on the Firebase Console (for which the `google-services.json` is downloaded) must match 
the [applicationId](https://developer.android.com/studio/build/application-id.html) declared in the 
`dev-app/dev-app.gradle` for the app to link to Firebase.

## Build & Install

### Build app for `autopush` environment

```
firebase-android-sdk$ ./gradlew :clean :firebase-perf:dev-app:build -PfireperfBuildForAutopush
```

> **Note:** Builds with HEAD version of `firebase-perf` SDK.

### Build app for `prod` environment

```
firebase-android-sdk$ ./gradlew :clean :firebase-perf:dev-app:build
```

> **Note:** Builds with latest public version of `firebase-perf` SDK.

> **Tip:** The above command will build all the configured variants which may slow down the build. 
To fasten local development replace `build` with `assembleRelease` task. To view the complete list 
of tasks available run `./gradlew :clean :firebase-perf:dev-app:tasks --all`.

After the build is successful, [bring up emulator/physical device](https://developer.android.com/studio/run/emulator) 
and install the apk:

```
firebase-android-sdk$ adb install firebase-perf/dev-app/build/outputs/apk/release/dev-app-release.apk
```

> **Tip:** Alternatively you can also use [Gradle Tool Panel](https://youtu.be/2S94dlL5nMI) located 
on the top right side of the Android Studio to run any provided gradle task (including installing/uninstalling 
apk and running tests).

## Instrumentation Test

### Run tests for `autopush` environment

To run tests on local connected device/emulator:

```
firebase-android-sdk$ ./gradlew :firebase-perf:dev-app:connectedCheck -PfireperfBuildForAutopush
```

To run tests on Firebase Test Lab:

```
firebase-android-sdk$ ./gradlew :firebase-perf:dev-app:devicecheck -PfireperfBuildForAutopush
```

### Run tests for `prod` environment

To run tests on a local connected device/emulator:

```
firebase-android-sdk$ ./gradlew :firebase-perf:dev-app:connectedCheck
```

To run tests on Firebase Test Lab:

```
firebase-android-sdk$ ./gradlew :firebase-perf:dev-app:devicecheck
```

#### Note

There are differences in terms of Firebase projects when running this command in different scenarios.

1. **CI Run**: These tests are run under Firebase Test Lab of the unified Firebase project 
(according to [this](https://github.com/firebase/firebase-android-sdk/blob/master/buildSrc/src/main/java/com/google/firebase/gradle/plugins/ci/device/FirebaseTestServer.java)) 
but the performance events are sent to a different project with which apps are configured with 
(see `copyRootGoogleServices` task). 
 
1. **Local run**: When running locally both the tests and the events will happen on the same locally 
integrated Firebase project.

## Logs

To monitor device logging: 

```
firebase-android-sdk$ adb logcat -s FirebasePerformance
```

Alternatively you can also use Android Studio [Logcat](https://developer.android.com/studio/debug/am-logcat).

## SDK size measurement

> **Note:** Currently we are using apk size as ballpark approximations for SDK size impact. It
 only serves as a proxy for SDK size changes and should not be considered solely for absolute
 SDK size.

As a local validation of SDK size impact, you can run the same command as the `dev-app` build:

```
firebase-android-sdk$ ./gradlew :clean :firebase-perf:dev-app:build --stacktrace
```

And go to the following locations to check for app artifact size:

*  Debug: `firebase-perf/dev-app/build/outputs/apk/debug/dev-app-debug.apk`
*  Release: `firebase-perf/dev-app/build/outputs/apk/release/dev-app-release.apk`
*  Aggressive: `firebase-perf/dev-app/build/outputs/apk/aggressive/dev-app-aggressive.apk`