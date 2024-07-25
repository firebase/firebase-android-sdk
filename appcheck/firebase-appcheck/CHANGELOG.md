# Unreleased


# 18.0.0
* [changed] Bump internal dependencies
* [changed] Internal support for `SafetyNet` has been dropped, as the [SafetyNet Attestation API
has been deprecated.](https://developer.android.com/privacy-and-security/safetynet/deprecation-timeline#safetynet_attestation_deprecation_timeline)


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-appcheck` library. The Kotlin extensions library has no additional
updates.

# 17.1.2
* [changed] Bump internal dependencies.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-appcheck` library. The Kotlin extensions library has no additional
updates.

# 17.1.1
* [fixed] Fixed a bug causing internal tests to depend directly on `firebase-common`.
* [fixed] Fixed client-side throttling in Play Integrity flows.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-appcheck` library. The Kotlin extensions library has no additional
updates.

# 17.1.0
* [changed] Added Kotlin extensions (KTX) APIs from `com.google.firebase:firebase-appcheck-ktx`
  to `com.google.firebase:firebase-appcheck` under the `com.google.firebase.appcheck` package.
  For details, see the
  [FAQ about this initiative](https://firebase.google.com/docs/android/kotlin-migration)
* [deprecated] All the APIs from `com.google.firebase:firebase-appcheck-ktx` have been added to
  `com.google.firebase:firebase-appcheck` under the `com.google.firebase.appcheck` package,
  and all the Kotlin extensions (KTX) APIs in `com.google.firebase:firebase-appcheck-ktx` are
  now deprecated. As early as April 2024, we'll no longer release KTX modules. For details, see the
  [FAQ about this initiative](https://firebase.google.com/docs/android/kotlin-migration)


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-appcheck` library. The Kotlin extensions library has no additional
updates.

# 17.0.1
* [changed] Internal updates to allow Firebase SDKs to obtain limited-use tokens.

# 17.0.0
* [feature] Added [`getLimitedUseAppCheckToken()`](/docs/reference/android/com/google/firebase/appcheck/FirebaseAppCheck#getLimitedUseAppCheckToken())
  for obtaining limited-use tokens for protecting non-Firebase backends.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-appcheck` library. The Kotlin extensions library has no additional
updates.

# 16.1.2
* [unchanged] Updated to keep [app_check] SDK versions aligned.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-appcheck` library. The Kotlin extensions library has no additional
updates.

# 16.1.1
* [changed] Migrated [app_check] SDKs to use standard Firebase executors.
  (GitHub [#4431](//github.com/firebase/firebase-android-sdk/issues/4431){: .external}
  and
  [#4449](//github.com/firebase/firebase-android-sdk/issues/4449){: .external})
* [changed] Moved Task continuations off the main thread.
  (GitHub [#4453](//github.com/firebase/firebase-android-sdk/issues/4453){: .external})


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-appcheck` library. The Kotlin extensions library has no additional
updates.

# 16.1.0
* [unchanged] Updated to accommodate the release of the updated
  [app_check] Kotlin extensions library.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-appcheck` library. The Kotlin extensions library has the following
additional updates:

* [feature] Firebase now supports Kotlin coroutines.
  With this release, we added
  [`kotlinx-coroutines-play-services`](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-play-services/){: .external}
  to `firebase-appcheck-ktx` as a transitive dependency, which exposes the
  `Task<T>.await()` suspend function to convert a
  [`Task`](https://developers.google.com/android/guides/tasks) into a Kotlin
  coroutine.

# 16.0.1
* [changed] Updated dependency of `play-services-basement` to its latest
  version (v18.1.0).

# 16.0.0
* [changed] [app_check] has exited beta and is now generally available for
  use.
* [feature] Added support for
  [Play Integrity](https://developer.android.com/google/play/integrity) as an
  attestation provider.

# 16.0.0-beta06
* [fixed] Fixed a bug in the [app_check] token refresh flow when using a
  custom provider.

# 16.0.0-beta05
* [changed] Internal improvements.

# 16.0.0-beta04
* [changed] Improved error handling logic by minimizing the amount of requests
  that are unlikely to succeed.
* [fixed] Fixed heartbeat reporting.

# 16.0.0-beta03
* [changed] Added `X-Android-Package` and `X-Android-Cert` request headers to
  [app_check] network calls.

# 16.0.0-beta02
* [feature] Added [`getAppCheckToken()`](/docs/reference/android/com/google/firebase/appcheck/FirebaseAppCheck#getAppCheckToken(boolean)),
  [`AppCheckTokenListener`](/docs/reference/android/com/google/firebase/appcheck/FirebaseAppCheck.AppCheckListener),
  and associated setters and removers for developers to request and observe
  changes to the [app_check] token.

# 16.0.0-beta01
* [feature] Initial beta release of the [app_check] SDK with abuse reduction
  features.

