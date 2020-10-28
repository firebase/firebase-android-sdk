# Unreleased
- [fixed] Removed excess validation of null and NaN values in query filters.
  This more closely aligns the SDK with the Firestore backend, which has always
  accepted null and NaN for all operators, even though this isn't necessarily
  useful.

# (22.0.0)
- [changed] Removed the deprecated `timestampsInSnapshotsEnabled` setting.
  Any timestamps in Firestore documents are now returned as `Timestamps`. To
  convert `Timestamp` classed to `java.util.Date`, use `Timestamp.toDate()`.

# 21.6.1
- [changed] Added new internal HTTP headers to the gRPC connection.

# 21.6.0
- [fixed] Removed a delay that may have prevented Firestore from immediately
  reestablishing a network connection if a connectivity change occurred while
  the app was in the background.
- [fixed] Fixed an issue that may have prevented the client from connecting
  to the backend immediately after a user signed in.
- [feature] Cloud Firestore now supports connecting to a local emulator via
 `FirebaseFirestore#useEmulator()`
- [feature] Added `Query.whereNotIn()` and `Query.whereNotEqualTo()` query
  operators. `Query.whereNotIn()` finds documents where a specified field’s
  value is not in a specified array. `Query.whereNotEqualTo()` finds
  documents where a specified field's value does not equal the specified value.
  Neither query operator will match documents where the specified field is not
  present.

# 21.4.3
- [changed] Firestore now limits the number of concurrent document lookups it
  will perform when resolving inconsistencies in the local cache (#1374).

# 21.4.2
- [changed] Removed Guava dependency from the SDK. This change is the first
  step in eliminating crashes caused by apps that depend on the wrong flavor of
  Guava (#1125).

# 21.4.1
- [fixed] Fixed a performance regression introduced by the addition of
  `Query.limitToLast(n: long)` in Firestore 23.3.1.
- [changed] Changed the in-memory representation of Firestore documents to
  reduce memory allocations and improve performance. Calls to 
  `DocumentSnapshot.getData()` and `DocumentSnapshot.toObject()` will see
  the biggest improvement.

# 21.4.0
- [feature] Firestore previously required that every document read in a
  transaction must also be written. This requirement has been removed, and
  you can now read a document in a transaction without writing to it.
- [changed] Firestore now recovers more quickly once connections suffering
  packet loss return to normal. 

# 21.3.1
- [feature] Added `Query.limitToLast(n: long)`, which returns the last `n`
  documents as the result.

# 21.3.0
- [feature] Added `Query.whereIn()` and `Query.whereArrayContainsAny()` query
  operators. `Query.whereIn()` finds documents where a specified field’s value
  is IN a specified array. `Query.whereArrayContainsAny()` finds documents
  where a specified field is an array and contains ANY element of a specified
  array.
- [changed] Improved the performance of repeatedly executed queries. Recently
  executed queries should see dramatic improvements. This benefit is reduced
  if changes accumulate while the query is inactive. Queries that use the
  `limit()` API may not always benefit, depending on the accumulated changes.

# 21.2.1
- [fixed] Fixed an issue where Android API level 19 and earlier devices would
  crash when unable to connect to Firestore (#904).
- [fixed] Fixed a race condition in Documents where access to getData and
  getField on the same document in different threads could cause a
  NullPointerException.
- [fixed] Fix a race condition that could cause a `NullPointerException` during
  client initialization.

# 21.2.0
- [feature] Added an `addSnapshotsInSyncListener()` method to 
  `FirebaseFirestore`that notifies you when all your snapshot listeners are
  in sync with each other.

# 21.1.2
- [fixed] Fixed a crash that could occur when a large number of documents were
  removed during garbage collection of the persistence cache.

# 21.1.1
- [fixed] Addressed a regression in 21.1.0 that caused the crash: "Cannot add
  document to the RemoteDocumentCache with a read time of zero".

# 21.1.0
- [feature] Added a `terminate()` method to `FirebaseFirestore` which
  terminates the instance, releasing any held resources. Once it completes, you
  can optionally call `clearPersistence()` to wipe persisted Firestore data from
  disk.
- [feature] Added a `waitForPendingWrites()` method to `FirebaseFirestore`
  which allows users to wait on a promise that resolves when all pending writes
  are acknowledged by the Firestore backend.
- [changed] Transactions now perform exponential backoff before retrying. This
  means transactions on highly contended documents are more likely to succeed.

# 21.0.0
- [changed] Transactions are now more flexible. Some sequences of operations
  that were previously incorrectly disallowed are now allowed. For example,
  after reading a document that doesn't exist, you can now set it multiple
  times successfully in a transaction.
- [fixed] Fixed an issue where query results were temporarily missing documents
  that previously had not matched but had been updated to now match the
  query (#155).

# 20.2.0
- [feature] Added a `@DocumentId` annotation which can be used on a
  `DocumentReference` or `String` property in a POJO to indicate that the SDK
  should automatically populate it with the document's ID.
- [fixed] Fixed an internal assertion that was triggered when an update
  with a `FieldValue.serverTimestamp()` and an update with a
  `FieldValue.increment()` were pending for the same document (#491).
- [changed] Improved performance of queries with large result sets.
- [changed] Improved performance for queries with filters that only return a
  small subset of the documents in a collection.
- [changed] Instead of failing silently, Firestore now crashes the client app
  if it fails to load SSL Ciphers. To avoid these crashes, you must bundle 
  Conscrypt to support non-GMSCore devices on Android API level 19 (KitKat) or
  earlier (for more information, refer to
  https://github.com/grpc/grpc-java/blob/master/SECURITY.md#tls-on-android).
- [changed] Failed transactions now fail with the exception from the last 
  attempt instead of always failing with an exception with code `ABORTED`.

# 20.1.0
- [changed] SSL and gRPC initialization now happens on a separate thread, which
  reduces the time taken to produce the first query result.
- [feature] Added `clearPersistence()`, which clears the persistent storage
  including pending writes and cached documents. This is intended to help
  write reliable tests (https://github.com/firebase/firebase-js-sdk/issues/449).

# 20.0.0
- [changed] Migrated from the Android Support Libraries to the Jetpack
  (AndroidX) Libraries.

# 19.0.2
- [fixed] Updated gRPC to 1.21.0. A bug in the prior version would occasionally
  cause a crash if a network state change occurred concurrently with an RPC.
  (#428)

# 19.0.1
- [fixed] Fixed an issue that prevented schema migrations for clients with
  large offline datasets (#370).

# 19.0.0
- [feature] You can now query across all collections in your database with a
  given collection ID using the `FirebaseFirestore.collectionGroup()` method.
- [changed] The garbage collection process for on-disk persistence that
  removes older documents is now enabled by default. The SDK will attempt to
  periodically clean up older, unused documents once the on-disk cache passes a
  threshold size (default: 100 MB). This threshold can be configured by setting
  `FirebaseFirestoreSettings.Builder.setCacheSizeBytes`. It must be set to a
  minimum of 1 MB. The garbage collection process can be disabled entirely by
  setting `FirebaseFirestoreSettings.setCacheSizeBytes` to
  `FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED`.

# 18.2.0
- [feature] Added `FieldValue.increment()`, which can be used in `update()` and
  `set(..., SetOptions.merge())` to increment or decrement numeric field values
  safely without transactions.
- [feature] Added functional interface `FirebaseFirestore.runBatch()`, similar
  to `FirebaseFirestore.runTransaction()`, which allows a developer to focus on
  the mutations of the batch rather than on creating and committing the batch.
- [changed] Prepared the persistence layer to support collection group queries.
  While this feature is not yet available, all schema changes are included in
  this release.
- [changed] Added `@RestrictTo` annotations to discourage the use of APIs that
  are not public. This affects internal APIs that were previously obfuscated and
  are not mentioned in our documentation.
- [changed] Improved error messages for certain Number types that are not
  supported by our serialization layer (#272).

# 18.1.0
- [changed] Internal changes to ensure functionality alignment with other SDK
  releases.
- [fixed] Fixed calculation of SQLite database size on Android 9 Pie devices.
  On these devices, the previous method sometimes incorrectly calculated the
  size by a few MBs, potentially delaying garbage collection.

# 18.0.1
- [fixed] Fixed an issue where Firestore would crash if handling write batches
  larger than 2 MB in size (#208).
- [changed] Firestore now recovers more quickly from long periods without
  network access (#217).

# 18.0.0
- [changed] The `timestampsInSnapshotsEnabled` setting is now enabled by
  default. Timestamp fields that read from a `DocumentSnapshot` are now
  returned as `Timestamp` objects instead of `Date` objects. This is a breaking
  change; developers must update any code that expects to receive a `Date`
  object. See https://firebase.google.com/docs/reference/android/com/google/firebase/firestore/FirebaseFirestoreSettings.Builder.html#setTimestampsInSnapshotsEnabled(boolean) for more details.
- [feature] Custom objects (POJOs) can now be passed in several ways: as a
  field value in `update()`, within `Map<>` objects passed to `set()`, in array
  transform operations, and in query filters.
- [feature] `DocumentSnapshot.get()` now supports retrieving fields as
  custom objects (POJOs) by passing a `Class<T>` instance, e.g.,
  `snapshot.get("field", CustomType.class)`.
- [fixed] Fixed an issue where if an app sent a write to the server, but the
  app was shut down before a listener received the write, the app could crash.

# 17.1.5
- [changed] Firestore now recovers more quickly from bad network states.
- [changed] Improved performance for reading large collections.
- [fixed] Offline persistence now properly records schema downgrades. This is a
  forward-looking change that allows you to safely downgrade from future SDK
  versions to this version (v17.1.5). You can already safely downgrade versions
  now depending on the source version. For example, you can safely downgrade
  from v17.1.4 to v17.1.2 because there are no schema changes between those
  versions. (#134)

# 17.1.4
- [fixed] Fixed a SQLite transaction handling issue that occasionally masked
  exceptions when Firestore closed a transaction that was never started. For
  more information, see the issue report in GitHub (https://github.com/firebase/firebase-android-sdk/issues/115).
- [fixed] Fixed a race condition that caused a `SQLiteDatabaseLockedException`
  when an app attempted to access the SQLite database from multiple threads.

# 17.1.2
- [changed] Changed how the SDK handles locally-updated documents while syncing
  those updates with Cloud Firestore servers. This can lead to slight behavior
  changes and may affect the `SnapshotMetadata.hasPendingWrites()` metadata
  flag.
- [changed] Eliminated superfluous update events for locally cached documents
  that are known to lag behind the server version. Instead, the SDK buffers
  these events until the client has caught up with the server.

# 17.1.1
- [fixed] Fixed an issue where the first `get()` call made after being offline
  could incorrectly return cached data without attempting to reach the backend.
- [changed] Changed `get()` to only make one attempt to reach the backend before
  returning cached data, potentially reducing delays while offline.
- [fixed] Fixed an issue that caused Firebase to drop empty objects from calls
  to `set(..., SetOptions.merge())`.
- [fixed] Updated printf-style templates to ensure that they're compile time
  constants. Previously, some were influenced by error messages. When those
  error messages contained `%p` or other, related tokens, `String.format()`
  would throw an exception.
- [changed] Some SDK errors that represent common mistakes, like permission
  errors or missing indexes, are automatically be logged as warnings in addition
  to being surfaced via the API.

# 17.1.0
- [feature] Added `FieldValue.arrayUnion()` and `FieldValue.arrayRemove()` to
  atomically add and remove elements from an array field in a document.
- [feature] Added `Query.whereArrayContains()` query operator to find documents
  where an array field contains a specific element.

# 17.0.4
- [fixed] Fixed an issue where queries returned fewer results than they should,
  caused by documents that were cached as deleted when they should not have
  been (firebase/firebase-ios-sdk#1548). Some cache data is cleared and so
  clients may use extra bandwidth the first time they launch with this version
  of the SDK.

# 17.0.3
- [changed] The `Timestamp` class now implements `Parcelable` in addition to
  `Comparable`.
