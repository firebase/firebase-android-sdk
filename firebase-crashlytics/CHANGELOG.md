# 17.0.0-beta04

- [changed] Imposed a limit on the maximum crash report payload size.

- [fixed] Reduced crash report payload size.

# 17.0.0-beta03

- [fixed] Fixed internal bugs to improve crash analysis and grouping.

- [changed] Improved compatibility with Google Analytics. For best
  performance, make sure you're using the latest versions of the
  Firebase Crashlytics SDK and the Firebase SDK for Google Analytics.

- [changed] Updated remaining Crashlytics backend API calls to prepare
  for Fabric sunset.

# 17.0.0-beta02

- [changed] Removed superfluous `D/FirebaseCrashlytics` prefix from logs.
  ([#1202](https://github.com/firebase/firebase-android-sdk/issues/1202))

- [changed] Updated Crashlytics backend API calls in preparation for
  Fabric sunset.

- [changed] Upgraded Firebase Analytics integration to improve crash-free
  users accuracy. For improved performance, we recommend that you upgrade to the
  latest version of the Firebase SDK for Firebase Analytics with this
  version of Firebase Crashlytics.

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
