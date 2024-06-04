# Unreleased


# 16.0.0-beta13
* [changed] Bump internal dependencies


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-appdistribution-api` library. The Kotlin extensions library has no additional
updates.

# 16.0.0-beta12
* [unchanged] Updated to accommodate the release of the updated
  [appdistro] library.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-appdistribution-api` library. The Kotlin extensions library has no additional
updates.

# 16.0.0-beta11
* [changed] Added Kotlin extensions (KTX) APIs from
  `com.google.firebase:firebase-appdistribution-api-ktx`
  to `com.google.firebase:firebase-appdistribution-api` under the
  `com.google.firebase.appdistribution` package.
  For details, see the
  [FAQ about this initiative](https://firebase.google.com/docs/android/kotlin-migration)
* [deprecated] All the APIs from `com.google.firebase:firebase-appdistribution-api-ktx` have been
  added to
  `com.google.firebase:firebase-appdistribution-api` under the
  `com.google.firebase.appdistribution` package,
  and all the Kotlin extensions (KTX) APIs in `com.google.firebase:firebase-appdistribution-api-ktx`
  are now deprecated. As early as April 2024, we'll no longer release KTX modules. For details,
  see the
  [FAQ about this initiative](https://firebase.google.com/docs/android/kotlin-migration)


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-appdistribution-api` library. The Kotlin extensions library has no additional
updates.

# 16.0.0-beta09
* [feature] Improved development mode to allow all API calls to be made without having to sign in.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-appdistribution-api` library. The Kotlin extensions library has no
additional updates.

# 16.0.0-beta08
* [fixed] Fixed an issue where a crash happened whenever a feedback
  notification was shown on devices running Android 4.4 and lower.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-appdistribution-api` library. The Kotlin extensions library has no
additional updates.

# 16.0.0-beta07
* [feature] Added support for testers to attach JPEG screenshots to their
  feedback.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-appdistribution-api` library. The Kotlin extensions library has no
additional updates.

# 16.0.0-beta06
* [feature] Added support for in-app tester feedback. To learn more, see
  [Collect feedback from testers](/docs/app-distribution/collect-feedback-from-testers?platform=android).
* [fixed] Fixed a bug where only the last listener added to an `UpdateTask`
  using `addOnProgressListener()` would receive updates.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-appdistribution-api` library. The Kotlin extensions library has no additional
updates.

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

