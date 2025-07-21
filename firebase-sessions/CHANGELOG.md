# Unreleased


# 3.0.0
* [changed] Added internal api for Crashlytics to notify Sessions of crash events
* [changed] Use multi-process DataStore instead of Preferences DataStore
* [changed] Update the heuristic to detect cold app starts
* [changed] **Breaking Change**: Updated minSdkVersion to API level 23 or higher.

# 2.1.1
* [unchanged] Updated to keep SDK versions aligned.

# 2.1.0
* [changed] Add warning for known issue b/328687152
* [changed] Use Dagger for dependency injection
* [changed] Updated datastore dependency to v1.1.3 to
  fix [CVE-2024-7254](https://github.com/advisories/GHSA-735f-pc8j-v9w8).

# 2.0.9
* [fixed] Make AQS resilient to background init in multi-process apps.

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

