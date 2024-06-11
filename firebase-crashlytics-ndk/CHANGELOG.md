# Unreleased
* [changed] Update libcrashlytics to support 16 kb page sizes.

# 19.0.1
* [changed] Updated `firebase-crashlytics` dependency to v19.0.1

# 19.0.0
* [changed] Bump internal dependencies

# 18.6.3
* [changed] Updated `firebase-crashlytics` dependency to v18.6.3

# 18.6.0
* [changed] Updated `firebase-crashlytics` dependency to v18.6.0

# 18.5.0
* [changed] Updated `firebase-crashlytics` dependency to v18.5.0

# 18.4.3
* [changed] Updated `firebase-crashlytics` dependency to v18.4.3

# 18.4.2
* [changed] Updated `firebase-crashlytics` dependency to v18.4.2

# 18.4.1
* [changed] Updated `firebase-crashlytics` dependency to v18.4.1

# 18.4.0
* [changed] Updated `firebase-crashlytics` dependency to v18.4.0

# 18.3.7
* [changed] Updated `firebase-crashlytics` dependency to v18.3.7

# 18.3.6
* [changed] Updated `firebase-crashlytics` dependency to v18.3.6.

# 18.3.5
* [fixed] Updated `firebase-common` to its latest version (v20.3.0) to fix an
  issue that was causing a nondeterministic crash on startup.
* [changed] Updated `firebase-crashlytics` dependency to v18.3.5.

# 18.3.4
<aside class="caution">This version of <code>firebase-crashlytics-ndk</code> can
  cause a nondeterministic crash on startup. For more information, see
  <a href="https://github.com/firebase/firebase-android-sdk/issues/4683"
     class="external">GitHub Issue #4683</a>. We recommend updating to the
  latest version (v18.3.5+) which contains a fix.
</aside>

* [changed] Updated `firebase-crashlytics` dependency to v18.3.4.

# 18.3.3
* [changed] Updated internal Crashpad version to commit `c902f6`.

# 18.3.2
* [fixed] Fixed an [issue](https://github.com/firebase/firebase-android-sdk/issues/4313){: .external}
  preventing native crashes from being reported for Android API 29+.

# 18.3.1
Warning: We're aware of an
[issue](https://github.com/firebase/firebase-android-sdk/issues/4313){: .external}
in this version of the [crashlytics] SDK for NDK.<br>**We strongly
recommend using the latest version of the SDK (v18.3.2+ or [bom] v31.0.3+).**

* [changed] Updated `firebase-crashlytics` dependency to v18.3.1.

# 18.3.0
Warning: We're aware of an
[issue](https://github.com/firebase/firebase-android-sdk/issues/4223){: .external}
in the [crashlytics] Android SDK v18.3.0.<br>**We strongly recommend
using the latest version of the SDK (v18.3.1+ or [bom] v31.0.1+).**

* [changed] Updated `firebase-crashlytics` dependency to v18.3.0.

# 18.2.13
* [changed] Updated dependency of `play-services-basement` to its latest
  version (v18.1.0).

# 18.2.12
* [changed] Updated `firebase-crashlytics` dependency to v18.2.12.

# 18.2.11
* [changed] Updated `firebase-crashlytics` dependency to v18.2.11.

# 18.2.10
* [changed] Updated `firebase-crashlytics` dependency to v18.2.10.

# 18.2.9
* [changed] Updated `firebase-crashlytics` dependency to v18.2.9.

# 18.2.8
* [changed] Updated `firebase-crashlytics` dependency to v18.2.8.

# 18.2.7
* [changed] Updated `firebase-crashlytics` dependency to v18.2.7.

# 18.2.6
* [changed] Updated internal Crashpad version to commit `281ba7`. With this
  change, disabling tagged pointers is no longer required, so the following can
  be removed from your manifest's `application` tag:
  `android:allowNativeHeapPointerTagging=false`.
* [changed] Updated `firebase-crashlytics` dependency to v18.2.6.

# 18.2.5
* [changed] Internal improvements to [crashlytics] file management, to
  ensure consistent creation and removal of intermediate [crashlytics] files.
* [changed] Updated `firebase-crashlytics` dependency to v18.2.5.

# 18.2.4
* [changed] Added an obfuscation exclusion for
  `com.google.firebase.crashlytics.ndk.FirebaseCrashlyticsNdk` to the Proguard
  configuration for this AAR, to avoid potential reflection errors when
  obfuscating NDK-enabled apps.
* [changed] Updated `firebase-crashlytics` dependency to v18.2.4.

# 18.2.3
* [changed] Internal changes to support upcoming Unity crash reporting
  improvements.
* [changed] Updated `firebase-crashlytics` dependency to v18.2.3.

# 18.2.1
* [fixed] Improved support for NDK crash reporting when using
  [Play Feature Delivery](/docs/android/learn-more#dynamic-feature-modules).
  Previously, `firebase-crashlytics-ndk` needed to be a dependency of the app
  module to consistently report native crashes for all supported Android
  versions. [crashlytics] will now report native crashes when used as a
  dependency of a feature module.
* [changed] Updated `firebase-crashlytics` dependency to v18.2.1.

# 18.2.0
* [changed] Updated `firebase-crashlytics` dependency to v18.2.0.

# 18.1.0
* [changed] Updated `firebase-crashlytics` dependency to v18.1.0.

# 18.0.1
* [changed] Updated `firebase-crashlytics` dependency to v18.0.1, which fixes
  a bug that could cause excessive disk usage from NDK crash report files when
  crash reporting is disabled.

# 18.0.0
* [changed] Internal changes to support dynamic feature modules.
* [changed] Updated `firebase-crashlytics` dependency to v18.0.0.

# 17.4.1
* [changed] Updated `firebase-crashlytics` dependency to v17.4.1.

# 17.4.0
* [changed] Updated `firebase-crashlytics` dependency to v17.4.0.

# 17.3.1
* [changed] Updated `firebase-crashlytics` dependency to v17.3.1.

# 17.3.0
Note: To ensure proper symbolication of NDK crashes, you must use
[[crashlytics] Gradle plugin v2.4.0+](#crashlytics_gradle_plugin_v2-4-0) when
using this version of the [crashlytics] NDK SDK and above.

* [fixed] Upgraded underlying native crash reporting library to
  [Crashpad](//crashpad.chromium.org){: .external}. This addresses emerging issues
  with capturing certain types of native crashes on Android 10+ using
  [Breakpad](//chromium.googlesource.com/breakpad){: .external}.
  ([Github Issue #1678](//github.com/firebase/firebase-android-sdk/issues/1678){: .external})

# 17.2.2
* [changed] Updated `firebase-crashlytics` dependency to v17.2.2.

# 17.2.1
* [fixed] Fixed signal handler to properly release storage on app exit.
  ([Github Issue #1749](https://github.com/firebase/firebase-android-sdk/issues/1749))
* [changed] Updated `firebase-crashlytics` dependency to v17.2.1.

# 17.1.1
* [changed] Updated `firebase-crashlytics` dependency to v17.1.1.

# 17.1.0
* [changed] Updated `firebase-crashlytics` dependency to v17.1.0.

# 17.0.1
* [changed] Updated `firebase-crashlytics` dependency to v17.0.1.

# 17.0.0
* [changed] The [firebase_crashlytics] SDK for NDK is now generally
  available.
* [changed] Updated `firebase-crashlytics` dependency to v17.0.0.

# 17.0.0-beta04
* [changed] Updated `firebase-crashlytics` dependency to v17.0.0-beta-04.

# 17.0.0-beta03
* [fixed] Updated package name in `AndroidManifest.xml` to reflect new
  [firebase_crashlytics] NDK package name.
* [changed] Improved debug logging.
* [changed] Released new `crashlytics.h` with updated C++ APIs.
* [changed] Added ProGuard rules files to avoid obfuscating public APIs called
  from C++.

# 17.0.0-beta01
This release includes the initial beta release of the [firebase_crashlytics]
SDK for NDK crash reporting.

The [firebase_crashlytics] SDK for NDK is a new version of the [crashlytics]
SDK for NDK crash reporting built _without_ Fabric and instead built entirely on
Firebase. This new SDK has new and improved APIs as well as an artifact name
change. The following release notes describe changes in the new SDK.

<aside class="note"><p>The changes in these release notes are only relevant to
  [crashlytics] NDK users who are upgrading from the legacy Fabric SDK.</p>
  <ul>
    <li>If you're using [crashlytics] for NDK crash reporting in your app for
      the first time, follow the
      <a href="/docs/crashlytics/get-started-new-sdk?platform=android">getting
        started instructions</a>.
    </li>
    <li>If you're upgrading from the legacy Fabric SDK to the
      [firebase_crashlytics] SDK for NDK crash reporting, follow the
      <a href="/docs/crashlytics/upgrade-sdk?platform=android">upgrade
        instructions</a> to update your app with the following SDK changes.
    </li>
  </ul>
</aside>

 * [changed] [crashlytics] NDK crash reporting will now start automatically
 when the [crashlytics] NDK dependency is included in your app.
 * [changed] The [crashlytics] Gradle plugin has new tasks to support
 uploading symbol files to [crashlytics] servers. See the
 [[crashlytics] Gradle plugin documentation](/docs/crashlytics/ndk-reports-new-sdk)
 for more information.

