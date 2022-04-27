# Firebase App Distribution Test App

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
`test-app/test-app.gradle` for the app to link to Firebase.

## Build & Install

### Build with HEAD version of `firebase-appdistribution` SDK.

```
firebase-android-sdk$ ./gradlew :clean :firebase-appdistribution:test-app:build
```

After the build is successful, [bring up emulator/physical device](https://developer.android.com/studio/run/emulator)
and install the apk:

```
firebase-android-sdk$ adb install firebase-appdistribution/test-app/build/outputs/apk/release/test-app-release.apk
```