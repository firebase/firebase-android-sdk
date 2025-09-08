# Unreleased

# 19.0.0

- [changed] **Breaking Change**: Updated minSdkVersion to API level 23 or higher.

# 18.0.1

- [fixed] updated proguard rules to keep component registrar working with newer proguard versions.

## Kotlin

The Kotlin extensions library transitively includes the updated `firebase-components` library. The
Kotlin extensions library has no additional updates.

# 17.1.2

- [changed] Internal changes to ensure only one interface is provided for
  kotlinx.coroutines.CoroutineDispatcher interfaces when both firebase-common and
  firebase-common-ktx provide them.
