# Firebase Remote Config Test App

## Setup

Download the `google-services.json` file
from [Firebase Console](https://console.firebase.google.com/) (for whatever Firebase project you
have or want to integrate the `test-app`) and store it under the current directory.

Note: The [Package name](https://firebase.google.com/docs/android/setup#register-app) for your app
created on the Firebase Console (for which the `google-services.json` is downloaded) must match
the [applicationId](https://developer.android.com/studio/build/application-id.html) declared in
the `test-app/test-app.gradle.kts` for the app to link to Firebase.

## Running

Run the test app directly from Android Studio by selecting and running
the `firebase-config.test-app` run configuration.
