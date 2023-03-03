# Unreleased

# 24.1.2
* [changed] Internal infrastructure improvements.

* [changed] Migrated [firebase_ml] to use standard Firebase executors.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-ml-modeldownloader` library. The Kotlin extensions library has no
additional updates.

# 24.1.1
* [fixed] Fixed an issue where `FirebaseModelDownloader.getModel` was throwing
  `FirebaseMlException.PERMISSION_DENIED` when the model name was empty. It now
  throws `FirebaseMlException.INVALID_ARGUMENT`
  (#4157)


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-ml-modeldownloader` library. The Kotlin extensions library has no
additional updates.
# 24.1.0
* [unchanged] Updated to accommodate the release of the updated
  [firebase_ml] Kotlin extensions library.


## Kotlin
The Kotlin extensions library transitively includes the updated
  `firebase-ml-modeldownloader` library. The Kotlin extensions library has the
  following additional updates:

* [feature] Firebase now supports Kotlin coroutines.
  With this release, we added
  [`kotlinx-coroutines-play-services`](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-play-services/){: .external}
  to `firebase-ml-modeldownloader-ktx` as a transitive dependency, which
   exposes the `Task<T>.await()` suspend function to convert a
  [`Task`](https://developers.google.com/android/guides/tasks) into a Kotlin
  coroutine.

# 24.0.5
* [changed] Updated dependency of `play-services-basement` to its latest
  version (v18.1.0).


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-ml-modeldownloader` library. The Kotlin extensions library has no
additional updates.
# 24.0.4
* [fixed] Fixed a race condition that was caused when differently sized
  models were concurrently downloaded using this SDK and the Model Downloader from
  the `com.google.firebase:firebase-ml-common` SDK.
  (#3321)


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-ml-modeldownloader` library. The Kotlin extensions library has no
additional updates.
# 24.0.3
* [changed] Updated dependencies of `play-services-basement`,
  `play-services-base`, and `play-services-tasks` to their latest versions
  (v18.0.0, v18.0.1, and v18.0.1, respectively). For more information, see the
  [note](#basement18-0-0_base18-0-1_tasks18-0-1) at the top of this release
  entry.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-ml-modeldownloader` library. The Kotlin extensions library has no
additional updates.
# 24.0.2
* [fixed] Fixed an issue where `FirebaseModelDownloader.getInstance` would
  crash when using non-default FirebaseApp instances.
  (#3321)
* [changed] Updated to the latest version of the `firebase-datatransport`
  library.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-ml-modeldownloader` library. The Kotlin extensions library has no
additional updates.
# 24.0.1
* [fixed] Added support for Android API key restrictions.

# 24.0.0
- [changed] Internal infrastructure improvements.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-ml-modeldownloader` library. The Kotlin extensions library has no
additional updates.

# 23.0.1
* [unchanged] Updated to accommodate the release of the [firebase_ml]
  Kotlin extensions library.


## Kotlin
* [feature] The beta release of a [firebase_ml] Android library with
  Kotlin extensions is now available. The Kotlin extensions library transitively
  includes the base `firebase-ml-model-downloader` library. To learn more,
  visit the
  [[firebase_ml] KTX documentation](/docs/reference/android/com/google/firebase/ml/modeldownloader/package-summary).

# 23.0.0
This release includes the initial beta release of the
[firebase_ml] Model Downloader SDK.

The [firebase_ml] Model Downloader SDK provides APIs for downloading models
hosted with [[firebase_ml] Custom Model Hosting](/docs/ml/use-custom-models).
This SDK is a lightweight version of the ML Kit Custom Models library
(`firebase-ml-model-interpreter`), allowing you to work with custom hosted
models without the interpreter API, which is now provided directly by TFLite
runtime.

* [feature] Added custom hosted model download and on-device management
  capabilities.

* [feature] Added ability to get the model download ID, which allows progress
  tracking of file downloads.
