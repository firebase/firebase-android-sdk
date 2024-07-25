# Unreleased


# 21.0.0
* [changed] Bump internal dependencies


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-storage` library. The Kotlin extensions library has no additional
updates.

# 20.3.0
* [fixed] Fixed an issue where the wrong SDK version was being reported to the backend.
* [changed] Added Kotlin extensions (KTX) APIs from `com.google.firebase:firebase-perf-ktx`
  to `com.google.firebase:firebase-perf` under the `com.google.firebase.perf` package.
  For details, see the
  [FAQ about this initiative](https://firebase.google.com/docs/android/kotlin-migration)
* [deprecated] All the APIs from `com.google.firebase:firebase-perf-ktx` have been added to
  `com.google.firebase:firebase-perf` under the `com.google.firebase.perf` package,
  and all the Kotlin extensions (KTX) APIs in `com.google.firebase:firebase-perf-ktx` are
  now deprecated. As early as April 2024, we'll no longer release KTX modules. For details, see the
  [FAQ about this initiative](https://firebase.google.com/docs/android/kotlin-migration)


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-storage` library. The Kotlin extensions library has no additional
updates.

# 20.2.1
* [changed] Migrated `firebase-storage` SDK to use standard Firebase executors.
  (GitHub [#4830](//github.com/firebase/firebase-android-sdk/pull/4830){: .external})


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-storage` library. The Kotlin extensions library has no additional
updates.

# 20.2.0
* [changed] Internal changes to ensure alignment with other SDK releases.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-storage` library. The Kotlin extensions library has no additional
updates.

# 20.1.0
* [fixed] Fixed an issue that caused an infinite number of retries with no
  exponential backoff for `uploadChunk()`.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-storage` library. The Kotlin extensions library has the following
additional updates:

* [feature] Firebase now supports Kotlin coroutines.
  With this release, we added
  [`kotlinx-coroutines-play-services`](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-play-services/){: .external}
  to `firebase-storage-ktx` as a transitive dependency, which exposes the
  `Task<T>.await()` suspend function to convert a
  [`Task`](https://developers.google.com/android/guides/tasks) into a Kotlin
  coroutine.
* [feature] Added
  [`StorageTask.taskState`](/docs/reference/kotlin/com/google/firebase/storage/ktx/package-summary#taskState)
  Kotlin Flows to monitor the progress of an upload or download `Task`.

# 20.0.2
* [changed] Updated dependency of `play-services-basement` to its latest
  version (v18.1.0).


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-storage` library. The Kotlin extensions library has no additional
updates.

# 20.0.1
* [changed] Updated dependencies of `play-services-basement`,
  `play-services-base`, and `play-services-tasks` to their latest versions
  (v18.0.0, v18.0.1, and v18.0.1, respectively). For more information, see the
  [note](#basement18-0-0_base18-0-1_tasks18-0-1) at the top of this release
  entry.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-storage` library. The Kotlin extensions library has no additional
updates.

# 20.0.0
* [feature] Added abuse reduction features.
* [feature] Added the ability to connect to the [firebase_storage] emulator.
* [changed] Internal changes to support dynamic feature modules.
* [changed] Internal infrastructure improvements.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-storage` library. The Kotlin extensions library has no additional
updates.

# 19.2.2
* [fixed] Fixed an issue that caused the SDK to report incorrect values for
[`getTotalByteCount()`](docs/reference/android/com/google/firebase/storage/FileDownloadTask.TaskSnapshot#getTotalByteCount())
after a download was paused and resumed.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-storage` library. The Kotlin extensions library has no additional
updates.

# 19.2.1
* [fixed] Fixed an issue that caused the SDK to crash if the download location
 was deleted before the download completed. Instead, the download now fails.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-storage` library. The Kotlin extensions library has no additional
updates.

# 19.2.0
* [changed] Updated to support improvements in the KTX library (see below).


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-storage` library and has the following additional updates:

* [feature] Added API support for destructuring of
  [`TaskSnapshot`](/docs/reference/kotlin/com/google/firebase/storage/StreamDownloadTask.TaskSnapshot)
  and
  [`ListResult`](/docs/reference/kotlin/com/google/firebase/storage/ListResult).

# 19.1.1
* [changed] Internal changes to ensure functionality alignment with other SDK releases.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-storage` library. The Kotlin extensions library has no additional
updates.

# 19.1.0
* [feature] Added `getCacheControl()`, `getContentDisposition()`,
  `getContentEncoding()`, `getContentLanguage()`, and `getContentType()` to
  [`StorageMetadata.Builder`](/docs/reference/android/com/google/firebase/storage/StorageMetadata.Builder)
  to provide access to the current state of the metadata.
* [fixed] Fixed an encoding issue in
  [`StorageReference.list()`](/docs/reference/android/com/google/firebase/storage/StorageReference.html#list(int))
  that caused the API to miss entries for prefixes that contained special
  characters.


## Kotlin
* [feature] The beta release of a [firebase_storage_full] Android library
  with Kotlin extensions is now available. The Kotlin extensions library
  transitively includes the base `firebase-storage` library. To learn more,
  visit the
  [[firebase_storage_full] KTX documentation](/docs/reference/kotlin/com/google/firebase/storage/ktx/package-summary).

# 19.0.1
* [fixed] [`StorageReference.listAll()`](/docs/reference/android/com/google/firebase/storage/StorageReference.html#listAll())
  now propagates the error messages if the List operation was denied by a
  Security Rule.

# 19.0.0
* [changed] Versioned to add nullability annotations to improve the Kotlin
  developer experience. No other changes.

# 18.1.1
* [changed] Internal changes to ensure functionality alignment with other SDK
  releases.

# 18.1.0
* [feature] Added
  [`StorageReference.list()`](/docs/reference/android/com/google/firebase/storage/StorageReference.html#list(int))
  and [`StorageReference.listAll()`](/docs/reference/android/com/google/firebase/storage/StorageReference.html#listAll()),
  which allows developers to list the files and folders under the given
  StorageReference.
* [changed] Added validation to
  [`StorageReference.getDownloadUrl()`](/docs/reference/android/com/google/firebase/storage/StorageReference.html#getDownloadUrl())
  and [`StorageReference.getMetadata()`](/docs/reference/android/com/google/firebase/storage/StorageReference.html#getMetadata())
  to return an error if the reference is the root of the bucket.

# 17.0.0
* [changed] Internal changes that rely on an updated API to obtain
  authentication credentials. If you use [firebase_auth], update to
  `firebase-auth` v17.0.0 or later to ensure functionality alignment.

# 16.0.2
* [fixed] This release includes minor fixes and improvements.

# 16.0.1
* [feature] Added support for `onSuccessTask()` and `addOnCanceledListener()`
  to [`StorageTask`](/docs/reference/android/com/google/firebase/storage/StorageTask),
  [`UploadTask`](/docs/reference/android/com/google/firebase/storage/UploadTask),
  [`StreamDownloadTask`](/docs/reference/android/com/google/firebase/storage/StreamDownloadTask),
  and [`FileDownloadTask`](/docs/reference/android/com/google/firebase/storage/FileDownloadTask).
* [changed] Removed the deprecated `StorageMetadata.getDownloadUrl()` and
  `UploadTask.TaskSnapshot.getDownloadUrl()` methods. To get a current download
  URL, use
  [`StorageReference.getDownloadUr()`](/docs/reference/android/com/google/firebase/storage/StorageReference.html#getDownloadUrl()).

