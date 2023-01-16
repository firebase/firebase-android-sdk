# Unreleased

# 16.0.0-beta05
* [unchanged] Updated to accommodate the release of the updated
  [appdistro] Kotlin extensions library.


## Kotlin
The Kotlin extensions library transitively includes the updated
  `firebase-appdistribution-api` library. The Kotlin extensions library has
  the following additional updates:

* [feature] Firebase now supports Kotlin coroutines.
  With this release, we added
  [`kotlinx-coroutines-play-services`](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-play-services/){: .external}
  to `firebase-appdistribution-api-ktx` as a transitive dependency, which
  exposes the `Task<T>.await()` suspend function to convert a
  [`Task`](https://developers.google.com/android/guides/tasks)
  into a Kotlin coroutine.

# 16.0.0-beta04
* [changed] Updated dependency of `play-services-basement` to its latest
  version (v18.1.0).


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-appdistribution-api` library. The Kotlin extensions library has no
additional updates.

# 16.0.0-beta03
* [feature] The [appdistro] SDK has been split into two libraries:

  * `firebase-appdistribution-api` - The API-only library<br>
    This new API-only library is functional only when the full
    [appdistro] SDK implementation (`firebase-appdistribution`) is present.
    `firebase-appdistribution-api` can be included in all
    [build variants](https://developer.android.com/studio/build/build-variants){: .external}.

  * `firebase-appdistribution` - The full SDK implementation<br>
    This full SDK implementation is optional and should only be included in
    pre-release builds.

  Visit the documentation to learn how to
  [add these SDKs](/docs/app-distribution/set-up-alerts?platform=android#add-appdistro)
  to your Android app.


## Kotlin
With the removal of the Kotlin extensions library
`firebase-appdistribution-ktx`, its functionality has been moved to the new
API-only library: `firebase-appdistribution-api-ktx`.

This new Kotlin extensions library transitively includes the
`firebase-appdistribution-api` library. The Kotlin extensions library has no
additional updates.
