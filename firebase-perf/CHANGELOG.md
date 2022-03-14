# Firebase Performance Monitoring SDK (aka `firebase-perf` and `firebase-perf-ktx`)

Refer [GMaven](https://maven.google.com/web/index.html?q=firebase-perf#com.google.firebase:firebase-perf) for the release artifacts and [Firebase Android Release Notes](https://firebase.google.com/support/release-notes/android) for the release info.

## Example Changelog

```
## vX.Y.Z (MXX)

#### Android library / Kotlin extensions

* {{feature}} You can now do this cool new thing. For more details, refer to the [documentation](docs/path).

* {{fixed}} Fixed the `methodFoo` warning.

* {{changed}} You need to do _this_ instead of _that_.

```

> **Note:** Refer go/firebase-android-release for `MXX` info.

## Unreleased

*   {{fixed}} Fixed a bug where screen traces were not capturing frame metrics for multi-activity apps.

## Released

## v20.0.6 (M112)

#### Android library

*   {{fixed}} Fixed a null pointer exception (NPE) when instrumenting network requests. ([GitHub Issue #3406](//github.com/firebase/firebase-android-sdk/issues/3406))
*   {{fixed}} Fixed a bug where incorrect session IDs were associated with some foreground and background traces.

#### Kotlin extensions

The Kotlin extensions library transitively includes the updated
`firebase-performance` library. The Kotlin extensions library has no additional
updates.

## v20.0.5 (M111)

#### Android library

*   {{feature}} Enable global custom attributes on Network Requests
*   {{fixed}} Update log statement to differentiate event drop because of rate limiting and sampling.

#### Kotlin extensions

The Kotlin extensions library transitively includes the updated
`firebase-performance` library. The Kotlin extensions library has no additional
updates.

## v20.0.1 (M97)

#### Android library

*   {{feature}} Firebase Performance logs now contain URLs to see the performance 
    data on the Firebase console.
*   {{fixed}} Fixed RateLimiter replenishment logic and unit alignment.

#### Kotlin extensions

The Kotlin extensions library transitively includes the updated
`firebase-performance` library. The Kotlin extensions library has no additional
updates.

## v20.0.0 (M95)

#### Android library

*   {{feature}} Introduce Dagger as a dependency injection framework for some
    parts of the code.
*   {{changed}} Improved the code organization of the SDK (package restructure,
    code conventions, remove unncessary annotations).
*   {{changed}} Improve the launch time of the SDK.

#### Kotlin extensions

The Kotlin extensions library transitively includes the updated
`firebase-performance` library. The Kotlin extensions library has no additional
updates.

## v19.1.1 (M89)

#### Android library

*   {{feature}} The Firebase Performance Monitoring SDK is now open sourced.
*   {{changed}} Improved performance event dispatch wait time from 2 hours to
    30 seconds.

#### Kotlin extensions

The Kotlin extensions library transitively includes the updated
`firebase-performance` library. The Kotlin extensions library has no additional
updates.

## v19.1.0 (M87)

#### Android library

*   {{changed}} Removed GMS dependency from {{perfmon}}. Google Play services
    installation is no longer required to use firebase performance.

*   {{changed}} Improved performance event dispatch wait time from 2 hours to
    30 seconds.

#### Kotlin extensions

The Kotlin extensions library transitively includes the updated
`firebase-performance` library. The Kotlin extensions library has no additional
updates.

## v19.0.11 (M85)

#### Android library

*   {{fixed}} Upgraded protobuf dependency to the latest released version
    ([GitHub Issue #2158](//github.com/firebase/firebase-android-sdk/issues/2158))

#### Kotlin extensions

The Kotlin extensions library transitively includes the updated
`firebase-performance` library. The Kotlin extensions library has no additional
updates.

## v19.0.10 (M83)

#### Android library
Note: We recommend using {{perfmon}} Gradle plugin version v1.3.4 or above with
this version of SDK.

*  {{changed}} Integrated with the `firebase-datatransport` library for
    performance log dispatch mechanism.

*  {{fixed}} Synchronized the access to fix a race condition that was causing a
    `NullPointerException` when making network requests.
    ([GitHub Issue #2096](//github.com/firebase/firebase-android-sdk/issues/2096))

#### Kotlin extensions

The Kotlin extensions library transitively includes the updated
`firebase-performance` library. The Kotlin extensions library has no additional
updates.

## v19.0.9 (M80)

#### Android library

*  {{fixed}} Created lazy dependency on {{firebase_remote_config}} to avoid
    main thread contention issue. ([GitHub Issue #1810](//github.com/firebase/firebase-android-sdk/issues/1810))

*  {{changed}} Updated the protocol buffer dependency to the
   `protobuf-javalite` artifact to allow for backward compatibility.

*  {{changed}} Removed Guava dependency from the SDK to avoid symbol collision with any other SDKs.

*  {{changed}} Removed proguarding for SDK; logcat messages will show original class paths for debugging.

*  {{changed}} Improved build configurations and dependencies to reduce SDK size.

#### Kotlin extensions

* {{feature}} The {{firebase_perfmon}} Android library with Kotlin
  extensions is now available. The Kotlin extensions library transitively
  includes the base `firebase-performance` library. To learn more,  visit the
  [{{perfmon}} KTX documentation](/docs/reference/kotlin/com/google/firebase/perf/ktx/package-summary).

## v19.0.8 (M75)

#### Android library

* {{changed}} Updated the
  [logging message](/docs/perf-mon/get-started-android#view-log-messages)
  for performance events.

* {{fixed}} Silenced {{firebase_remote_config}} logging triggered by
  {{firebase_perfmon}}.
  ([GitHub Issue #403](//github.com/firebase/firebase-android-sdk/issues/403))

* {{fixed}} Removed unnecessary logging. {{perfmon}} now only logs debug
  information if the `firebase_performance_logcat_enabled` setting is `true` in
  `AndroidManifest.xml`. Visit the documentation for details about
  explicitly [enabling debug logging](/docs/perf-mon/get-started-android#view-log-messages).

* {{changed}} Migrated to use the {{firebase_installations}} service _directly_
  instead of using an indirect dependency via the Firebase Instance ID SDK.

## v19.0.7 (M69)

#### Android library

* {{changed}} Updated dependency on the Firebase Instance ID library to v20.1.5,
  which is a step towards a direct dependency on the {{firebase_installations}}
  service in a future release.

  This update to `firebase-iid` v20.1.5 fixed the following GitHub issues:
  [#1454](//github.com/firebase/firebase-android-sdk/issues/1454),
  [#1397](//github.com/firebase/firebase-android-sdk/issues/1397), and
  [#1339](//github.com/firebase/firebase-android-sdk/issues/1339).

## v19.0.6 (M68)

#### Android library

* {{fixed}} Fixed an NPE crash when calling `trace.stop()`.
  ([GitHub Issue #1383](//github.com/firebase/firebase-android-sdk/issues/1383))

## v19.0.5 (M62)

#### Android library

* {{fixed}} Muted logcat logging for {{firebase_perfmon}} when
`firebase_performance_logcat_enabled` is not set or set to false.
([#403](//github.com/firebase/firebase-android-sdk/issues/403))
* {{fixed}} Skipped automatic performance event creation when
`firebase_performance_collection_enabled` is set to false.
* {{changed}} Internal infrastructure improvements.

## v19.0.4 (M61)

#### Android library

* {{changed}} Improved internal infrastructure to work better with
  {{firebase_remote_config}}.

----

**NOTE:** For some historical reasons below internal versions may or may not correlate to an externally released version. However, this document keeps track of what internal version was released as an external version so that its easier to figure out what version of the SDK we were dealing with (and whether it was publicly released). Unfortunately, we did not keep records of what internal version correlated to an external version until `v16.2.0`, and so the values before that were procured as a result of manually going through history and correlating release dates.

## 19.0.3 (M60.1): 1.0.0.282466371

#### Android library

* {{changed}} Internal infrastructure improvements.

## 19.0.2 (M59): 1.0.0.277188197

#### Android library

* {{changed}} Internal infrastructure improvements.

## 19.0.1 (M57): 1.0.0.272275548

#### Android library

* {{changed}} Internal infrastructure improvements.

## 19.0.0 (M53): 

#### Android library

* {{changed}} Versioned to add nullability annotations to improve the Kotlin
  developer experience. No other changes.

## 18.0.1 (M50): 1.0.0.252929170

#### Android library

* {{fixed}} Fixed an `IllegalStateException` that was thrown when an activity
  with hardware acceleration disabled was stopped.
  
## 18.0.0 (Android X - Clone of 17.0.2): 1.0.0.249530108

## 17.0.2 (M48): 1.0.0.249530108

#### Android library

* {{fixed}} Fixed a `Null Pointer Exception` that was being observed on certain Android 7.0 devices.

* {{fixed}} Updates to make {{perfmon}} work better with the latest version of
  {{firebase_remote_config}}.
  
## 17.0.1 (Unreleased): 1.0.0.242580265

## 17.0.0 (M47): 1.0.0.242580265

#### Android library

* {{removed}} Removed the deprecated counter API. Use metrics API going forward.

## 16.2.5 (M46): 1.0.0.240228580

#### Android library

* {{fixed}} Fixed a bug that was causing apps using multiple processses to
  throw an `IllegalStateException` in the non-main processes.

## 16.2.4 (M44): 1.0.0.233854359

#### Android library

* {{fixed}} Fixed a bug that was causing a `NoClassDefFoundError` to be thrown
  which resulted in intermittent app crashes.

* {{fixed}} Updates to make {{perfmon}} work better with the latest version of
  {{firebase_remote_config}}.

* {{changed}} {{firebase_perfmon}} no longer depends on {{firebase_analytics}}.

## 16.2.3 (M40): 1.0.0.225053256

#### Android library

* {{fixed}} Bug fixes.

## 16.2.2 (M38.1): 1.0.0.221486272

## 16.2.1 (M38): 1.0.0.221486272

#### Android library

* {{fixed}} SDK size is now smaller.

## 16.2.0 (M36): 1.0.0.217212991

#### Android library

* {{feature}} Introduces the Sessions feature, which gives developers access to actionable insights about data captured using {{perfmon}}.

* {{fixed}} Minor bug fixes and improvements.

## 16.1.1 (EAP): 1.0.0.212694509

## 16.1.0 (M30): 1.0.0.206222422

#### Android library

* {{fixed}} Fixed a `SecurityException` crash on certain devices that do not have Google Play Services on them.

## 16.0.0: 1.0.0.196558987

## 15.2.0: 1.0.0.194951622

## 15.1.0: 1.0.0.194607773

## 15.0.0: 1.0.0.184862077
