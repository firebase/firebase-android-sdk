# Unreleased


# 2.0.7
* [fixed] Removed extraneous logs that risk leaking internal identifiers.

# 2.0.6
* [changed] Updated protobuf dependency to `3.25.5` to fix
  [CVE-2024-7254](https://github.com/advisories/GHSA-735f-pc8j-v9w8).

# 2.0.5
* [unchanged] Updated to keep SDK versions aligned.

# 2.0.4
* [fixed] Handled datastore writes when device has full internal memory more gracefully.
  (GitHub [#5859](https://github.com/firebase/firebase-android-sdk/issues/5859))
* [fixed] Safely unbind malfunctioning session lifecycle service to release service connections.
  (GitHub [#5869](https://github.com/firebase/firebase-android-sdk/issues/5869))

# 1.2.3
* [fixed] Force validation or rotation of FIDs.

# 1.2.1
* [changed] Bump internal dependencies.
* [fixed] Handle corruption in DataStore Preferences more gracefully.

# 1.2.0
* [feature] Added support for accurate sessions on multi-process apps.

# 1.0.2
* [fixed] Made Sessions more resilient to the FirebaseApp instance being deleted.

# 1.0.1
* [fixed] Fixed NPE when no version name is
  set ([#5195](https://github.com/firebase/firebase-android-sdk/issues/5195)).
* [fixed] Populate DataCollectionStatus fields for Crashlytics and Perf.

# 1.0.0
* [feature] Initial Firebase sessions library.

