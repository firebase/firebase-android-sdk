# Unreleased
- [fixed] Fixed calculation of SQLite database size on Android 9 Pie devices.
  Previous method could be off by a few MBs on these devices, potentially
  delaying garbage collection.

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
