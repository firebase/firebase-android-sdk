# Unreleased


# 21.0.0
* [changed] Bump internal dependencies


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-functions` library. The Kotlin extensions library has no additional
updates.

# 20.4.0
* [changed] Added Kotlin extensions (KTX) APIs from `com.google.firebase:firebase-functions-ktx`
  to `com.google.firebase:firebase-functions` under the `com.google.firebase.functions` package.
  For details, see the
  [FAQ about this initiative](https://firebase.google.com/docs/android/kotlin-migration)
* [deprecated] All the APIs from `com.google.firebase:firebase-functions-ktx` have been added to
  `com.google.firebase:firebase-functions` under the `com.google.firebase.functions` package,
  and all the Kotlin extensions (KTX) APIs in `com.google.firebase:firebase-functions-ktx` are
  now deprecated. As early as April 2024, we'll no longer release KTX modules. For details, see the
  [FAQ about this initiative](https://firebase.google.com/docs/android/kotlin-migration)


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-functions` library. The Kotlin extensions library has no additional
updates.

# 20.3.1
* [changed] Added support for App Check limited-use tokens in HTTPS Callable Functions.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-functions` library. The Kotlin extensions library has no additional
updates.

# 20.3.0
* [changed] Internal changes to ensure alignment with other SDK releases.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-functions` library. The Kotlin extensions library has no additional
updates.

# 20.2.2
* [changed] Moved Task continuations off the main thread.
* [changed] Internal infrastructure improvements.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-functions` library. The Kotlin extensions library has no additional
updates.

# 20.2.1
* [changed] Updated dependency of `firebase-iid` to its latest
  version (v21.1.0).


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-functions` library. The Kotlin extensions library has no additional
updates.

# 20.2.0
* [unchanged] Updated to accommodate the release of the updated
  [functions_client] Kotlin extensions library.


## Kotlin
The Kotlin extensions library transitively includes the updated
  `firebase-functions` library. The Kotlin extensions library has the following
  additional updates:

* [feature] Firebase now supports Kotlin coroutines.
  With this release, we added
  [`kotlinx-coroutines-play-services`](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-play-services/){: .external}
  to `firebase-functions-ktx` as a transitive dependency, which exposes the
  `Task<T>.await()` suspend function to convert a
  [`Task`](https://developers.google.com/android/guides/tasks) into a Kotlin
  coroutine.

# 20.1.1
* [changed] Updated dependency of `play-services-basement` to its latest
  version (v18.1.0).


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-functions` library. The Kotlin extensions library has no additional
updates.

# 20.1.0
* [feature] Added a new method
  [`getHttpsCallableFromUrl(java.net.URL)`](/docs/reference/android/com/google/firebase/functions/FirebaseFunctions#public-httpscallablereference-gethttpscallablefromurl-url-url)
  to create callables with URLs.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-functions` library. The Kotlin extensions library has no additional
updates.

# 20.0.2
* [changed] Updated dependencies of `play-services-basement`,
  `play-services-base`, and `play-services-tasks` to their latest versions
  (v18.0.0, v18.0.1, and v18.0.1, respectively). For more information, see the
  [note](#basement18-0-0_base18-0-1_tasks18-0-1) at the top of this release
  entry.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-functions` library. The Kotlin extensions library has no additional
updates.

# 20.0.1
* [fixed] Fixed an issue that prevented functions from proceeding after
  [app_check] failures.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-functions` library. The Kotlin extensions library has no additional
updates.

# 20.0.0
* [feature] Added abuse reduction features.
* [changed] Internal changes to support dynamic feature modules.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-functions` library. The Kotlin extensions library has no additional
updates.

# 19.2.0
* [feature] Added support for custom domains, [`FirebaseFunctions#getInstance()`](/docs/reference/android/com/google/firebase/functions/FirebaseFunctions#getInstance(java.lang.String)).


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-functions` library. The Kotlin extensions library has no additional
updates.

# 19.1.0
* [feature] Added support for connecting to the Firebase Emulator Suite via
  a new method,
  [`FirebaseFunctions#UseEmulator()`](/docs/reference/android/com/google/firebase/functions/FirebaseFunctions#useEmulator(java.lang.String,%20int)).
* [deprecated] Deprecated the `useFunctionsEmulator(String)` method.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-functions` library. The Kotlin extensions library has no additional
updates.

# 19.0.2
* [changed] Internal changes to ensure functionality alignment with other SDK releases.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-functions` library. The Kotlin extensions library has no additional
updates.

# 19.0.1
* [unchanged] Updated to accommodate the release of the [functions_client]
  Kotlin extensions library.


## Kotlin
* [feature] The beta release of a [functions_client] Android library with
  Kotlin extensions is now available. The Kotlin extensions library transitively
  includes the base `firebase-functions` library. To learn more,  visit the
  [[cloud_functions] KTX documentation](/docs/reference/kotlin/com/google/firebase/functions/ktx/package-summary).

# 19.0.0
* [changed] Versioned to add nullability annotations to improve the Kotlin
  developer experience. No other changes.

# 18.1.0
* [feature] Added
  [`getTimeout`](/docs/reference/android/com/google/firebase/functions/HttpsCallableReference)
  method to get the timeout for a callable. For more details, refer to
  [GitHub PR #574](//github.com/firebase/firebase-android-sdk/pull/574).

# 17.0.0
* [changed] Updated minSdkVersion to API level 16.

# 16.3.0
* [changed] Changed the default timeout for callable functions to 70 seconds
  ([#2329](//github.com/firebase/firebase-android-sdk/pull/224)).
* [feature] Added
  [`setTimeout`](/docs/reference/android/com/google/firebase/functions/HttpsCallableReference)
  and
  [`withTimeout`](/docs/reference/android/com/google/firebase/functions/HttpsCallableReference)
  methods to change the timeout for a callable
  ([#2329](//github.com/firebase/firebase-android-sdk/pull/224)).

# 16.1.0
* [feature] `FirebaseFunctions.getInstance()` now allows passing in an
optional region to override the default "us-central1".
* [feature] New `useFunctionsEmulator` method allows testing against a local
instance of the [Cloud Functions Emulator](https://firebase.google.com/docs/functions/local-emulator).

