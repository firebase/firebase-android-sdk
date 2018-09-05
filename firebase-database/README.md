# firebase-database

This is the Realtime Database component of the Firebase Android SDK.

The Firebase Realtime Database is a cloud-hosted database. Data is stored as JSON and synchronized
in realtime to every connected client. When you build cross-platform apps with our iOS, Android,
and JavaScript SDKs, all of your clients share one Realtime Database instance and automatically
receive updates with the newest data.

All Gradle commands should be run from the source root (which is one level up from this folder).

Building
========
You can build the SDK by invoking `./gradlew :firebase-database:assemble`.

If you want to test changes locally, you may also run
`./gradlew -PprojectsToPublish="firebase-database" firebasePublish`. This generates the Maven 
dependency tree (under `build/`) that you can use during app development.

Testing
=======

To run the unit tests:

- Invoke `./gradlew :firebase-database:check`.

To run the integration tests:

- Make sure that you have configured a `google-service.json` from your Firebase test project in the
  source root.
- For now, you have to disable security rule enforcement for the Realtime Databse in your test
  project.
- Start an Android Emulator.
- Invoke `./gradlew :firebase-database:connectedCheck`.
- Re-enable your security rules after your test run.


Formatting
==========
Format your source files via `./gradlew :firebase-database:googleJavaFormat`

