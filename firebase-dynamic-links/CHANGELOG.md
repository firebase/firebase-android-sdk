# Unreleased


# 22.1.0
* [changed] Added deprecation annotations to the public API. See https://firebase.google.com/support/dynamic-links-faq for further context.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-dynamic-links` library. The Kotlin extensions library has no additional
updates.

# 22.0.0
* [changed] Bump internal dependencies


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-dynamic-links` library. The Kotlin extensions library has no additional
updates.

# 21.2.0
* [changed] Added Kotlin extensions (KTX) APIs from `com.google.firebase:firebase-dynamic-links-ktx`
  to `com.google.firebase:firebase-dynamic-links` under the `com.google.firebase.dynamiclinks` package.
  For details, see the
  [FAQ about this initiative](https://firebase.google.com/docs/android/kotlin-migration)
* [deprecated] All the APIs from `com.google.firebase:firebase-dynamic-links-ktx` have been added to
  `com.google.firebase:firebase-dynamic-links` under the `com.google.firebase.dynamiclinks` package,
  and all the Kotlin extensions (KTX) APIs in `com.google.firebase:firebase-dynamic-links-ktx` are
  now deprecated. As early as April 2024, we'll no longer release KTX modules. For details, see the
  [FAQ about this initiative](https://firebase.google.com/docs/android/kotlin-migration)


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-dynamic-links` library. The Kotlin extensions library has no additional
updates.

# 21.1.0
* [unchanged] Updated to accommodate the release of the updated
  [ddls] Kotlin extensions library.


## Kotlin
The Kotlin extensions library transitively includes the updated
  `firebase-dynamic-links` library. The Kotlin extensions library has the
  following additional updates:

* [feature] Firebase now supports Kotlin coroutines.
  With this release, we added
  [`kotlinx-coroutines-play-services`](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-play-services/){: .external}
  to `firebase-dynamic-links-ktx` as a transitive dependency, which exposes the
  `Task<T>.await()` suspend function to convert a
  [`Task`](https://developers.google.com/android/guides/tasks) into a Kotlin
  coroutine.

# 21.0.2
* [changed] Updated dependency of `play-services-basement` to its latest
  version (v18.1.0).


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-dynamic-links` library. The Kotlin extensions library has no
additional updates.

# 21.0.1
* [changed] Updated dependencies of `play-services-basement`,
  `play-services-base`, and `play-services-tasks` to their latest versions
  (v18.0.0, v18.0.1, and v18.0.1, respectively). For more information, see the
  [note](#basement18-0-0_base18-0-1_tasks18-0-1) at the top of this release
  entry.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-dynamic-links` library. The Kotlin extensions library has no
additional updates.

# 21.0.0
* [changed] Due to its
  [dependency on Google Play services](/docs/android/android-play-services),
  this SDK now requires devices and emulators to target API level 19 (KitKat)
  or higher and to use Android 4.4 or higher.
* [fixed] Fixed non-null annotation.
  #2336


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-dynamic-links` library. The Kotlin extensions library has no
additional updates.

# 20.1.1
* [changed] Internal infrastructure improvements.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-dynamic-links` library. The Kotlin extensions library has no
additional updates.

# 20.1.0
* [feature] Added `getUtmParameters` method to `PendingDynamicLinkData`.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-dynamic-links` library. The Kotlin extensions library has no
additional updates.

# 20.0.0
* [changed] Internal infrastructure improvements.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-dynamic-links` library. The Kotlin extensions library has no
additional updates.

# 19.1.1
* [changed] Updated to support improvements in the KTX library (see below).


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-storage` library and has the following additional updates:

* [feature] Added API support for destructuring of
  [`ShortDynamicLink`](/docs/reference/kotlin/com/google/firebase/dynamiclinks/ShortDynamicLink)
  and
  [`PendingDynamicLinkData`](/docs/reference/kotlin/com/google/firebase/dynamiclinks/PendingDynamicLinkData).

# 19.1.0
* [feature] Added new getter methods to
  [`DynamicLink.Builder`](//firebase.google.com/docs/reference/android/com/google/firebase/dynamiclinks/DynamicLink.Builder)
  to improve Kotlin interop.


## Kotlin
* [feature] The beta release of a [ddls] Android library with
  Kotlin extensions is now available. The Kotlin extensions library transitively
  includes the base `firebase-dynamic-links` library. To learn more, visit the
  [[ddls] KTX documentation](/docs/reference/kotlin/com/google/firebase/dynamiclinks/ktx/package-summary).

# 19.0.0
* [changed] Versioned to add nullability annotations to improve the Kotlin
  developer experience. No other changes.

# 16.2.0
* [changed] Refactored code to ensure functionality alignment with other
  updated Firebase libraries.
* [changed] Updated minSdkVersion to API level 16.

# 16.1.7
* [changed] Internal refactor.

# 16.1.3
* [fixed] Fixed an issue that caused short link creation to fail when creating
links through `FirebaseDynamicLinks.getInstance().createDynamicLink().buildShortDynamicLink()`
using Google Play Services 13.2.80 and FDL SDK 16.1.0. The issue only occurred
when creating shortening links from parameters, links created using
preconstructed long links from `setLongLink()` were unaffected. This fix also
addresses issues in newer versions of Google Play Services.

