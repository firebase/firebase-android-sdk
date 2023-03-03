# Unreleased
* [changed] Improved crash reporting reliability for crashes that occur early in the app's
  lifecycle.
* [changed] Add improved support capturing build ids for Native ANRs on older
  Android versions.


# 18.3.5
* [fixed] Updated `firebase-common` to its latest version (v20.3.0) to fix an
  issue that was causing a nondeterministic crash on startup.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-crashlytics` library. The Kotlin extensions library has no additional
updates.
# 18.3.4
<aside class="caution">This version of <code>firebase-crashlytics</code> and
  <code>firebase-crashlytics-ktx</code> can cause a nondeterministic crash on
  startup. For more information, see
  <a href="https://github.com/firebase/firebase-android-sdk/issues/4683"
     class="external">GitHub Issue #4683</a>. We recommend updating to the
  latest version (v18.3.5+) which contains a fix.
</aside>

* [changed] Improved crash reporting reliability for crashes that occur early
  in the app's lifecycle.

* [changed] Added improved support for capturing `BuildId`s for native ANRs on
  older Android versions.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-crashlytics` library. The Kotlin extensions library has no additional
updates.

# 18.3.3
* [unchanged] Updated to accommodate the release of the updated
  `firebase-crashlytics-ndk` v18.3.3.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-crashlytics` library. The Kotlin extensions library has no additional
updates.

# 18.3.2
* [unchanged] Updated to accommodate the release of the updated
  `firebase-crashlytics-ndk` v18.3.2.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-crashlytics` library. The Kotlin extensions library has no additional
updates.

# 18.3.1
* [fixed] Fixed an [issue](https://github.com/firebase/firebase-android-sdk/issues/4223){: .external}
  in v18.3.0 that caused a `NoClassDefFoundError` in specific cases.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-crashlytics` library. The Kotlin extensions library has no additional
updates.

# 18.3.0
Warning: We're aware of an
[issue](https://github.com/firebase/firebase-android-sdk/issues/4223){: .external}
in this version of the [crashlytics] Android SDK.<br>**We strongly recommend
using the latest version of the SDK (v18.3.1+ or [bom] v31.0.1+).**

* [changed] Improved reporting for crashes that occur early in the app's
  lifecycle. After updating to this version, you might notice a sudden
  _increase_ in the number of crashes that are reported for your app.


## Kotlin
The Kotlin extensions library transitively includes the updated
  `firebase-crashlytics` library. The Kotlin extensions library has the
  following additional updates:

* [feature] Firebase now supports Kotlin coroutines.
  With this release, we added
  [`kotlinx-coroutines-play-services`](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-play-services/){: .external}
  to `firebase-crashlytics-ktx` as a transitive dependency, which exposes the
  `Task<T>.await()` suspend function to convert a
  [`Task`](https://developers.google.com/android/guides/tasks) into a Kotlin
  coroutine.

# 18.2.13
* [changed] Updated dependency of `play-services-basement` to its latest
  version (v18.1.0).
  

## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-crashlytics` library. The Kotlin extensions library has no additional
updates.

# 18.2.12
* [changed] Internal changes to avoid accessing device-specific information.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-crashlytics` library. The Kotlin extensions library has no additional
updates.

# 18.2.11
* [changed] Improved crash reporting reliability for multi-process apps on
  Android 28 and above.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-crashlytics` library. The Kotlin extensions library has no additional
updates.

# 18.2.10
* [fixed] Fixed a bug that could prevent unhandled exceptions from being
  propogated to the default handler when the network is unavailable.

* [changed] Internal changes to support on-demand fatal crash reporting for
  Flutter apps.

* [fixed] Fixed a bug that prevented [crashlytics] from initalizing on some
  devices in some cases. (#3269)


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-crashlytics` library. The Kotlin extensions library has no additional
updates.

# 18.2.9
* [changed] Updated dependencies of `play-services-basement`,
  `play-services-base`, and `play-services-tasks` to their latest versions
  (v18.0.0, v18.0.1, and v18.0.1, respectively). For more information, see the
  [note](#basement18-0-0_base18-0-1_tasks18-0-1) at the top of this release
  entry.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-crashlytics` library. The Kotlin extensions library has no additional
updates.

# 18.2.8
* [changed] Updated to the latest version of the `firebase-datatransport`
  library.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-crashlytics` library. The Kotlin extensions library has no additional
updates.

# 18.2.7
* [changed] Improved runtime efficiency of the
  [`setCustomKey` functions](/docs/crashlytics/customize-crash-reports?platform=android#add-keys),
  significantly reducing the number of `Task` objects and disk writes when keys
  are updated frequently.
  (#3254)
* [fixed] Fixed a StrictMode `DiskReadViolation`.
  (#3265)


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-crashlytics` library. The Kotlin extensions library has no additional
updates.

# 18.2.6
* [changed] Internal changes to support future improvements to Flutter crash
  reporting.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-crashlytics` library. The Kotlin extensions library has no additional
updates.

# 18.2.5
* [fixed] Fixed a bug that prevented some [crashlytics] session files from
  being removed after the session ended. All session-specific files are now
  properly cleaned up.
* [changed] Internal improvements to [crashlytics] file management, to
  ensure consistent creation and removal of intermediate [crashlytics] files.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-crashlytics` library. The Kotlin extensions library has no additional
updates.

# 18.2.4
* [changed] Internal changes to support ANR collection and their upcoming
  display in the console.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-crashlytics` library. The Kotlin extensions library has no additional
updates.

# 18.2.3
* [fixed] Fixed a race condition that prevented some launch-time crashes from
  being reported to Crashlytics.

* [changed] Internal changes to support upcoming Unity crash reporting
  improvements.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-crashlytics` library. The Kotlin extensions library has no additional
updates.

# 18.2.1
* [fixed] Fixed a `ConcurrentModificationException` that could be logged to
  logcat when setting multiple custom key/values in rapid succession.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-crashlytics` library. The Kotlin extensions library has no additional
updates.

# 18.2.0
* [changed] Internal changes.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-crashlytics` library. The Kotlin extensions library has no additional
updates.

# 18.1.0
* [changed] Internal changes to support upcoming Unity features.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-crashlytics` library. The Kotlin extensions library has no additional
updates.

# 18.0.1
* [fixed] Fixed a bug that could prevent proper removal of [crashlytics] NDK
  crash report files when crash reporting is disabled, resulting in excessive
  disk use.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-crashlytics` library. The Kotlin extensions library has no additional
updates.

# 18.0.0
* [changed] Removed call to
  [`FirebaseInstallations#getId()`](/docs/reference/android/com/google/firebase/installations/FirebaseInstallations#getId())
  when [automatic data collection](/docs/crashlytics/customize-crash-reports?platform=android#enable-reporting)
  is disabled for [crashlytics]. [crashlytics] no longer makes any network
  calls when reporting is disabled.

* [changed] Internal changes to support dynamic feature modules.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-crashlytics` library. The Kotlin extensions library has no additional
updates.

# 17.4.1
* [changed] Improved rooted device detection.
  (#2515)

* [fixed] Fix an uncaught IllegalStateExeception that could be thrown if
  [crashlytics] is unable to register a receiver that collects battery state
  information. If registration fails due to the app already having registered
  too many receivers, [crashlytics] will report default values for the battery
  state rather than crashing.
  (#2504)


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-crashlytics` library. The Kotlin extensions library has no additional
updates.

# 17.4.0
* [feature] Added the
  [`setCustomKeys`](/docs/reference/android/com/google/firebase/crashlytics/CustomKeysAndValues)
  API to allow bulk logging of custom keys and values.
  ([Github PR #2443](//github.com/firebase/firebase-android-sdk/pull/2443){: .external})



## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-crashlytics` library. The Kotlin extensions library has no additional
updates.

# 17.3.1
* [changed] Removed OkHttp dependency to eliminate conflicts with apps and
  SDKs using incompatible versions.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-crashlytics` library. The Kotlin extensions library has no additional
updates.

# 17.3.0
* [changed] Clarified debug logs for crash reports enqueued to be sent via the
  `firebase-datatransport` library.

* [fixed] Addressed an issue which could cause a `RejectedExecutionException`
  in rare cases.
  ([Github Issue #2013](//github.com/firebase/firebase-android-sdk/issues/2013){: .external})


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-crashlytics` library. The Kotlin extensions library has no additional
updates.

# 17.2.2
* [fixed] Fixed crash that can occur when using a built-in resource as the app
  launcher icon.
  ([Github Issue #1935](//github.com/firebase/firebase-android-sdk/issues/1935){: .external})

* [fixed] Fixed a bug preventing crash reports from being sent in some cases
  when an app is using [crashlytics] on multiple processes.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-crashlytics` library. The Kotlin extensions library has no additional
updates.

# 17.2.1
* [fixed] Improved handling of asynchronous tasks that need to wait for
  completion on the main thread.
  ([Github PR #1739](//github.com/firebase/firebase-android-sdk/pull/1739){: .external})

* [changed] Added an overload to the `setCrashlyticsCollectionEnabled` API to
  allow for passing `null` to clear any previously set value.
  ([Github PR #1434](//github.com/firebase/firebase-android-sdk/pull/1434){: .external})

* [changed] Migrated to use the [firebase_installations] service _directly_
  instead of using an indirect dependency via the Firebase Instance ID SDK.
  ([Github PR #1760](//github.com/firebase/firebase-android-sdk/pull/1760){: .external})

  {% include "docs/reference/android/client/_includes/_iid-indirect-dependency-solutions.html" %}


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-crashlytics` library. The Kotlin extensions library has no additional
updates.

# 17.1.1
* [changed] To improve the reliability of submitting crash uploads on poor
  networks, changed the Transport SDK to retry connection errors
  ([Github Issue #1705](//github.com/firebase/firebase-android-sdk/issues/1705){: .external})
  and increased the number of retries before deleting events
  ([Github Issue #1708](//github.com/firebase/firebase-android-sdk/issues/1708){: .external}).


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-crashlytics` library. The Kotlin extensions library has no additional
updates.

# 17.1.0
* [fixed] Updated [crashlytics] integration with [firebase_analytics] to
  include native crashes in crash-free users counts.

* [fixed] Removed a harmless, yet unexpected `FileNotFoundException` log
  output that printed when an optional file is unavailable.
  ([Github Issue #1559](//github.com/firebase/firebase-android-sdk/issues/1559#issuecomment-638387614){: .external})



## Kotlin
* [feature] The [firebase_crashlytics] Android library with Kotlin
  extensions is now available. The Kotlin extensions library transitively
  includes the base `firebase-crashlytics` library. To learn more, visit the
  [[crashlytics] KTX documentation](/docs/reference/kotlin/com/google/firebase/crashlytics/ktx/package-summary).

# 17.0.1
* [fixed] Fixed an issue causing a `SQLiteException` when changing versions
  of [crashlytics].
  ([Github Issue #1531](https://github.com/firebase/firebase-android-sdk/issues/1531){: .external})

* [fixed] Improved reliability of sending reports at crash time on Android API
  level 28+.

# 17.0.0
* [changed] The [firebase_crashlytics] SDK is now generally available.

* [fixed] Fixed an issue that could cause apps to crash if a crash report
  payload is too large (rare).

* [changed] Updated dependency on the Firebase Instance ID library to v20.1.5,
  which is a step towards a direct dependency on the [firebase_installations]
  service in a future release.

# 17.0.0-beta04
* [changed] Imposed a limit on the maximum crash report payload size.

* [fixed] Reduced crash report payload size.

# 17.0.0-beta03
* [fixed] Fixed internal bugs to improve crash analysis and grouping.

* [changed] Improved compatibility with Google Analytics. For best
  performance, make sure you're using the latest versions of the
  [firebase_crashlytics] SDK and the Firebase SDK for Google Analytics.

* [changed] Updated remaining [crashlytics] backend API calls to prepare
  for Fabric sunset.

# 17.0.0-beta02
* [changed] Removed superfluous `D/FirebaseCrashlytics` prefix from logs.
  ([#1202](https://github.com/firebase/firebase-android-sdk/issues/1202))

* [changed] Updated [crashlytics] backend API calls in preparation for
  Fabric sunset.

* [changed] Upgraded [firebase_analytics] integration to improve crash-free
  users accuracy. For improved performance, we recommend that you upgrade to the
  latest version of the Firebase SDK for [firebase_analytics] with this
  version of [firebase_crashlytics].

# 17.0.0-beta01
This release for [firebase_crashlytics] includes the initial beta release of
the [firebase_crashlytics] SDK.

The [firebase_crashlytics] SDK is a new version of the [crashlytics] SDK
built _without_ Fabric and instead built entirely on Firebase. This new SDK has
new and improved APIs as well as an artifact name change.
The following release notes describe changes in the new SDK.

<aside class="note"><p>The changes in these release notes are only relevant to
  [crashlytics] users who are upgrading from the legacy Fabric SDK.</p>
  <ul>
    <li>If you're using [crashlytics] for NDK crash reporting in your app for
      the first time, follow the
      <a href="/docs/crashlytics/get-started-new-sdk?platform=android">getting
        started instructions</a>.
    </li>
    <li>If you're upgrading from the legacy Fabric SDK to the
      [firebase_crashlytics] SDK, follow the
      <a href="/docs/crashlytics/upgrade-sdk?platform=android">upgrade
        instructions</a> to update your app with the following SDK changes.
    </li>
  </ul>
</aside>

 - [changed] Replaced static methods with new instance methods that are more
 consistent with other Firebase SDKs and more intuitive to use. The new APIs
 give your users more control over how you collect their data.
 - [removed] Removed the Fabric [crashlytics] API key. Now, [crashlytics]
 will always use the `google-services.json` file to associate your app with your
 Firebase project. If you linked your app from Fabric, remove the Fabric API key
 from your `AndroidManifest.xml` file.
 - [removed] The `fabric.properties` and `crashlytics.properties` files are no
 longer supported. Remove them from your app.

