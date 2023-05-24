# Unreleased
Note: We did some super cool stuff here!

* [feature] Added support for disjunctions in queries (`OR` queries).

* [feature] Firebase now supports Kotlin coroutines.
  With this release, we added
  [`kotlinx-coroutines-play-services`](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-play-services/){: .external}
  to `firebase-firestore-ktx` as a transitive dependency, which exposes the
  `Task<T>.await()` suspend function to convert a
  [`Task`](https://developers.google.com/android/guides/tasks) into a Kotlin
  coroutine.

* [fixed] An issue on GitHub [#123](//github.com/firebase/firebase-android-sdk/issues/number)

* [removed] Removed some old stuff (#562)

* [feature] Added this thing we wanted
  [#444](//github.com/firebase/firebase-android-sdk/issues/number){: .external}

* [feature] Added
  [`Query.snapshots()`](/docs/reference/kotlin/com/google/firebase/firestore/ktx/package-summary#snapshots_1)
  and
  [`DocumentReference.snapshots()`](/docs/reference/kotlin/com/google/firebase/firestore/ktx/package-summary#snapshots)
  Kotlin Flows to listen for realtime updates.

* [fixed] Fixed an issue in `waitForPendingWrites()` that could lead to a
  `NullPointerException`.

* [feature] Added
  [`Query.whereNotIn()`](/docs/reference/android/com/google/firebase/firestore/Query#whereNotIn(java.lang.String,%20java.util.List<?%20extends%20java.lang.Object>))
  and
  [`Query.whereNotEqualTo()`](/docs/reference/android/com/google/firebase/firestore/Query#whereNotEqualTo(java.lang.String,%20java.lang.Object))
  query operators.

  * `Query.whereNotIn()` finds documents where a specified field's value is
    not in a specified array.
  * `Query.whereNotEqualTo()` finds documents where a specified field's value
    does not equal the specified value.

  Neither query operator finds documents where the specified field isn't
  present.

* [unchanged] Idk ig we did some stuff

* [removed] some stuff that we didn't really like got removed

## Kotlin
* [feature] Added
  [`Query.dataObjects<T>()`](/docs/reference/kotlin/com/google/firebase/firestore/ktx/package-summary#dataObjects)
  and
  [`DocumentReference.dataObjects<T>()`](/docs/reference/kotlin/com/google/firebase/firestore/ktx/package-summary#dataObjects_1)
  Kotlin Flows to listen for realtime updates and convert its values to a
  specific type.

# 24.5.0
* [fixed] Fixed stack overflow caused by deeply nested server timestamps.
  (GitHub [#4702](//github.com/firebase/firebase-android-sdk/issues/4702){: .external})

## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.