# 17.0.0-beta01

This release for Firebase Crashlytics includes the initial beta release of
the Firebase Crashlytics SDK.

The Firebase Crashlytics SDK is a new version of the Crashlytics SDK
built _without_ Fabric and instead built entirely on Firebase. This new SDK has
new and improved APIs as well as an artifact name change.
The following release notes describe changes in the new SDK.

 - If you're using Crashlytics in your app for the first time, follow the
 [getting started instructions](https://firebase.google.com/docs/crashlytics/get-started-new-sdk?platform=android).
 - If you're upgrading from the legacy Fabric SDK to the
 Firebase Crashlytics SDK, follow the [upgrade instructions](https://firebase.google.com/docs/crashlytics/upgrade-sdk?platform=android)
 to update your app with the following SDK changes.

Note: The following changes are only relevant to Crashlytics users who are
upgrading from the legacy Fabric SDK.

 - [changed] Replaced static methods with new instance methods that are more
 consistent with other Firebase SDKs and more intuitive to use. The new APIs
 give your users more control over how you collect their data.
 - [removed] Removed the Fabric Crashlytics API key. Now, Crashlytics
 will always use the `google-services.json` file to associate your app with your
 Firebase project. If you linked your app from Fabric, remove the Fabric API key
 from your `AndroidManifest.xml` file.
 - [removed] The `fabric.properties` and `crashlytics.properties` files are no
 longer supported. Remove them from your app.
