# Unreleased


# 25.0.0
* [feature] Enable queries with range & inequality filters on multiple fields. [#5729](//github.com/firebase/firebase-android-sdk/pull/5729)
* [changed] Internal improvements.
* [feature] Support conversion between `java.time.Instant` and `Timestamp` [#5853](//github.com/firebase/firebase-android-sdk/pull/5853)

## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.

# 24.11.0
* [feature] Enable snapshot listener option to retrieve data from local cache only. [#5690](//github.com/firebase/firebase-android-sdk/pull/5690)


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.

# 24.10.3
* [fixed] Fixed the missing handling setter annotations bug introduced by [#5626](//github.com/firebase/firebase-android-sdk/pull/5626). [#5706](//github.com/firebase/firebase-android-sdk/pull/5706)


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.

# 24.10.2
* [changed] Internal test improvements.
* [fixed] Fixed the `@Exclude` annotation doesn't been propagated to Kotlin's corresponding bridge methods. [#5626](//github.com/firebase/firebase-android-sdk/pull/5626)


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.

# 24.10.1
* [fixed] Fixed an issue caused by calling mutation on immutable map object. [#5573](//github.com/firebase/firebase-android-sdk/pull/5573)
* [fixed] Fixed an issue in the local cache synchronization logic where all locally-cached documents that matched a resumed query would be unnecessarily re-downloaded; with the fix it now only downloads the documents that are known to be out-of-sync. [#5506](//github.com/firebase/firebase-android-sdk/pull/5506)
* [fixed] Fixed an issue where GC runs into a infinite loop in a certain case. [#5417](https://github.com/firebase/firebase-android-sdk/issues/5417)


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.

# 24.10.0
* [fixed] Fixed the `DocumentSnapshot` equals method to not consider internal state when comparing snapshots.

# 24.9.1
* [feature] Expose Sum/Average aggregate query support in API. [#5217](//github.com/firebase/firebase-android-sdk/pull/5217)


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.

# 24.9.0
* [changed] Added Kotlin extensions (KTX) APIs from `com.google.firebase:firebase-firestore-ktx`
  to `com.google.firebase:firebase-firestore` under the `com.google.firebase.firestore` package.
  For details, see the
  [FAQ about this initiative](https://firebase.google.com/docs/android/kotlin-migration)
* [deprecated] All the APIs from `com.google.firebase:firebase-firestore-ktx` have been added to
  `com.google.firebase:firebase-firestore` under the `com.google.firebase.firestore` package,
  and all the Kotlin extensions (KTX) APIs in `com.google.firebase:firebase-firestore-ktx` are
  now deprecated. As early as April 2024, we'll no longer release KTX modules. For details, see the
  [FAQ about this initiative](https://firebase.google.com/docs/android/kotlin-migration)


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.

# 24.8.1
* [fixed] Disabled `GradleMetadataPublishing` to fix breakage of the Kotlin extensions library. [#5337]


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.

# 24.8.0
* [feature] Added the option to allow the SDK to create cache indexes automatically to
  improve query execution locally. See
  [`db.getPersistentCacheIndexManager().enableIndexAutoCreation()`](/docs/reference/android/com/google/firebase/firestore/PersistentCacheIndexManager#enableIndexAutoCreation())
  ([GitHub [#4987](//github.com/firebase/firebase-android-sdk/pull/4987){: .external}).


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.

# 24.7.1
* [fixed] Implement equals method on Filter class. [#5210](//github.com/firebase/firebase-android-sdk/issues/5210)

# 24.7.0
* [feature] Expose MultiDb support in API. [#4015](//github.com/firebase/firebase-android-sdk/issues/4015)
* [fixed] Fixed a thread interference issue that may lead to a ConcurrentModificationException.
  (GitHub [#5091](//github.com/firebase/firebase-android-sdk/issues/5091){: .external})


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.

# 24.6.1
* [feature] Implemented an optimization in the local cache synchronization logic that reduces the number of billed document reads when documents were deleted on the server while the client was not actively listening to the query (e.g. while the client was offline). (GitHub [#4982](//github.com/firebase/firebase-android-sdk/pull/4982){: .external})


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.

# 24.6.0
* [fixed] Fixed stack overflow caused by deeply nested server timestamps.
  (GitHub [#4702](//github.com/firebase/firebase-android-sdk/issues/4702){: .external})
* [feature] Added new
  [cache config APIs](/docs/reference/android/com/google/firebase/firestore/FirebaseFirestoreSettings.Builder#setLocalCacheSettings(com.google.firebase.firestore.LocalCacheSettings))
  to customize the SDK's cache setup.
* [feature] Added
  [LRU garbage collector](/docs/reference/android/com/google/firebase/firestore/MemoryLruGcSettings)
  to the SDK's memory cache.
* [deprecated] Deprecated the following APIs from
  [`FirebaseFirestoreSettings`](/docs/reference/android/com/google/firebase/firestore/FirebaseFirestoreSettings):<br>
  `isPersistenceEnabled` and `getCacheSizeBytes`.
* [deprecated] Deprecated the following APIs from
  [`FirebaseFirestoreSettings.Builder`](/docs/reference/android/com/google/firebase/firestore/FirebaseFirestoreSettings.Builder#setLocalCacheSettings(com.google.firebase.firestore.LocalCacheSettings)):<br>
  `isPersistenceEnabled`, `getCacheSizeBytes`, `setPersistenceEnabled`,
  and `setCacheSizeBytes`.
* [changed] Internal changes to ensure alignment with other SDK releases.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.

# 24.5.0
* [fixed] Fixed stack overflow caused by deeply nested server timestamps.
  (GitHub [#4702](//github.com/firebase/firebase-android-sdk/issues/4702){: .external})


## Kotlin
* [feature] Added
  [`Query.dataObjects<T>()`](/docs/reference/kotlin/com/google/firebase/firestore/ktx/package-summary#dataObjects)
  and
  [`DocumentReference.dataObjects<T>()`](/docs/reference/kotlin/com/google/firebase/firestore/ktx/package-summary#dataObjects_1)
  Kotlin Flows to listen for realtime updates and convert its values to a
  specific type.

# 24.4.5
* [feature] Added support for disjunctions in queries (`OR` queries).


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.

# 24.4.4
* [changed] Relaxed certain query validations performed by the SDK
  ([GitHub Issue #4231](//github.com/firebase/firebase-android-sdk/issues/4231)).
* [changed] Updated gRPC to 1.52.1, and updated JavaLite, protoc, and
  protobuf-java-util to 3.21.11.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.

# 24.4.3
* [fixed] Fixed a potential high-memory usage issue.
* [fixed] Fixed an issue that stopped some performance optimization from being
  applied.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.

# 24.4.2
* [fixed] Fixed an issue that stopped some performance optimization from being
  applied.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.

# 24.4.1
* [fixed] Fix `FAILED_PRECONDITION` when writing to a deleted document in a
  transaction.
  (#5871)
* [fixed] Fixed [firestore] failing to raise initial snapshot from an empty
  local cache result.
  (#4207)
* [fixed] Removed invalid suggestions to use `GenericTypeIndicator` from
  error messages.
  (#222)
* [changed] Updated dependency of `io.grpc.*` to its latest version
  (v1.50.2).


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.

# 24.4.0
* [feature] Added
  [`Query.count()`](/docs/reference/android/com/google/firebase/firestore/Query#count()),
  which fetches the number of documents in the result set without actually
  downloading the documents.


## Kotlin
The Kotlin extensions library transitively includes the updated
  `firebase-firestore` library. The Kotlin extensions library has the following
  additional updates:

* [feature] Firebase now supports Kotlin coroutines.
  With this release, we added
  [`kotlinx-coroutines-play-services`](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-play-services/){: .external}
  to `firebase-firestore-ktx` as a transitive dependency, which exposes the
  `Task<T>.await()` suspend function to convert a
  [`Task`](https://developers.google.com/android/guides/tasks) into a Kotlin
  coroutine.

# 24.3.1
* [changed] Updated dependency of `io.grpc.*` to its latest
  version (v1.48.1).


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library.

# 24.3.0
* [changed] Updated dependency of `play-services-basement` to its latest
  version (v18.1.0).


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library also has the
following additional updates:

* [feature] Added
  [`Query.snapshots()`](/docs/reference/kotlin/com/google/firebase/firestore/ktx/package-summary#snapshots_1)
  and
  [`DocumentReference.snapshots()`](/docs/reference/kotlin/com/google/firebase/firestore/ktx/package-summary#snapshots)
  Kotlin Flows to listen for realtime updates.

# 24.2.2
* [fixed] Fixed an issue in `waitForPendingWrites()` that could lead to a
  `NullPointerException`.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.

# 24.2.1
* [changed] Internal refactor and test improvements.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.

# 24.2.0
* [feature] Added customization support for
  [`FirebaseFirestore.runTransaction`](/docs/reference/android/com/google/firebase/firestore/FirebaseFirestore#runTransaction(com.google.firebase.firestore.Transaction.Function%3CTResult%3E)).


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.

# 24.1.2
* [fixed] Fixed an issue where patching multiple fields shadows each other.
  (#3528).


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.

# 24.1.1
* [fixed] Fixed an issue in the beta version of the index engine that might
  cause [firestore] to exclude document results for limit queries with local
  modifications.
* [changed] [firestore] can now serialize objects with `android.net.Uri`s.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.

# 24.1.0
* [feature] Added beta support for indexed query execution. You can
  enable indexes by invoking `FirebaseFirestore.setIndexConfiguration()` with
  the JSON index definition exported by the [firebase_cli]. Queries against
  the cache are executed using an index once the asynchronous index generation
  completes.
* [fixed] Fixed missing document fields issue with offline overlays.
  (#3528).


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.

# 24.0.2
* [fixed] Fixed a [firebase_app_check] issue that caused [firestore]
  listeners to stop working and receive a `Permission Denied` error. This issue
  only occurred if the [app_check] expiration time was set to under an hour.
* [fixed] Fixed a potential problem during the shutdown of [firestore] that
  prevented the shutdown from proceeding if a network connection was opened
  right before.
* [fixed] Fixed an NPE issue where mutations with multiple documents were not
  handled correctly during previous mutation acknowledgement.
  (#3490).
* [changed] Queries are now sent to the backend before the SDK starts local
  processing, which reduces overall query latency.
* [changed] Updated dependencies of `play-services-basement`,
  `play-services-base`, and `play-services-tasks` to their latest versions
  (v18.0.0, v18.0.1, and v18.0.1, respectively). For more information, see the
  [note](#basement18-0-0_base18-0-1_tasks18-0-1) at the top of this release
  entry.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.

# 24.0.1
* [changed] Optimized performance for offline usage.
* [changed] Optimized performance for queries with collections that contain
  subcollections.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.

# 24.0.0
* [changed] This SDK now requires devices and emulators to target API level
  19 (KitKat) or higher and to use Android 4.4 or higher. This is due to an
  update in its gRPC dependency version and to align with requirements of other
  Firebase libraries.
* [feature] Added support for [firebase_app_check].


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.

# 23.0.4
* [fixed] Fixed an issue where some fields were missed when copying in the
  `FirebaseFirestoreSettings.Builder` copy constructor.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.

# 23.0.3
* [fixed] Fixed an issue that was causing failures when a data bundle with
  multi-byte Unicode characters was loaded.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.

# 23.0.2
* [changed] Improved Firestore's network condition detection.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.

# 23.0.1
* [changed] The SDK now tries to immediately establish a connection to the
  backend when the app enters the foreground.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.

# 23.0.0
* [changed] Internal infrastructure improvements.
* [changed] Internal changes to support dynamic feature modules.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.

# 22.1.2
* [changed] Internal changes in preparation for future support of
  dynamic feature modules.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.

# 22.1.1
* [fixed] Fixed an issue that dropped the limit for queries loaded from
  [firestore] bundles that were generated by the NodeJS SDK.
* [fixed] Fixed a bug where local cache inconsistencies were unnecessarily
  being resolved, causing the `Task` objects returned from `get()`
  invocations to never complete.
  #2404


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.

# 22.1.0
* [feature] Added support for [firestore] bundles via
  [`FirebaseFirestore.loadBundle()`](/docs/reference/android/com/google/firebase/firestore/FirebaseFirestore#loadBundle(java.nio.ByteBuffer))
  and
  [`FirebaseFirestore.getNamedQuery()`](/docs/reference/android/com/google/firebase/firestore/FirebaseFirestore#getNamedQuery(java.lang.String)).
  Bundles contain pre-packaged data produced with the Firebase Admin Node.js SDK
  and can be used to populate the cache for [firestore] without the need to
  read documents from the backend.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.

# 22.0.2
* [changed] A write to a document that contains `FieldValue` transforms is no
  longer split into two separate operations. This reduces the number of writes
  that the backend performs and allows each `WriteBatch` to hold 500 writes
  regardless of how many `FieldValue` transformations are attached.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.

# 22.0.1
* [changed] Removed excess validation of null and NaN values in query filters.
  This more closely aligns the SDK with the [firestore] backend, which has
  always accepted null and NaN for all operators.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.

# 22.0.0
* [changed] Removed the deprecated `timestampsInSnapshotsEnabled` setting.
  Any timestamp in a [firestore] document is now returned as a `Timestamp`. To
  convert `Timestamp` classes to `java.util.Date`, use
  [`Timestamp.toDate()`](/docs/reference/android/com/google/firebase/Timestamp#toDate()).


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.

# 21.7.1
* [changed] Added new internal HTTP headers to the gRPC connection.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.

# 21.7.0
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
* [fixed] Fixed an issue that caused poor performance for queries that
  filtered results using nested array values.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.

# 21.6.0
* [fixed] Removed a delay that may have prevented [firestore] from
  immediately reestablishing a network connection if a connectivity change
  occurred while the app was in the background.
* [fixed] Fixed an issue that may have prevented the client from connecting
  to the backend immediately after a user signed in.
* [feature] Added support for connecting to the Firebase Emulator Suite via
  a new method,
  [`FirebaseFirestore#useEmulator()`](/docs/reference/android/com/google/firebase/firestore/FirebaseFirestore#useEmulator(java.lang.String,%20int)).


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.

# 21.5.0
* [changed] Updated the protocol buffer dependency to the newer
  `protobuf-javalite` artifact. The new artifact is incompatible with the old
  one, so this library needed to be upgraded to avoid conflicts.
  No developer action is necessary.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.

# 21.4.3
* [changed] [firestore] now limits the number of concurrent document lookups
  it will perform when resolving inconsistencies in the local cache.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.

# 21.4.2
* [changed] Removed Guava dependency from the SDK. This change is the first
  step in eliminating crashes caused by apps that depend on the wrong flavor of
  Guava. ([Issue #1125](//github.com/firebase/firebase-android-sdk/issues/1125))


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.

# 21.4.1
* [fixed] Fixed a performance regression introduced by the addition of
  `Query.limitToLast(n: long)` in [firestore] v21.3.1.
* [changed] Changed the in-memory representation of [firestore] documents to
  reduce memory allocations and improve performance. Calls to
  `DocumentSnapshot.getData()` and `DocumentSnapshot.toObject()` will see
  the biggest improvement.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.

# 21.4.0
* [feature] Cloud Firestore previously required that every document read in a
  transaction must also be written. This requirement has been removed, and
  you can now read a document in a transaction without writing to it.
* [changed] Cloud Firestore now recovers more quickly when connections
  suffering packet loss return to normal.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.

# 21.3.1
* [feature] Added `Query.limitToLast(n: long)`, which returns the last `n`
  documents as the result.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.

# 21.3.0
* [feature] Added `Query.whereIn()` and `Query.whereArrayContainsAny()` query
  operators. `Query.whereIn()` finds documents where a specified field’s value
  is IN a specified array. `Query.whereArrayContainsAny()` finds documents
  where a specified field is an array and contains ANY element of a specified
  array.
* [changed] Improved the performance of repeatedly executed queries. Recently
  executed queries should see dramatic improvements. This benefit is reduced
  if changes accumulate while the query is inactive. Queries that use the
  `limit()` API may not always benefit, depending on the accumulated changes.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.

# 21.2.1
* [fixed] Fixed an issue where devices targeting Android API level 19 or
  earlier would crash when they were unable to connect to [firestore].
* [fixed] Fixed a race condition in Documents where access to `getData` and
  `getField` on the same document in different threads could cause a
  `NullPointerException`.
* [fixed] Fixed a race condition that could cause a `NullPointerException`
  during client initialization.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.

# 21.2.0
* [feature] Added an [`addSnapshotsInSyncListener()`](/docs/reference/android/com/google/firebase/firestore/FirebaseFirestore#addSnapshotsInSyncListener(java.lang.Runnable)) method to
  `FirebaseFirestore` that notifies you when all your snapshot listeners are
  in sync with each other.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.

# 21.1.1
* [fixed] Addressed a regression in v21.1.0 that caused the crash: "Cannot add
  document to the RemoteDocumentCache with a read time of zero".


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.

# 21.1.0
Warning: We have received reports that this **v21.1.0 release** of the Firebase
Android SDK for [firestore] can trigger an uncaught exception. Make sure to
update to the next version of the Cloud Firestore SDK to get the fix.

* [feature] Added a
  [`FirebaseFirestore.terminate()`](/docs/reference/android/com/google/firebase/firestore/FirebaseFirestore#terminate())
  method which terminates the instance, releasing any held resources. Once it
  completes, you can optionally call `clearPersistence()` to wipe persisted
  [firestore] data from disk.
* [feature] Added a
  [`FirebaseFirestore.waitForPendingWrites()`](/docs/reference/android/com/google/firebase/firestore/FirebaseFirestore#waitForPendingWrites())
  method which allows users to wait on a promise that resolves when all pending
  writes are acknowledged by the [firestore] backend.
* [changed] Transactions now perform exponential backoff before retrying.
  This means transactions on highly contended documents are more likely to
  succeed.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.

# 21.0.0
* [changed] Transactions are now more flexible. Some sequences of operations
  that were previously incorrectly disallowed are now allowed. For example,
  after reading a document that doesn't exist, you can now set it multiple
  times successfully in a transaction.
* [fixed] Fixed an issue where query results were temporarily missing
  documents that previously had not matched but had been updated to now match
  the query. Refer to this
  [GitHub issue](https://github.com/firebase/firebase-android-sdk/issues/155)
  for more details.
* [changed] Added nullability annotations to improve the Kotlin developer
  experience.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.

# 20.2.0
* [feature] Added a `@DocumentId` annotation which can be used on a
  `DocumentReference` or `String` property in a POJO to indicate that the SDK
  should automatically populate it with the document's ID.
* [fixed] Fixed an internal assertion that was triggered when an update with
  a `FieldValue.serverTimestamp()` and an update with a
  `FieldValue.increment()` were pending for the same document. Refer to this
  [GitHub issue](https://github.com/firebase/firebase-android-sdk/issues/491)
  for more details.
* [changed] Improved performance of queries with large result sets.
* [changed] Improved performance for queries with filters that only return a
  small subset of the documents in a collection.
* [changed] Instead of failing silently, [firestore] now crashes the client
  app if it fails to load SSL Ciphers. To avoid these crashes, you must bundle
  Conscrypt to support non-GMSCore devices on Android API level 19 (KitKat) or
  earlier (for more information, refer to
  [TLS on Android](https://github.com/grpc/grpc-java/blob/master/SECURITY.md#tls-on-android)).


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.

# 20.1.0
* [changed] SSL and gRPC initialization now happens on a separate thread,
  which reduces the time taken to produce the first query result.
* [feature] Added `clearPersistence()`, which clears the persistent storage
  including pending writes and cached documents. This is intended to help
  write reliable tests. Refer to this
  [GitHub issue](https://github.com/firebase/firebase-js-sdk/issues/449) for
  more details.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.

# 19.0.2
* [fixed] Updated gRPC to 1.21.0. A bug in the prior version would
  occasionally cause a crash if a network state change occurred concurrently
  with an RPC. Refer to
  [GitHub issue #428](https://github.com/firebase/firebase-android-sdk/issues/428)
  for more details.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.

# 19.0.1
* [fixed] Fixed an issue that prevented schema migrations for clients with
  large offline datasets. Refer to this
  [GitHub issue](https://github.com/firebase/firebase-android-sdk/issues/370)
  for more details.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.

# 19.0.0
* [feature] You can now query across all collections in your database with a
  given collection ID using the
  [`FirebaseFirestore.collectionGroup()`](/docs/reference/android/com/google/firebase/firestore/FirebaseFirestore#collectionGroup)
  method.
* [changed] The garbage collection process for on-disk persistence that
  removes older documents is now enabled by default. The SDK will attempt to
  periodically clean up older, unused documents once the on-disk cache passes a
  threshold size (default: 100 MB). See
  [Configure cache size](/docs/firestore/manage-data/enable-offline#configure_cache_size)
  for details on how to configure this.
* [changed] Internal changes that rely on an updated API to obtain
  authentication credentials. If you use [firebase_auth], update to
  `firebase-auth` v17.0.0 or later to ensure functionality alignment.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-firestore` library. The Kotlin extensions library has no additional
updates.

# 18.2.0
* [unchanged] No changes to the base `firebase-firestore` library.


## Kotlin
* [feature] The beta release of a [firestore] Android library with Kotlin
  extensions is now available. The Kotlin extensions library transitively
  includes the base `firebase-firestore` library.  To learn more, visit the
  [[firestore] KTX documentation](/docs/reference/kotlin/com/google/firebase/firestore/ktx/package-summary).

# 18.2.0
* [feature] Added [`FieldValue.increment()`](/docs/reference/android/com/google/firebase/firestore/FieldValue#increment(double)),
  which can be used in `update()` and `set(..., SetOptions.merge())` to
  increment or decrement numeric field values safely without transactions.
* [feature] Added functional interface [`FirebaseFirestore.runBatch()`](/docs/reference/android/com/google/firebase/firestore/FirebaseFirestore#runBatch( com.google.firebase.firestore.WriteBatch.Function)),
  similar to [`FirebaseFirestore.runTransaction()`](/docs/reference/android/com/google/firebase/firestore/FirebaseFirestore#runTransaction(com.google.firebase.firestore.Transaction.Function%3CTResult%3E )),
  which allows a developer to focus on the mutations of the batch rather than on
  creating and committing the batch.
* [changed] Prepared the persistence layer to support collection group
  queries. While this feature is not yet available, all schema changes are
  included in this release.
* [changed] Added `@RestrictTo` annotations to discourage the use of APIs that
  are not public. This affects internal APIs that were previously obfuscated and
  are not mentioned in our documentation.
* [changed] Improved error messages for certain Number types that are not
  supported by our serialization layer.

# 18.1.0
* [changed] Internal changes to ensure functionality alignment with other SDK
  releases.
* [fixed] Fixed calculation of SQLite database size on Android 9 Pie devices.
  On these devices, the previous method sometimes incorrectly calculated the
  size by a few MBs, potentially delaying garbage collection.

# 18.0.1
* [fixed] Fixed an issue where [firestore] would crash if handling write
  batches larger than 2 MB in size.
* [changed] [firestore] now recovers more quickly from long periods without
  network access.

# 18.0.0
* [changed] The `timestampsInSnapshotsEnabled` setting is now enabled by
  default. Timestamp fields that read from a `DocumentSnapshot` are now returned
  as `Timestamp` objects instead of `Date` objects. This is a breaking change;
  developers must update any code that expects to receive a `Date` object. See
  [`FirebaseFirestoreSettings.Builder.setTimestampsInSnapshotsEnabled()`](/docs/reference/android/com/google/firebase/firestore/FirebaseFirestoreSettings.Builder.html#setTimestampsInSnapshotsEnabled(boolean))
  for more details.
* [feature] Custom objects (POJOs) can now be passed in several ways: as a
  field value in `update()`, within `Map<>` objects passed to `set()`, in array
  transform operations, and in query filters.
* [feature] `DocumentSnapshot.get()` now supports retrieving fields as custom
  objects (POJOs) by passing a `Class<T>` instance, e.g.,
  `snapshot.get("field", CustomType.class)`.
* [fixed] Fixed an issue where if an app sent a write to the server, but the
  app was shut down before a listener received the write, the app could crash.

# 17.1.5
* [changed] [firestore] now recovers more quickly from bad network states.
* [changed] Improved performance for reading large collections.
* [fixed] Offline persistence now properly records schema downgrades. This is
  a forward-looking change that allows you to safely downgrade from future SDK
  versions to this version (v17.1.5). You can already safely downgrade versions
  now depending on the source version. For example, you can safely downgrade
  from v17.1.4 to v17.1.2 because there are no schema changes between those
  versions.  Related:
  https://github.com/firebase/firebase-android-sdk/issues/134

# 17.1.4
* [fixed] Fixed a SQLite transaction-handling issue that occasionally masked
  exceptions when Firestore closed a transaction that was never started. For
  more information, see the [issue report in GitHub](https://github.com/firebase/firebase-android-sdk/issues/115).
* [fixed] Fixed a race condition that caused a `SQLiteDatabaseLockedException`
  when an app attempted to access the SQLite database from multiple threads.

# 17.1.2
* [changed] Changed how the SDK handles locally-updated documents while syncing those updates with Cloud Firestore servers. This can lead to slight behavior changes and may affect the [`SnapshotMetadata.hasPendingWrites()`](/docs/reference/android/com/google/firebase/firestore/SnapshotMetadata.html#hasPendingWrites()) metadata flag.
* [changed] Eliminated superfluous update events for locally cached documents that are known to lag behind the server version. Instead, the SDK buffers these events until the client has caught up with the server.

# 17.1.1
* [fixed] Fixed an issue where the first `get()` call made after being offline could incorrectly return cached data without attempting to reach the backend.
* [changed] Changed `get()` to only make one attempt to reach the backend before returning cached data, potentially reducing delays while offline.
* [fixed] Fixed an issue that caused Firebase to drop empty objects from calls to `set(..., SetOptions.merge())`.
* [fixed] Updated printf-style templates to ensure that they're compile time constants. Previously, some were influenced by error messages. When those error messages contained `%p` or other, related tokens, `String.format()` would throw an exception.
* [changed] Some SDK errors that represent common mistakes, like permission errors or missing indexes, are automatically logged as warnings in addition to being surfaced via the API.

# 17.1.0
* [fixed] Corrected an issue with methods in the Cloud Firestore v17.0.5 release. To avoid potential errors, don't use v17.0.5.

# 17.0.5
* [feature] Added [`FieldValue.arrayUnion()`](/docs/reference/android/com/google/firebase/firestore/FieldValue.html#arrayUnion(Object...)) and [`FieldValue.arrayRemove()`](/docs/reference/android/com/google/firebase/firestore/FieldValue.html#arrayRemove(Object...)) to atomically add and remove elements from an array field in a document.
* [feature] Added [`Query.whereArrayContains()`](/docs/reference/android/com/google/firebase/firestore/Query.html#whereArrayContains(com.google.firebase.firestore.FieldPath, java.lang.Object)) query operator to find documents where an array field contains a specific element.
* [changed] Improved offline performance with many outstanding writes.
* [fixed] Firestore will now recover from auth token expiration when the system clock is wrong.

# 17.0.4
* [fixed] Fixed an issue where queries returned fewer results than they
  should. The issue related to
  [improper caching](https://github.com/firebase/firebase-ios-sdk/issues/1548),
  so clients may use extra bandwidth the first time they launch with this
  version of the SDK, as they re-download cleared cached data.

# 17.0.3
* [changed] The [`Timestamp`](/docs/reference/android/com/google/firebase/Timestamp) class now implements [`Parcelable`](//developer.android.com/reference/android/os/Parcelable) in addition to [`Comparable`](//developer.android.com/reference/java/lang/Comparable).

# 17.0.2
* [changed] gRPC requirement updated from 1.8.0 to 1.12.0. This allows quicker
  failover between Wi-Fi and cellular networks.

# 17.0.1
* [fixed] Fixed an issue where `set()` didn't correctly respect
  [`SetOptions.mergeFields()`](/docs/reference/android/com/google/firebase/firestore/SetOptions.html#mergeFields(java.util.List<java.lang.String>))
  for data containing
  [`FieldValue.delete()`](/docs/reference/android/com/google/firebase/firestore/FieldValue.html#delete())
  or
  [`FieldValue.serverTimestamp()`](/docs/reference/android/com/google/firebase/firestore/FieldValue.html#serverTimestamp())
  values.

