# Unreleased
* [changed] Migrated [app_check] SDKs to use standard Firebase executors. (#4431, #4449)
* [changed] Moved Task continuations off the main thread. (#4453)

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

