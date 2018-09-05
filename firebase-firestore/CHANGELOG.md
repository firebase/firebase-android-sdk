# Unreleased
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
