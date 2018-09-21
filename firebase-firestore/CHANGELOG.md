# 17.1.1
- [fixed] Fixed an issue where the first `get()` call made after being offline
  could incorrectly return cached data without attempting to reach the backend.
- [changed] Changed `get()` to only make 1 attempt to reach the backend before
  returning cached data, potentially reducing delays while offline. Previously
  it would make 2 attempts, to work around a backend bug.
- [fixed] Fixed an issue that caused us to drop empty objects from calls to
  `set(..., SetOptions.merge())`.
- [fixed] Ensure printf style templates are compile time constants. Previously,
  some were influenced by error messages. When those error messages contained
  '%p' (amongst other possibilities), String.format() would throw an exception.
- [changed] Some SDK errors that represent common mistakes (such as permission
  denied or a missing index) will automatically be logged as a warning in
  addition to being surfaced via the API.

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
