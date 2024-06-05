# Unreleased


# 21.0.1
* [fixed] Fixed an `ExceptionInInitializerError` where the `url.openStream()` causes a crash if
  FirebasePerf is not yet initialized (Github #5584).


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-performance` library. The Kotlin extensions library has no additional
updates.

# 21.0.0
* [changed] Bump internal dependencies


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-performance` library. The Kotlin extensions library has no additional
updates.

# 20.5.2
* [changed] Bump internal dependencies.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-performance` library. The Kotlin extensions library has no additional
updates.

# 20.5.1
* [changed] Make Fireperf generate its own session Id.

# 20.5.0
* [changed] Added Kotlin extensions (KTX) APIs from `com.google.firebase:firebase-perf-ktx`
  to `com.google.firebase:firebase-perf` under the `com.google.firebase.perf` package.
  For details, see the
  [FAQ about this initiative](https://firebase.google.com/docs/android/kotlin-migration)
* [deprecated] All the APIs from `com.google.firebase:firebase-perf-ktx` have been added to
  `com.google.firebase:firebase-perf` under the `com.google.firebase.perf` package,
  and all the Kotlin extensions (KTX) APIs in `com.google.firebase:firebase-perf-ktx` are
  now deprecated. As early as April 2024, we'll no longer release KTX modules. For details, see the
  [FAQ about this initiative](https://firebase.google.com/docs/android/kotlin-migration)

# 20.4.1
* [changed] Updated `firebase-sessions` dependency to v1.0.2
* [fixed] Make fireperf data collection state is reliable for Firebase Sessions library.

# 20.4.0
* [feature] Integrated with Firebase sessions library to enable upcoming features related to
  session-based performance metrics.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-performance` library. The Kotlin extensions library has no additional
updates.

# 20.3.3
* [fixed] Fixed app start trace creation where some measured time could be NULL (#4730).
* [changed] Adjusted default behavior when remote config fetch fails.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-performance` library. The Kotlin extensions library has no additional
updates.

# 20.3.2
* [changed] Updated JavaLite, protoc, protobuf-java-util to 3.21.11.
* [changed] Updated [perfmon] to use double-precision for sampling.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-performance` library. The Kotlin extensions library has no additional
updates.

# 20.3.1
* [changed] Migrated [perfmon] to use standard Firebase executors.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-performance` library. The Kotlin extensions library has no additional
updates.

# 20.3.0
* [fixed] Fixed a `NullPointerException` crash when instrumenting screen
  traces on Android 7, 8, and 9.
  (#4146)


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-performance` library. The Kotlin extensions library has the following
additional updates:

* [feature] Added a
  [`trace(String, Trace.() -> T)`](/docs/reference/kotlin/com/google/firebase/perf/ktx/package-summary#trace(kotlin.String,kotlin.Function1))
  extension function to create a custom trace with the specified name.

# 20.2.0
* [unchanged] Updated to accommodate the release of the updated
  [perfmon] Kotlin extensions library.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-performance` library. The Kotlin extensions library has the following
additional updates:

* [feature] Firebase now supports Kotlin coroutines.
  With this release, we added
  [`kotlinx-coroutines-play-services`](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-play-services/){: .external}
  to `firebase-performance-ktx` as a transitive dependency, which exposes the
  `Task<T>.await()` suspend function to convert a
  [`Task`](https://developers.google.com/android/guides/tasks) into a Kotlin
  coroutine.

# 20.1.1
* [changed] Updated dependency of `play-services-basement` to its latest
  version (v18.1.0).


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-performance` library. The Kotlin extensions library has no additional
updates.

# 20.1.0
* [feature] Added support for out-of-the-box measurement of screen performance
  metrics for [Fragments](//developer.android.com/guide/fragments){: .external}.
  For more details, visit
  [Learn about screen rendering performance data](/docs/perf-mon/screen-traces?platform=android).
* [fixed] Fixed a bug where screen traces were not capturing frame metrics for
  multi-Activity apps.
* [fixed] Excluded custom attributes that have key/value lengths of 0.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-performance` library. The Kotlin extensions library has no additional
updates.

# 20.0.6
* [fixed] Fixed a null pointer exception (NPE) when instrumenting network
  requests.
  (#3406)
* [fixed] Fixed a bug where incorrect session IDs were associated with some
  foreground and background traces.
* [changed] Updated dependencies of `play-services-basement`,
  `play-services-base`, and `play-services-tasks` to their latest versions
  (v18.0.0, v18.0.1, and v18.0.1, respectively). For more information, see the
  [note](#basement18-0-0_base18-0-1_tasks18-0-1) at the top of this release
  entry.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-performance` library. The Kotlin extensions library has no additional
updates.

# 20.0.5
* [feature] Enabled global custom attributes for network request traces.
* [fixed] Updated log statement to differentiate an event being dropped due to
  rate limiting and sampling.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-performance` library. The Kotlin extensions library has no additional
updates.

# 20.0.4
* [changed] Improved [perfmon] start up time by 25%. This improvement was
  achieved by moving some component initialization to background threads.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-performance` library. The Kotlin extensions library has no additional
updates.

# 20.0.3
* [changed] [perfmon] now has a random delay of 5 to 30 seconds before
  fetching [remote_config] upon app startup.
* [fixed] Added a validation to stop screen traces with 0 total frames from
  being sent to the backend.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-performance` library. The Kotlin extensions library has no additional
updates.

# 20.0.2
* [fixed] Fixed inaccurate calculation of screen activity metrics for
  multi-activity apps.
  (#2672)
  Note: You may see some changes in data for frozen frames and slow rendering
  metrics.
* [fixed] Fixed issue where screen traces were not being tracked for Android
  API levels 23 and below.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-performance` library. The Kotlin extensions library has no additional
updates.

# 20.0.1
* [feature] Logs for [firebase_perfmon] now contain URLs to view
  performance data in the [name_appmanager].
* [fixed] Fixed `RateLimiter` replenishment logic and unit alignment.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-performance` library. The Kotlin extensions library has no additional
updates.

# 20.0.0
* [feature] Introduce Dagger as a dependency injection framework for some
  parts of the code.
* [changed] Improved the code organization of the SDK (package restructure,
  code conventions, remove unncessary annotations).
* [changed] Improve the launch time of the SDK.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-performance` library. The Kotlin extensions library has no additional
updates.

# 19.1.1
* [feature] The [firebase_perfmon] SDK is now
  [open sourced](//github.com/firebase/firebase-android-sdk/tree/master/firebase-perf){: .external}.
* [fixed] Fixed issue on the console logger to avoid throwing
    `UnknownFormatConversionException`.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-performance` library. The Kotlin extensions library has no additional
updates.

# 19.1.0
* [changed] Removed GMS dependency from [perfmon]. Google Play services
  installation is no longer required to use [perfmon].
* [changed] Improved performance event dispatch wait time from 2 hours to
  30 seconds.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-performance` library. The Kotlin extensions library has no additional
updates.

# 19.0.11
* [fixed] Upgraded protobuf dependency to the latest released version
  (#2158)


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-performance` library. The Kotlin extensions library has no additional
updates.

# 19.0.10
Note: We recommend using [perfmon] Gradle plugin v1.3.4+ with this version of
the [perfmon] SDK and above.

* [changed] Integrated with the `firebase-datatransport` library for
  performance log dispatch mechanism.
* [fixed] Synchronized the access to fix a race condition that was causing a
  `NullPointerException` when making network requests.
  (#2096)


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-performance` library. The Kotlin extensions library has no additional
updates.

# 19.0.9
* [fixed] Created lazy dependency on [firebase_remote_config] to avoid
  main thread contention issue.
  (#1810)
* [changed] Updated the protocol buffer dependency to the
  `protobuf-javalite` artifact to allow for backward compatibility.
* [changed] Removed Guava dependency from the SDK to avoid symbol collision
  with any other SDKs.
* [changed] Removed proguarding for SDK; logcat messages will show original
  class paths for debugging.
* [changed] Improved build configurations and dependencies to reduce SDK
  size.


## Kotlin
* [feature] The [firebase_perfmon] Android library with Kotlin
  extensions is now available. The Kotlin extensions library transitively
  includes the base `firebase-performance` library. To learn more,  visit the
  [[perfmon] KTX documentation](/docs/reference/kotlin/com/google/firebase/perf/ktx/package-summary).

# 19.0.8
* [changed] Updated the
  [logging message](/docs/perf-mon/get-started-android#view-log-messages)
  for performance events.
* [fixed] Silenced [firebase_remote_config] logging triggered by
  [firebase_perfmon].
  (#403)
* [fixed] Removed unnecessary logging. [perfmon] now only logs debug
  information if the `firebase_performance_logcat_enabled` setting is `true` in
  `AndroidManifest.xml`. Visit the documentation for details about
  explicitly [enabling debug logging](/docs/perf-mon/get-started-android#view-log-messages).
* [changed] Migrated to use the [firebase_installations] service _directly_
  instead of using an indirect dependency via the Firebase Instance ID SDK.

  {% include "docs/reference/android/client/_includes/_iid-indirect-dependency-solutions.html" %}

# 19.0.7
* [changed] Updated dependency on the Firebase Instance ID library to v20.1.5,
  which is a step towards a direct dependency on the [firebase_installations]
  service in a future release.

  This update to `firebase-iid` v20.1.5 fixed the following GitHub issues:
  [#1454](//github.com/firebase/firebase-android-sdk/issues/1454),
  [#1397](//github.com/firebase/firebase-android-sdk/issues/1397), and
  [#1339](//github.com/firebase/firebase-android-sdk/issues/1339).

# 19.0.6
* [fixed] Fixed an NPE crash when calling `trace.stop()`.
  (#1383)

# 19.0.5
* [fixed] Muted logcat logging for [firebase_perfmon] when
  `firebase_performance_logcat_enabled` is not set or set to false.
  ([#403](//github.com/firebase/firebase-android-sdk/issues/403))
* [fixed] Skipped automatic performance event creation when
  `firebase_performance_collection_enabled` is set to false.
* [changed] Internal infrastructure improvements.

# 19.0.4
* [changed] Improved internal infrastructure to work better with
  [firebase_remote_config].

# 19.0.3
* [changed] Internal infrastructure improvements.

# 19.0.2
* [changed] Internal infrastructure improvements.

# 19.0.1
* [changed] Internal infrastructure improvements.

# 19.0.0
* [changed] Versioned to add nullability annotations to improve the Kotlin
  developer experience. No other changes.

# 18.0.1
* [fixed] Fixed an `IllegalStateException` that was thrown when an activity
  with hardware acceleration disabled was stopped.

# 17.0.2
* [fixed] Fixed a `Null Pointer Exception` that was being observed on certain Android 7.0 devices.
* [fixed] Updates to make [perfmon] work better with the latest version of
  [firebase_remote_config].

# 17.0.0
* [removed] Removed the deprecated counter API. Use metrics API going forward.

# 16.2.5
* [fixed] Fixed a bug that was causing apps using multiple processses to
  throw an `IllegalStateException` in the non-main processes.

# 16.2.4
* [fixed] Fixed a bug that was causing a `NoClassDefFoundError` to be thrown
  which resulted in intermittent app crashes.
* [fixed] Updates to make [perfmon] work better with the latest version of
  [firebase_remote_config].
* [changed] [firebase_perfmon] no longer depends on [firebase_analytics].

# 16.2.3
* [fixed] Bug fixes.

# 16.2.1
* [fixed] SDK size is now smaller.

# 16.2.0
* [feature] Introduces the Sessions feature, which gives developers access to actionable insights about data captured using [perfmon].
* [fixed] Minor bug fixes and improvements.

# 16.1.0
* [fixed] Fixed a `SecurityException` crash on certain devices that do not have Google Play Services on them.

