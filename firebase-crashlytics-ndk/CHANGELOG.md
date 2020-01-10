# 17.0.0-beta01

This release includes the initial beta release of Firebase Crashlytics's NDK crash reporting SDK.

If you previously used the legacy Fabric SDK for NDK crash reporting, you can consider this release as a new major version of the SDK which includes artifact name changes and improvements to APIs. **The following release notes describe these changes to the SDK.**

- If you're using Crashlytics for NDK crash reporting in your app for the first time, first follow the [getting started instructions](https://firebase.google.com/docs/crashlytics/get-started?platform=android).
 - If you're upgrading from the legacy Fabric SDK to the Firebase Crashlytics SDK, first follow the [upgrade instructions](https://firebase.google.com/docs/crashlytics/upgrade-sdk?platform=android).

The following changes are only relevant to Crashlytics NDK users who are upgrading from a previous Crashlytics NDK version:

 - [changed] Crashlytics NDK crash reporting will now start automatically when the Crashlytics NDK dependency is included in your app. 
 - [changed] The Crashlytics Gradle plugin has new tasks to support uploading symbol files to Crashlytics servers. See the [Crashlytics Gradle plugin documentation](https://firebase.google.com/docs/crashlytics/ndk-reports) for more information.

