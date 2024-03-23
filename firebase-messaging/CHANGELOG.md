# Unreleased
* [changed] Switched Firelog to use the new TransportBackend.

# 23.4.1
* [changed] Bump internal dependencies.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-messaging` library. The Kotlin extensions library has no additional
updates.

# 24.0.0
* [changed] Called messageHandled() after a message has been handled to indicate
  that the message has been handled successfully.
* [changed] Added an internal identifier to Firelog logging for compliance.

# 23.3.1
* [changed] Added metadata to FirebaseInstanceIdReceiver to signal that it
  finishes background broadcasts after the message has been handled.
* [changed] Specified notification's dismiss intent target via action instead of
  component name.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-messaging` library. The Kotlin extensions library has no additional
updates.

# 23.3.0
* [changed] Added Kotlin extensions (KTX) APIs from `com.google.firebase:firebase-messaging-ktx`
  to `com.google.firebase:firebase-messaging` under the `com.google.firebase.messaging` package.
  For details, see the
  [FAQ about this initiative](https://firebase.google.com/docs/android/kotlin-migration)
* [deprecated] All the APIs from `com.google.firebase:firebase-messaging-ktx` have been added to
  `com.google.firebase:firebase-messaging` under the `com.google.firebase.messaging` package,
  and all the Kotlin extensions (KTX) APIs in `com.google.firebase:firebase-messaging-ktx` are
  now deprecated. As early as April 2024, we'll no longer release KTX modules. For details, see the
  [FAQ about this initiative](https://firebase.google.com/docs/android/kotlin-migration)


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-messaging` library. The Kotlin extensions library has no additional
updates.

# 23.2.1
* [changed] Changed to finish a background broadcast after the message has been
  handled, subject to a timeout. This keeps the `FirebaseMessagingService`'s
  process in an active state while it is handling an FCM message, up to the
  20 seconds allowed.

# 23.2.0
* [deprecated] Deprecated FCM upstream messaging. See the
  [FAQ](https://firebase.google.com/support/faq#fcm-23-deprecation) for more
  details.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-messaging` library. The Kotlin extensions library has no additional
updates.

# 23.1.2
* [fixed] Fixed a breakage related to Jetpack core library related to an
  [upstream update](https://android-review.googlesource.com/c/platform/frameworks/support/+/2399893).
* [changed] Updated JavaLite, protoc, protobuf-java-util to 3.21.11.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-messaging` library. The Kotlin extensions library has no additional
updates.

# 23.1.1
* [fixed] Fixed deadlock when handling simultaneous messages.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-messaging` library. The Kotlin extensions library has no additional
updates.

# 23.1.0
* [unchanged] Updated to accommodate the release of the updated
  [messaging_longer] Kotlin extensions library.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-messaging` library. The Kotlin extensions library has the following
additional updates:

* [feature] Firebase now supports Kotlin coroutines.
  With this release, we added
  [`kotlinx-coroutines-play-services`](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-play-services/){: .external}
  to `firebase-messaging-ktx` as a transitive dependency, which exposes the
  `Task<T>.await()` suspend function to convert a
  [`Task`](https://developers.google.com/android/guides/tasks) into a Kotlin
  coroutine.

# 23.0.8
* [changed] Updated dependency of `play-services-basement` to its latest
  version (v18.1.0).


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-messaging` library. The Kotlin extensions library has no additional
updates.

# 23.0.7
* [changed] Message broadcasts now finish immediately after binding to the
  service. This change should reduce the chance of ANRs.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-messaging` library. The Kotlin extensions library has no additional
updates.

# 23.0.6
* [changed] Added the `POST_NOTIFICATIONS` permission to enable posting
  notifications when targeting SDK level 33. See [messaging] guidance
  on how to [request runtime notification permission on Android 13+](/docs/cloud-messaging/android/client#request-permission13)
* [fixed] Added an annotation to an internal class to fix a missing class
  warning.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-messaging` library. The Kotlin extensions library has no additional
updates.

# 23.0.5
* [fixed] Fixed a dependency on the `firebase-datatransport` layer.
  ([GitHub #3716](https://github.com/firebase/firebase-android-sdk/issues/3716){: .external})
* [fixed] Upgraded logging priority for message delivery events to avoid
  dropped logs.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-messaging` library. The Kotlin extensions library has no additional
updates.

# 23.0.3
* [fixed] Removed test resources from library.
* [fixed] Changed to catch `RuntimeException` when getting the `Bundle` from
  an `Activity Intent` while checking for notification analytics data.
* [changed] Internal changes to notification building methods.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-messaging` library. The Kotlin extensions library has no additional
updates.

# 23.0.2
* [fixed] Fixed an issue where the messaging component in
  the [firebase_bom_long] leaked the `httpcomponents` transitive dependencies.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-messaging` library. The Kotlin extensions library has no additional
updates.

# 23.0.1
* [changed] Updated to the latest version of the `firebase-datatransport`
  library.
* [changed] Updated dependencies of `play-services-basement`,
  `play-services-base`, and `play-services-tasks` to their latest versions
  (v18.0.0, v18.0.1, and v18.0.1, respectively). For more information, see the
  [note](#basement18-0-0_base18-0-1_tasks18-0-1) at the top of this release
  entry.
* [fixed] On Android 7.0 and earlier, the SDK now logs that a notification was
  opened after `onActivityCreated` to avoid a race condition when unparceling
  the extras Bundle.
* [fixed] Switched to stopping an image download by canceling a `Future` to
  interrupt the download thread. This change avoids errors that can occur in the
  image downloading library when trying to close the stream on a different thread
  than the one that started the download.
* [fixed] Fixed reference documentation for [`RemoteMessage.getMessageId()`](/docs/reference/android/com/google/firebase/messaging/RemoteMessage#public-string-getmessageid)
  and updated obsolete references to Google Cloud Messaging (GCM).


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-messaging` library. The Kotlin extensions library has no additional
updates.

# 23.0.0
* [changed] Due to its
  [dependency on Google Play services](/docs/android/android-play-services),
  this SDK now requires devices and emulators to target API level 19 (KitKat)
  or higher and to use Android 4.4 or higher.
* [feature] Added methods for determining and controlling whether Google
  Play services is set as the app’s notification delegate. By default, FCM
  will now set Google Play services as the app’s notification delegate so
  that it is allowed to display notifications for the app. This could be used
  in the future to show an app’s notifications without needing to start the
  app, which may improve message reliability and timeliness.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-messaging` library. The Kotlin extensions library has no additional
updates.

# 22.0.0
* [changed] Removed dependency on the deprecated Firebase Instance ID SDK.
  Caution: **This is a breaking change for apps that use [messaging] and the
  deprecated Firebase Instance ID API to manage registration tokens.**<br>We
  strongly recommend
  [migrating to [messaging]'s token APIs](/docs/projects/manage-installations#fid-iid).
  If you're unable to migrate to the replacement APIs, add a direct dependency
  on the `firebase-iid` library to your `build.gradle` file.
* [feature] Changed to open an `Activity` directly when a notification is
  tapped instead of passing it through `FirebaseMessagingService`. This change
  is to comply with Android 12 notification trampoline restrictions.
* [feature] Internal changes to use proto instead of JSON for logging.
* [changed] Internal changes to support dynamic feature modules.
* [changed] Internal infrastructure improvements.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-messaging` library. The Kotlin extensions library has no additional
updates.

# 21.1.0
* [feature] Migrated internal handling of new token callbacks and
  notification actions from Firebase Instance ID to [firebase_messaging].
* [feature] Added functionality to generate [messaging] tokens from
  `FirebaseMessaging.getToken`, while continuing to call through to Firebase
  Instance ID if it is present. This will allow [firebase_messaging] to
  remove its dependency on Firebase Instance ID in the future.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-messaging` library. The Kotlin extensions library has no additional
updates.

# 21.0.1
* [changed] Updated to latest version of the `firebase-datatransport` library.
* [feature] The SDK now gracefully handles missing default resources.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-messaging` library. The Kotlin extensions library has no additional
updates.

# 21.0.0
* [feature] Migrated auto-initialization from Firebase Instance ID to
  [firebase_messaging].
* [feature] Added a check for incompatible versions of Firebase Instance ID.
  An exception is thrown during instantiation if one is found.
* [fixed] Fixed an issue where events were erronously logged to
  [firebase_analytics] twice.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-messaging` library. The Kotlin extensions library has no additional
updates.

# 20.3.0
* [feature] Added
  [`getToken`](/docs/reference/android/com/google/firebase/messaging/FirebaseMessaging.html#getToken())
  and
  [`deleteToken`](/docs/reference/android/com/google/firebase/messaging/FirebaseMessaging.html#deleteToken())
  methods directly to `FirebaseMessaging`.
* [changed] Internal changes to the Google Play services interface to improve
  future development velocity.


## Kotlin
* [feature] The [messaging_longer] Android library with Kotlin
  extensions is now available. The Kotlin extensions library transitively
  includes the base `firebase-messaging` library. To learn more,  visit the
  [[messaging_longer] KTX documentation](/docs/reference/kotlin/com/google/firebase/messaging/ktx/package-summary).

# 20.2.4
* [changed] Internal changes to ensure functionality alignment with other SDK
  releases. For more information, refer to the
  [Firebase Installations v16.3.3 release notes](/support/release-notes/android#installations_v16-3-3).

# 20.2.3
* [fixed] Fixed an issue that caused an app to crash when a user tapped on a
  received notification.

# 20.2.2
Warning: **This version of `firebase-messaging` (v20.2.2) should not be used.**
It has a known issue that causes an app to crash when a user taps on a
received notification. A fix for this issue was released on July 08, 2020
(see [v20.2.3](/support/release-notes/android#messaging_v20-2-3)).

* [changed] Internal improvements.

# 20.2.1
* [changed] Internal changes to ensure functionality alignment with other SDK
  releases. For more information, refer to the
  [Firebase Instance ID v20.2.1 release notes](/support/release-notes/android#iid_v20-2-1).

# 20.2.0
* [changed] Internal changes to ensure functionality alignment with other SDK
  releases (for more information, refer to the
  [[messaging_longer] Direct Boot v20.2.0 release notes](/support/release-notes/android#messaging-directboot_v20-2-0)).

# 20.1.7
* [changed] Internal changes to ensure functionality alignment with other SDK
  releases (for more information, refer to the
  [Firebase Instance ID v20.1.7 release notes](/support/release-notes/android#iid_v20-1-7)).

# 20.1.6
* [fixed] Fixed a bug in topic syncing that was causing increased usage of
  shared system resources when waiting for a working network connection.

# 20.1.5
Warning: In some cases, `firebase-messaging` v20.1.4 and v20.1.5 are erroneously
consuming system resources which can negatively impact the performance of a
user's device. To avoid this effect, **update your app's version of
`firebase-messaging` to
[v20.1.6](/support/release-notes/android#messaging_v20-1-6) or later**.

* [changed] Internal changes to ensure functionality alignment with other SDK releases.

# 20.1.4
Warning: In some cases, `firebase-messaging` v20.1.4 and v20.1.5 are erroneously
consuming system resources which can negatively impact the performance of a
user's device. To avoid this effect, **update your app's version of
`firebase-messaging` to
[v20.1.6](/support/release-notes/android#messaging_v20-1-6) or later**.

* [changed] Internal changes to ensure functionality alignment with other SDK releases.

# 20.1.3
* [changed] Internal changes to ensure functionality alignment with other SDK
  releases.

# 20.1.2
**As of v20.1.1, the [messaging_longer] SDK depends on the
[installations_sdk]. Learn about possible impacts in the
[v20.1.1 release notes](/support/release-notes/android#messaging_v20-1-1).**

* [changed] Internal changes to ensure functionality alignment with other
  SDK releases (for more information, refer to the bug fix in the [Firebase
  Instance ID v20.1.1 release notes](/support/release-notes/android#iid_v20-1-1)).

# 20.1.1
Warning: This version of `firebase-messaging` (v20.1.1) has known issues
involving silent failures and should not be used. A fix for these issues was
released on [March 03, 2020](/support/release-notes/android#2020-03-03).

* [changed] Changed the default for notification titles. Previously, an empty
  title was replaced with the app's label, but now an empty title causes the
  notification title to be omitted.
* [fixed] Fixed an issue that could cause ANRs when receiving messages.
* [changed] [messaging_longer] now transitively depends on the
  [installations_sdk]. After updating to the latest dependency versions, make
  sure that push notifications still work as expected. Also, be aware of the
  following:

  * The [messaging] registration tokens of installed instances of your apps
    might change once after updating dependencies to their latest versions. To
    learn if your apps are affected, review the
    <a href="//github.com/firebase/firebase-android-sdk/blob/master/firebase-installations/FCM_TOKENS_CHANGE.md"
       class="external">[firebase_installations] documentation</a>. Also,
    make sure to
    <a href="/docs/cloud-messaging/android/client#monitor-token-generation">monitor
      [messaging] registration token generation</a> using the
    <code>#onNewToken</code> implementation.

  * Apps that use the Firebase auto-initialization process and the Gradle plugin
    to convert `google-services.json` into resources are unaffected. However,
    apps that create their own `FirebaseOptions` instances must provide a valid
    API key, Firebase project ID, and application ID.

# 20.1.0
* [feature] Added
  [`setDeliveryMetricsExportToBigQuery(boolean)`](/docs/reference/android/com/google/firebase/messaging/FirebaseMessaging.html#setDeliveryMetricsExportToBigQuery(boolean))
  and
  [`deliveryMetricsExportToBigQueryEnabled()`](/docs/reference/android/com/google/firebase/messaging/FirebaseMessaging.html#deliveryMetricsExportToBigQueryEnabled())
  to control and query if messsage delivery metrics are exported to BigQuery.
* [changed] Changed to catch and log NullPointerException when trying to close
  the image download stream. This NPE can happen if the image download takes too
  long and times out.

# 20.0.1
* [fixed] Fixed notifications on API level 24 and later to display the event
  time when `event_time` is set.

# 20.0.0
* [feature] Added support for more Android notification parameters, including:
  `ticker`, `sticky`,`event_time`, `local_only`, `notification_priority`,
  `default_sound`, `default_vibrate_timings`, `default_light_settings`,
  `visibility`, `notification_count`, `vibrate_timings` and `light_settings`.
* [feature] Added support for Android notifications that include an image.
* [changed] Added nullability annotations to improve the Kotlin developer
  experience.

# 19.0.1
* [fixed] Fixed an issue where `FirebaseMessagingService.onNewToken` would be
  invoked for tokens created for non-default FirebaseApp instances.
* [fixed] SDK now only retries topic subscriptions and token registration on
  the following errors: "ERROR_SERVICE_NOT_AVAILABLE" and
  "ERROR_INTERNAL_SERVER_ERROR".

# 18.0.0
* [changed] Updated minSdkVersion to API level 16.

# 17.6.0
* [feature] Added functionality to automatically download and show an image in
  a notification message. Retrieve the image URL set in the message with the
  `getImageUrl` method in
  [`RemoteMessage.Notification`](/docs/reference/android/com/google/firebase/messaging/RemoteMessage.Notification).

# 17.5.0
* [changed] Added internal improvements and refactored code.

# 17.4.0
* [feature] Added `getChannelId` method to [`RemoteMessage.Notification`](/docs/reference/android/com/google/firebase/messaging/RemoteMessage.Notification) for getting the channel ID set in a notification message.
* [fixed] Fixed a rare `ClassCastException` while receiving a message.

# 17.3.4
* [fixed] Bug fixes and internal improvements.

# 17.3.2
* [fixed] Fixed an issue that would occasionally cause apps to crash with
  Android Not Responding (ANR) errors when receiving a message.

# 17.3.0
* [changed] Incremented the version number to 17.3.0 due to internal SDK
  changes. These changes do not affect client functionality, and developers
  do not need to take any action.

# 17.1.0
* [feature] Added `onNewToken` method to [`FirebaseMessagingService`](/docs/reference/android/com/google/firebase/messaging/FirebaseMessagingService) which is invoked when the app gets a new Instance ID token or its existing token changes.

# 17.0.0
* [feature] Added `getPriority()` and `getOriginalPriority()` methods to
  [`RemoteMessage`](/docs/reference/android/com/google/firebase/messaging/RemoteMessage).
* [changed] The methods `subscribeToTopic()` and `unsubscribeFromTopic()` on
  [`FirebaseMessaging`](/docs/reference/android/com/google/firebase/messaging/FirebaseMessaging)
  now return a
  [`Task`](/docs/reference/android/com/google/android/gms/tasks/Task) that can
  be used to see when the request has completed.

