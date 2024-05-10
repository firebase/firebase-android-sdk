# Unreleased


# 21.0.0
* [changed] Bump internal dependencies


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-database` library. The Kotlin extensions library has no additional
updates.

# 20.3.1
* [fixed] Fixed the `@Exclude` annotation doesn't been propagated to Kotlin's corresponding bridge methods. [#5626](//github.com/firebase/firebase-android-sdk/pull/5706)


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-database` library. The Kotlin extensions library has no additional
updates.

# 20.3.0
* [changed] Added Kotlin extensions (KTX) APIs from `com.google.firebase:firebase-database-ktx`
  to `com.google.firebase:firebase-database` under the `com.google.firebase.database` package.
  For details, see the
  [FAQ about this initiative](https://firebase.google.com/docs/android/kotlin-migration)
* [deprecated] All the APIs from `com.google.firebase:firebase-database-ktx` have been added to
  `com.google.firebase:firebase-database` under the `com.google.firebase.database` package,
  and all the Kotlin extensions (KTX) APIs in `com.google.firebase:firebase-database-ktx` are
  now deprecated. As early as April 2024, we'll no longer release KTX modules. For details, see the
  [FAQ about this initiative](https://firebase.google.com/docs/android/kotlin-migration)


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-database` library. The Kotlin extensions library has no additional
updates.

# 20.2.2
* [changed] Internal changes to ensure alignment with other SDK releases.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-database` library. The Kotlin extensions library has no additional
updates.

# 20.2.1
* [changed] Internal changes to ensure alignment with other SDK releases.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-database` library. The Kotlin extensions library has no additional
updates.

# 20.2.0
* [unchanged] Updated to accommodate the release of the updated
  [database] Kotlin extensions library.


## Kotlin
* [feature] Added
  [`Query.values<T>()`](/docs/reference/kotlin/com/google/firebase/database/ktx/package-summary#values)
  Kotlin Flows to listen for realtime updates and convert its values to a
  specific type.

# 20.1.0
* [unchanged] Updated to accommodate the release of the updated
[database] Kotlin extensions library.


## Kotlin
The Kotlin extensions library transitively includes the updated
  `firebase-database` library. The Kotlin extensions library has the following
  additional updates:

* [feature] Firebase now supports Kotlin coroutines.
  With this release, we added
  [`kotlinx-coroutines-play-services`](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-play-services/){: .external}
  to `firebase-database-ktx` as a transitive dependency, which exposes the
  `Task<T>.await()` suspend function to convert a
  [`Task`](https://developers.google.com/android/guides/tasks) into a Kotlin
  coroutine.
* [feature] Added
  [`Query.snapshots`](/docs/reference/kotlin/com/google/firebase/database/ktx/package-summary#snapshots)
  and
  [`Query.childEvents`](/docs/reference/kotlin/com/google/firebase/database/ktx/package-summary#childEvents)
  Kotlin Flows to listen to realtime events.

# 20.0.6
* [changed] Updated dependency of `play-services-basement` to its latest
  version (v18.1.0).
* [fixed] Fixed issue where `Query.get()` was propagating events to
  listeners on unrelated queries.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-database` library. The Kotlin extensions library has no additional
updates.

# 20.0.5
* [fixed] `Query.get` no longer throws "Client is offline" exception when local
  value is not available. Instead, it waits for a backend connection.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-database` library. The Kotlin extensions library has no additional
updates.

# 20.0.4
* [changed] Updated dependencies of `play-services-basement`,
  `play-services-base`, and `play-services-tasks` to their latest versions
  (v18.0.0, v18.0.1, and v18.0.1, respectively). For more information, see the
  [note](#basement18-0-0_base18-0-1_tasks18-0-1) at the top of this release
  entry.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-database` library. The Kotlin extensions library has no additional
updates.

# 20.0.3
* [fixed] Fixed a crash that prevented the SDK from connecting to the
backend if a credential refresh was unsuccesful.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-database` library. The Kotlin extensions library has no additional
updates.

# 20.0.2
* [fixed] The SDK can now continue to issue writes for apps that send
  invalid [app_check] tokens if [app_check] enforcement is not enabled.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-database` library. The Kotlin extensions library has no additional
updates.

# 20.0.1
* [fixed] Fixed an issue that prevented clients from connecting to the
  backend when the app used [app_check] without [auth].


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-database` library. The Kotlin extensions library has no additional
updates.

# 20.0.0
* [feature] Added abuse reduction features.
* [changed] Internal changes to support dynamic feature modules.
* [changed] Internal infrastructure improvements.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-database` library. The Kotlin extensions library has no additional
updates.

# 19.7.0
* [feature] Added [`Query#startAfter()`](/docs/reference/android/com/google/firebase/database/Query#startAfter(java.lang.String,%20java.lang.String))
  and [`Query#endBefore()`](/docs/reference/android/com/google/firebase/database/Query#endBefore(java.lang.String,%20java.lang.String))
  filters to help with paginated queries.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-database` library. The Kotlin extensions library has no additional
updates.

# 19.6.0
* [feature] Added [`DatabaseReference#get()`](/docs/reference/android/com/google/firebase/database/DatabaseReference#get())
  and [`Query#get()`](/docs/reference/android/com/google/firebase/database/Query#get()),
  which return data from the server even when older data is available in the local
  cache.
* [fixed] Fixed a crash that occured on some Pixel devices when closing the
  network connection.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-database` library. The Kotlin extensions library has no additional
updates.

# 19.5.1
* [fixed] Fixed a regression introduced in v19.3.0 that may cause assertion
  failures, especially when persistence is enabled.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-database` library. The Kotlin extensions library has no additional
updates.

# 19.5.0
* [feature] The SDK can now infer a default database URL even if it is omitted
in the project's configuration.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-database` library. The Kotlin extensions library has no additional
updates.

# 19.4.0
* [feature] Added support for connecting to the Firebase Emulator Suite via
  a new method,
  [`FirebaseDatabase#useEmulator()`](/docs/reference/android/com/google/firebase/database/FirebaseDatabase#useEmulator(java.lang.String,%20int)).


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-database` library. The Kotlin extensions library has no additional
updates.

# 19.3.1
* [changed] Added internal HTTP header to the WebChannel connection.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-database` library. The Kotlin extensions library has no additional
updates.

# 19.3.0
* [feature] Added [`ServerValue.increment()`](/docs/reference/android/com/google/firebase/database/ServerValue#increment(double))
  to support atomic field value increments without transactions.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-database` library. The Kotlin extensions library has no additional
updates.

# 19.2.1
* [changed] Internal changes to ensure functionality alignment with other SDK releases.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-database` library. The Kotlin extensions library has no additional
updates.

# 19.2.0
* [feature] Added support for type wildcards in
  [`GenericTypeIndicator`](/docs/reference/android/com/google/firebase/database/GenericTypeIndicator),
  expanding our custom class serialization to include classes with wildcard
  generics.


## Kotlin
* [feature] The beta release of a [database] Android library with
  Kotlin extensions is now available. The Kotlin extensions library transitively
  includes the base `firebase-database` library. To learn more,  visit the
  [[database] KTX documentation](/docs/reference/kotlin/com/google/firebase/database/ktx/package-summary).

# 19.1.0
* [feature] Added support for the [firebase_database] Emulator. To connect
  to the emulator, specify "http://<hostname>:<port>/?ns=<project_id>" as your
  Database URL (via [`FirebaseDatabase.getInstance(String)`](/docs/reference/android/com/google/firebase/database/FirebaseDatabase.html#getSdkVersion())).
  Note that if you are running the [database] Emulator on "localhost" and
  connecting from an app that is running inside an Android Emulator,
  the [database] Emulator host will be "10.0.2.2" followed by its port.

# 19.0.0
* [changed] Versioned to add nullability annotations to improve the Kotlin
  developer experience. No other changes.

# 18.0.1
* [changed] Internal changes to ensure functionality alignment with other SDK
  releases.
* [fixed] The SDK now reports the correct version number (via
  [`FirebaseDatabase.getSdkVersion()`](/docs/reference/android/com/google/firebase/database/FirebaseDatabase.html#getSdkVersion()`).

# 17.0.0
* [changed] Internal changes that rely on an updated API to obtain
  authentication credentials. If you use [firebase_auth], update to
  `firebase-auth` v17.0.0 or later to ensure functionality alignment.

# 16.0.6
* [fixed] Fixed a potential `NullPointerException` calling method
  `java.lang.String.toLowerCase`.
  (https://github.com/firebase/firebase-android-sdk/issues/179)

# 16.0.3
* [fixed] Fixed an initialization issue that prevented the Realtime Database
client from being initialized outside of Android's main thread.

# 16.0.2
* [fixed] This release includes minor fixes and improvements.

# 16.0.1
* [changed] Added `Nullability` annotations to all public API classes/methods.

