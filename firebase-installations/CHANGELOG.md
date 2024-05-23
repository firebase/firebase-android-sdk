# Unreleased


# 18.0.0
* [changed] Bump internal dependencies


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-installations` library. The Kotlin extensions library has no additional
updates.

# 17.2.0
* [changed] Added Kotlin extensions (KTX) APIs from `com.google.firebase:firebase-installations-ktx`
  to `com.google.firebase:firebase-installations` under the `com.google.firebase.installations` package.
  For details, see the
  [FAQ about this initiative](https://firebase.google.com/docs/android/kotlin-migration)
* [deprecated] All the APIs from `com.google.firebase:firebase-installations-ktx` have been added to
  `com.google.firebase:firebase-installations` under the `com.google.firebase.installations` package,
  and all the Kotlin extensions (KTX) APIs in `com.google.firebase:firebase-installations-ktx` are
  now deprecated. As early as April 2024, we'll no longer release KTX modules. For details, see the
  [FAQ about this initiative](https://firebase.google.com/docs/android/kotlin-migration)


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-installations` library. The Kotlin extensions library has no additional
updates.

# 17.1.3
* [changed] Internal changes to improve startup time

# 17.1.2
* [fixed] Updated `firebase-common` to its latest version (v20.3.0) to fix an issue that was 
  causing a nondeterministic crash on startup.

# 17.1.0
* [changed] Internal changes to ensure functionality alignment with other
  SDK releases. For more details, refer to the
  [Firebase Instance ID v21.1.0 release notes](/support/release-notes/android#iid_v21-1-0).

