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

## Test In-App Feedback Locally

In-App Feedback is currently tricky to test locally because it relies on the
fact that a release exists with the same hash of the running binary.

To build the debug APK, upload it to App Distribution, and install it on the running emulator:
1. In firebase-appdistribution/test-app/test-app.gradle, uncomment the line `// testers "your email here"` and replace "your email here" with the email you intend to use for testing.
1. Start an emulator
1. Run the following command from the repo's root directory:

    ```
    ./gradlew clean :firebase-appdistribution:test-app:build :firebase-appdistribution:test-app:appDistributionUploadBetaDebug && adb install firebase-appdistribution/test-app/build/outputs/apk/beta/debug/test-app-beta-debug.apk
    ```
   
This will build the debug variant of the app (which has the full SDK), upload it to App Distribution, and install it on the running emulator. Run the app in the emulator to test. As an alternative you can always download it using App Distribution, but using `adb` is just faster.

After that, if you want to avoid having to do this every time you want to test locally:

1. Submit feedback in the locally running app, to generate some logs
1. In the Logcat output, find the release name (i.e. `"projects/1095562444941/installations/fCmpB677QTybkwfKbViGI-/releases/3prs96fui9kb0"`)
1. Modify the body of `ReleaseIdentifier.identifyRelease()` to be:

    ```
    return Tasks.forResult("<your release name>");
    ```
