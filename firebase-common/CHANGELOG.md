# Unreleased

# 22.0.0
* [changed] **Breaking Change**: Updated minSdkVersion to API level 23 or higher.
* [removed] **Breaking Change**: Stopped releasing the deprecated Kotlin extensions
  (KTX) module and removed it from the Firebase Android BoM. Instead, use the KTX APIs
  from the main module. For details, see the
  [FAQ about this initiative](https://firebase.google.com/docs/android/kotlin-migration).

# 21.0.0
* [fixed] Correctly declare dependency on firebase-components, issue #5732
* [changed] Added extension method `Random.nextAlphanumericString()` (PR #5818)
* [changed] Migrated internal `SharedPreferences` usages to `DataStore`. ([GitHub PR #6801](https://github.com/firebase/firebase-android-sdk/pull/6801){ .external})

# 20.4.0
* [changed] Added Kotlin extensions (KTX) APIs from `com.google.firebase:firebase-common-ktx`
to `com.google.firebase:firebase-common` under the `com.google.firebase` package.
For details, see the
[FAQ about this initiative](https://firebase.google.com/docs/android/kotlin-migration)

## Kotlin
* [deprecated] All the APIs from `com.google.firebase:firebase-common-ktx` have been added to
`com.google.firebase:firebase-common` under the `com.google.firebase package`, and all the
Kotlin extensions (KTX) APIs in `com.google.firebase:firebase-common-ktx` are now deprecated.
As early as April 2024, we'll no longer release KTX modules. For details, see the
FAQ about this initiative.
[FAQ about this initiative](https://firebase.google.com/docs/android/kotlin-migration)

# 20.3.3
* [fixed] Addressed issue with C++ being absent in user agent.

## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-common` library. The Kotlin extensions library has no additional
updates
