# Unreleased
- [feature] Custom objects (POJOs) can now be passed as a field value in
  update(), within `Map<>` objects passed to set(), in array transform
  operations, and in query filters.
- [feature] DocumentSnapshot.get() now supports retrieving fields as
  custom objects (POJOs) by passing a Class<T> instance, e.g.
  `snapshot.get("field", CustomType.class)`.

# 17.1.2
- [changed] Changed the internal handling for locally updated documents that
  haven't yet been read back from Cloud Firestore. This can lead to slight
  behavior changes and may affect the `SnapshotMetadata.hasPendingWrites()`
  metadata flag.
- [changed] Eliminated superfluous update events for locally cached documents
  that are known to lag behind the server version. Instead, we buffer these
  events until the client has caught up with the server.

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
