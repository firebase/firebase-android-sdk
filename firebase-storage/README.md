# firebase-storage

This is the Cloud Storage for Firebase component of the Firebase Android SDK.

Cloud Storage for Firebase is a powerful, simple, and cost-effective object storage service built
for Google scale. The Firebase SDKs for Cloud Storage add Google security to file uploads and
downloads for your Firebase apps, regardless of network quality. You can use our SDKs to store
images, audio, video, or other user-generated content. On the server, you can use Google Cloud
Storage, to access the same files.

All Gradle commands should be run from the source root (which is one level up from this folder).

Building
========
You can build the SDK by invoking `./gradlew :firebase-storage:assemble`.

If you want to test changes locally, you may also run
`./gradlew -PprojectsToPublish="firebase-storage" firebasePublish`. This generates the Maven 
dependency tree (under `build/`) that you can use during app development.

Testing
=======

To run the unit tests:

- Invoke `./gradlew :firebase-storage:check`.

To run the integration tests:

- Make sure that you have configured a `google-service.json` from your Firebase test project in the
  source root. 
- Enable Firebase Storage in your project. Firebase Storage can be implicitly enabled by visiting
  the Storage tab in the [Firebase Console](https://console.firebase.google.com/).
- For now, you have to disable security rule enforcement for Cloud Storage in your test project.
- Start an Android Emulator.
- Invoke `./gradlew :firebase-storage:connectedCheck`.
- Re-enable your security rules after your test run.


Formatting
==========
Format your source files via `./gradlew :firebase-storage:googleJavaFormat`

