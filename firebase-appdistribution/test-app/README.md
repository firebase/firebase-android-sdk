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

### Enable the test-app as a subproject ###

You'll need to do this on a fresh checkout, otherwise you will see the error `Project 'test-app' not found in project ':firebase-appdistribution'.` when running `./gradlew` tasks for the test app.

By default, product-specific subprojects are disabled in the SDK because their `google-services.json` files aren't always available in CI and therefore they can't be reliably built.  To do local development with this test app, it needs to be manually enabled by uncommenting it out at the bottom of [subprojects.cfg](https://github.com/firebase/firebase-android-sdk/blob/master/subprojects.cfg) (*Don't check this in*)

```
# <near the bottom of the file>
# Test Apps
# If needed for development, uncomment but don't submit

#
#some-other-app:test-app
#and-another:test-app
#...
firebase-appdistribution:test-app

```

### Build with HEAD version of `firebase-appdistribution` SDK.

```
firebase-android-sdk$ ./gradlew :clean :firebase-appdistribution:test-app:build
```

After the build is successful, [bring up emulator/physical device](https://developer.android.com/studio/run/emulator)
and install the apk:

```
firebase-android-sdk$ adb install firebase-appdistribution/test-app/build/outputs/apk/release/test-app-release.apk
```