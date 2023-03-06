# Unreleased

# 20.3.1
* [fixed] Fixed nullpointer crash #4214
* [changed] Updated grpc to 1.52.1 and javalite, protoc, protobufjavautil to 3.21.11.

# 20.3.0
* [changed] Migrated [inappmessaging] Display to use standard Firebase
  executors.

* [changed] Moved Task continuations off the main thread.

* [feature] Added a new API for
  [removing a dismiss listener](/docs/reference/android/com/google/firebase/inappmessaging/FirebaseInAppMessaging#removeDismissListener(com.google.firebase.inappmessaging.FirebaseInAppMessagingDismissListener)).
  (GitHub
  [#4492](//github.com/firebase/firebase-android-sdk/issues/4492){: .external})


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-inappmessaging-display` library. The Kotlin extensions library has no additional
updates.


# 20.2.0
* [fixed] Fixed a bug that prevented marking more than one message as
  impressed.


## Kotlin
The Kotlin extensions library transitively includes the updated
  `firebase-inappmessaging-display` library. The Kotlin extensions library has
  the following additional updates:

* [feature] Firebase now supports Kotlin coroutines.
  With this release, we added
  [`kotlinx-coroutines-play-services`](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-play-services/){: .external}
  to `firebase-inappmessaging-display-ktx` as a transitive dependency, which
  exposes the `Task<T>.await()` suspend function to convert a
  [`Task`](https://developers.google.com/android/guides/tasks) into a Kotlin
  coroutine.

# 20.1.3
* [changed] Updated dependency of `play-services-basement` to its latest
  version (v18.1.0).


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-inappmessaging-display` library. The Kotlin extensions library has no
additional updates.

# 20.1.2
* [changed] Updated dependencies of `play-services-basement`,
  `play-services-base`, and `play-services-tasks` to their latest versions
  (v18.0.0, v18.0.1, and v18.0.1, respectively). For more information, see the
  [note](#basement18-0-0_base18-0-1_tasks18-0-1) at the top of this release
  entry.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-inappmessaging-display` library. The Kotlin extensions library has no
additional updates.

# 20.1.1
- [changed] Updated the gRPC dependency version.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-inappmessaging-display` library. The Kotlin extensions library has no
additional updates.

# 20.1.0
* [changed] Migrated to Glide library for image downloading.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-inappmessaging-display` library. The Kotlin extensions library has no
additional updates.

# 20.0.0
* [changed] Internal infrastructure improvements.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-inappmessaging-display` library. The Kotlin extensions library has no
additional updates.

# 19.1.5
* [fixed] Fixed `WindowManager$BadTokenException` when showing an in-app
  message.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-inappmessaging-display` library. The Kotlin extensions library has no
additional updates.

# 19.1.4
* [fixed] Fixed in-app message button click not working in Android 11
  because of package visibility changes.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-inappmessaging-display` library. The Kotlin extensions library has no
additional updates.

# 19.1.3
* [changed] Internal changes to maintain compatibility with other Firebase
  SDKs.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-inappmessaging-display` library. The Kotlin extensions library has no
additional updates.

# 19.1.2
* [changed] Internal changes to maintain compatibility with other Firebase
  SDKs.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-inappmessaging-display` library. The Kotlin extensions library has no
additional updates.

# 19.1.1
* [fixed] Improved link handling on devices without any browser installed
  or without Chrome installed.

* [feature] Added the ability to register a dismiss listener that reacts to
  message dismissal.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-inappmessaging-display` library. The Kotlin extensions library has no
additional updates.

# 19.1.0
* [changed] Updated the protocol buffer dependency to the newer
  `protobuf-javalite` artifact. The new artifact is incompatible with the old
  one, so this library needed to be upgraded to avoid conflicts.
  No developer action is necessary.



## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-inappmessaging-display` library. The Kotlin extensions library has no
additional updates.

# 19.0.7
* [fixed] Improved handling of activity transitions.
  (GitHub [Issue #1410](//github.com/firebase/firebase-android-sdk/issues/1410)
  and [Issue #1092](//github.com/firebase/firebase-android-sdk/issues/1092))

* [changed] Migrated to use the [firebase_installations] service _directly_
  instead of using an indirect dependency via the Firebase Instance ID SDK.

  {% include "docs/reference/android/client/_includes/_iid-indirect-dependency-solutions.html" %}



## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-inappmessaging-display` library. The Kotlin extensions library has no
additional updates.

# 19.0.6
* [fixed] Fixed issue causing apps to become unresponsive in limited network
  conditions. [GitHub Issue #1430](//github.com/firebase/firebase-android-sdk/issues/1430)


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-inappmessaging-display` library. The Kotlin extensions library has no
additional updates.

# 19.0.5
* [fixed] Fixed issue where campaigns with frequency limits were not properly
  displaying multiple times.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-inappmessaging-display` library. The Kotlin extensions library has no
additional updates.

# 19.0.4
* [fixed] Fixed issue with messages not being fetched on app first open.

* [fixed] Fixed issue with first foreground trigger not being picked up.



## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-inappmessaging-display` library. The Kotlin extensions library has no
additional updates.

# 19.0.3
* [changed] Internal changes to enable future SDK improvements.



## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-inappmessaging-display` library. The Kotlin extensions library has no
additional updates.

# 19.0.2
* [changed] Updated version of Dagger to 2.24.
* [fixed] Bug fixes to improve SDK stability.
* [fixed] Fixed memory leak.


## Kotlin
* [feature] The beta release of a [inappmessaging] Display Android library
  with Kotlin extensions is now available. The Kotlin extensions library
  transitively includes the base `firebase-inappmessaging-display` library.
  To learn more,  visit the
  [[inappmessaging] Display KTX documentation](/docs/reference/kotlin/com/google/firebase/inappmessaging/display/ktx/package-summary).

# 19.0.1
* [changed] Internal changes to accommodate open-sourcing of the library and
  to ensure functionality alignment with other SDK releases.

# 19.0.0
* [changed] Versioned to add nullability annotations to improve the Kotlin
  developer experience. No other changes.

# 18.0.2
* [changed] Updated to support Picasso version 2.71828.
* [fixed] Updated to send engagement metrics via [analytics].
* [fixed] Fixed issue with callbacks triggering for Card templates.

# 17.2.0
* [feature] Adds support for card in-app messages.
* [feature] Adds direct triggering (via [inappmessaging] SDK) of in-app
  messages.

# 17.1.1
* [fixed] Fixed [firestore] and [inappmessaging] compatibility on Android
  API level 19 (KitKat). The underlying issue was that [firestore] and
  [cloud_functions] couldn't agree on which ciphers to use; this update fixes
  this issue by overriding the set of ciphers that they use. Refer to
  [GitHub issue 244](https://github.com/firebase/firebase-android-sdk/issues/244)
  for more information.

# 17.1.0
* [feature] Adds functionality to programmatically register listeners for
  updates on in-app engagement (for example, impression, click, display errors).   See
  [`FirebaseInAppMessaging.addClickListener()`](/docs/reference/android/com/google/firebase/inappmessaging/FirebaseInAppMessaging.addClickListener())
  for more details.

# 17.0.5
* [fixed] Users with restricted API keys can now use the SDK as expected.

# 17.0.3
* [fixed] Improved caching logic, added safeguards for cache expiration, and cleaned up API surface to prepare for open sourcing.

# 17.0.1
* [fixed] Fixed an issue where [fiam] and Firestore could not be included/built into the same app, because of an obfuscation collision.

# 17.0.0
* [feature] The initial public beta release of the Firebase In-App Messaging Display SDK for Android is now available. To learn more, see the [Firebase In-App Messaging documentation](/docs/in-app-messaging).

