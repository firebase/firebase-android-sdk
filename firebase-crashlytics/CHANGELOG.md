# 17.0.0-beta01

This release for Firebase Crashlytics includes the initial beta release of the Firebase Crashlytics SDK.

If you previously used the legacy Fabric SDK for Crashlytics, you can consider this release as a major versioning of the Fabric SDK which includes an SDK name change and improvements to APIs. **The following release notes describe these changes to the SDK.**

 - If you're using Crashlytics in your app for the first time, follow the [getting started instructions](https://firebase.google.com/docs/crashlytics/get-started?platform=android). 
 - If you're upgrading from the legacy Fabric SDK to the Firebase Crashlytics SDK, follow the [upgrade instructions](https://firebase.google.com/docs/crashlytics/upgrade-sdk?platform=android) to make the required changes in your app to accommodate the SDK changes described in the following release notes. 

The following changes are only relevant to Crashlytics users who are upgrading from a previous Crashlytics version: 

 - [changed] Replaced static methods with new instance methods that are more consistent with other Firebase SDKs and more intuitive to use. The new APIs give your users more control over how you collect their data. 
 - [removed] Removed the Fabric Crashlytics API key. Now, Crashlytics will always use the `google-services.json` file to associate your app with your Firebase project. If you linked your app from Fabric, remove the Fabric API key from your `AndroidManifest.xml` file.
 - [removed] The `fabric.properties` and `crashlytics.properties` files are no longer supported. Remove them from your app.
