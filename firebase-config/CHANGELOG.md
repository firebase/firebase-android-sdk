# Unreleased


# 22.0.0
* [changed] Bump internal dependencies


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-config` library. The Kotlin extensions library has no additional
updates.

# 21.6.3
* [fixed] Fixed a bug that could cause a crash if the app was backgrounded
  while it was listening for real-time Remote Config updates. For more information, see #5751

# 21.6.2
* [fixed] Fixed an issue that could cause [remote_config] personalizations to be logged early in
  specific cases.
* [fixed] Fixed an issue where the connection to the real-time Remote Config backend could remain
  open in the background.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-config` library. The Kotlin extensions library has no additional
updates.

# 21.6.1
* [changed] Bump internal dependencies.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-config` library. The Kotlin extensions library has no additional
updates.

# 21.6.0
* [changed] Added support for other Firebase products to integrate with [remote_config].

# 21.5.0
* [changed] Added Kotlin extensions (KTX) APIs from `com.google.firebase:firebase-config-ktx`
  to `com.google.firebase:firebase-config` under the `com.google.firebase.remoteconfig` package.
  For details, see the
  [FAQ about this initiative](https://firebase.google.com/docs/android/kotlin-migration)
* [deprecated] All the APIs from `com.google.firebase:firebase-config-ktx` have been added to
  `com.google.firebase:firebase-config` under the `com.google.firebase.remoteconfig` package,
  and all the Kotlin extensions (KTX) APIs in `com.google.firebase:firebase-config-ktx` are
  now deprecated. As early as April 2024, we'll no longer release KTX modules. For details, see the
  [FAQ about this initiative](https://firebase.google.com/docs/android/kotlin-migration)


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-config` library. The Kotlin extensions library has no additional
updates.

# 21.4.1
* [changed] Internal improvements to support Remote Config real-time updates.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-config` library. The Kotlin extensions library has no additional
updates.

# 21.4.0
* [unchanged] Updated to accommodate the release of the updated
  [remote_config] Kotlin extensions library.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-config` library. The Kotlin extensions library has the following
additional updates.

* [feature] Added the
  [`FirebaseRemoteConfig.configUpdates`](/docs/reference/kotlin/com/google/firebase/remoteconfig/ktx/package-summary#(com.google.firebase.remoteconfig.FirebaseRemoteConfig).configUpdates())
  Kotlin Flow to listen for real-time config updates.

# 21.3.0
* [feature] Added support for real-time config updates. To learn more, see
  [Get started with [firebase_remote_config]](/docs/remote-config/get-started?platform=android#add-real-time-listener).


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-config` library. The Kotlin extensions library has no additional
updates.

# 21.2.1
* [changed] Migrated [remote_config] to use standard Firebase executors.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-config` library. The Kotlin extensions library has no additional
updates.

# 21.2.0
* [unchanged] Updated to accommodate the release of the updated
  [remote_config] Kotlin extensions library.


## Kotlin
The Kotlin extensions library transitively includes the updated
  `firebase-config` library. The Kotlin extensions library has the following
  additional updates:

* [feature] Firebase now supports Kotlin coroutines.
  With this release, we added
  [`kotlinx-coroutines-play-services`](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-play-services/){: .external}
  to `firebase-config-ktx` as a transitive dependency, which exposes the
  `Task<T>.await()` suspend function to convert a
  [`Task`](https://developers.google.com/android/guides/tasks) into a Kotlin
  coroutine.

# 21.1.2
* [changed] Updated dependency of `play-services-basement` to its latest
  version (v18.1.0).


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-config` library. The Kotlin extensions library has no additional
updates.

# 21.1.1
* [fixed] Fixed a bug that caused HTTP errors in some locales. For more
  information, see
  <a href="https://github.com/firebase/firebase-android-sdk/issues/3757"
     class="external">GitHub Issue #3757</a>


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-config` library. The Kotlin extensions library has no additional
updates.

# 21.1.0
<aside class="caution">This version contains a bug that causes HTTP errors in
  some locales. We recommend updating to the latest version (v21.1.1+) which
  contains a fix.
  For more information, see
  <a href="https://github.com/firebase/firebase-android-sdk/issues/3757"
     class="external">GitHub Issue #3757</a>
</aside>

* [changed] Added first-open time to [remote_config] server requests.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-config` library. The Kotlin extensions library has no additional
updates.

# 21.0.2
* [changed] Updated dependencies of `play-services-basement`,
  `play-services-base`, and `play-services-tasks` to their latest versions
  (v18.0.0, v18.0.1, and v18.0.1, respectively). For more information, see the
  [note](#basement18-0-0_base18-0-1_tasks18-0-1) at the top of this release
  entry.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-config` library. The Kotlin extensions library has no additional
updates.

# 21.0.1
* [fixed] Fixed a bug in the initialization of [remote_config] with a
  non-primary Firebase app.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-config` library. The Kotlin extensions library has no additional
updates.

# 21.0.0
* [changed] Internal infrastructure improvements.
* [changed] Internal changes to support dynamic feature modules.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-config` library. The Kotlin extensions library has no additional
updates.

# 20.0.4
* [changed] Improved condition targeting signals.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-config` library. The Kotlin extensions library has no additional
updates.

# 20.0.3
* [changed] Standardize support for other Firebase products that integrate
  with [remote_config].


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-config` library. The Kotlin extensions library has no additional
updates.

# 20.0.2
* [fixed] Fixed an issue that was causing [remote_config] to return the
  static default value even if a remote value was defined. (#2186)


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-config` library. The Kotlin extensions library has no additional
updates.

# 20.0.1
* [changed] Added support for other Firebase products to integrate with
  [remote_config].


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-config` library. The Kotlin extensions library has no additional
updates.

# 20.0.0
* [removed] Removed the protocol buffer dependency. Also, removed support for
  configs saved on device using the legacy protocol buffer format (the SDK
  stopped using this legacy format starting with [remote_config] v16.3.0).
* [removed] Removed the deprecated synchronous method
  `FirebaseRemoteConfig.activateFetched()`. Use the asynchronous
  [`FirebaseRemoteConfig.activate()`](/docs/reference/android/com/google/firebase/remoteconfig/FirebaseRemoteConfig#activate())
  instead.
* [removed] Removed the deprecated synchronous methods
  `FirebaseRemoteConfig.setDefaults(int)` and
  `FirebaseRemoteConfig.setDefaults(Map<String,Object>)`. Use the asynchronous
  [`FirebaseRemoteConfig.setDefaultsAsync(int)`](/docs/reference/android/com/google/firebase/remoteconfig/FirebaseRemoteConfig#setDefaultsAsync(int))
  and [`FirebaseRemoteConfig.setDefaultsAsync(Map<String,Object>)`](/docs/reference/android/com/google/firebase/remoteconfig/FirebaseRemoteConfig#setDefaultsAsync(Map<String,Object>))
  instead.
* [removed] Removed the deprecated synchronous method
  `FirebaseRemoteConfig.setConfigSettings(FirebaseRemoteConfigSettings)`.
  Use the asynchronous
  [`FirebaseRemoteConfig.setConfigSettingsAsync(FirebaseRemoteConfigSettings)`](/docs/reference/android/com/google/firebase/remoteconfig/FirebaseRemoteConfig#setConfigSettingsAsync(FirebaseRemoteConfigSettings))
  instead.
* [removed] Removed the deprecated method
  `FirebaseRemoteConfig.getByteArray(String)`. Use
  [`FirebaseRemoteConfig.getString(String)`](/docs/reference/android/com/google/firebase/remoteconfig/FirebaseRemoteConfig#getString(String))
  instead.
* [removed] Removed the deprecated methods
  `FirebaseRemoteConfigSettings.isDeveloperModeEnabled()` and
  `FirebaseRemoteConfigSettings.Builder.setDeveloperModeEnabled(boolean)`. Use
  [`FirebaseRemoteConfigSettings#getMinimumFetchIntervalInSeconds()`](/docs/reference/android/com/google/firebase/remoteconfig/FirebaseRemoteConfigSettings#getMinimumFetchIntervalInSeconds())
  and [`FirebaseRemoteConfigSettings.Builder#setMinimumFetchIntervalInSeconds(long)`](/docs/reference/android/com/google/firebase/remoteconfig/FirebaseRemoteConfigSettings.Builder#setMinimumFetchIntervalInSeconds(long))
  instead.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-config` library. The Kotlin extensions library has no additional
updates.

# 19.2.0
* [changed] Migrated to use the [firebase_installations] service _directly_
  instead of using an indirect dependency via the Firebase Instance ID SDK.

  {% include "docs/reference/android/client/_includes/_iid-indirect-dependency-solutions.html" %}
* [changed] Updated the protocol buffer dependency to the newer
  `protobuf-javalite` artifact. The new artifact is incompatible with the old
  one, so this library needed to be upgraded to avoid conflicts. No developer
  action is necessary.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-config` library. The Kotlin extensions library has no additional
updates.

# 19.1.4
* [changed] Updated dependency on the Firebase Instance ID library to v20.1.5,
  which is a step towards a direct dependency on the Firebase installations
  service in a future release.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-config` library. The Kotlin extensions library has no additional
updates.

# 19.1.3
* [fixed] Fixed an issue where [`FirebaseRemoteConfig.fetch()`](/docs/reference/android/com/google/firebase/remoteconfig/FirebaseRemoteConfig.html#fetch())
would sometimes report a misformatted language tag.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-config` library. The Kotlin extensions library has no additional
updates.

# 19.1.2
* [fixed] Resolved known issue where
  [`FirebaseRemoteConfigSettings.Builder.setFetchTimeoutInSeconds()`](/docs/reference/android/com/google/firebase/remoteconfig/FirebaseRemoteConfigSettings.Builder)
  was not always honored.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-config` library. The Kotlin extensions library has no additional
updates.

# 19.1.1
* [changed] Updated [`FirebaseRemoteConfig.fetch()`](docs/reference/android/com/google/firebase/remoteconfig/FirebaseRemoteConfig.html#fetch())
implementation to use [`FirebaseInstanceId.getInstanceId()`](/docs/reference/android/com/google/firebase/iid/FirebaseInstanceId.html#getInstanceId())
in favor of the deprecated [`FirebaseInstanceId.getToken()`](/docs/reference/android/com/google/firebase/iid/FirebaseInstanceId.html#getToken()).


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-config` library. The Kotlin extensions library has no additional
updates.

# 19.1.0
* [changed] Added getters to the fields of the
  [`FirebaseRemoteConfigSettings.Builder`](/docs/reference/android/com/google/firebase/remoteconfig/FirebaseRemoteConfigSettings.Builder)
  object to provide better Kotlin patterns.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-config` library. The Kotlin extensions library has no additional
updates.

# 19.0.4
* [fixed] Resolved
  [known issue](//github.com/firebase/firebase-android-sdk/issues/973) where
  network calls may fail on devices using API 19 and earlier.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-config` library. The Kotlin extensions library has no additional
updates.

# 19.0.3
* [fixed] Resolved
  [known issue](https://github.com/firebase/firebase-android-sdk/issues/787)
  where the [firebase_remote_config] SDK threw an error when Android
  [StrictMode](https://developer.android.com/reference/android/os/StrictMode)
  was turned on.
* [fixed] Resolved issue where setting Byte Arrays via
  [`FirebaseRemoteConfig.setDefaultsAsync(int)`](/docs/reference/android/com/google/firebase/remoteconfig/FirebaseRemoteConfig#setDefaultsAsync(int)),
  [`FirebaseRemoteConfig.setDefaultsAsync(Map<String,Object>)`](/docs/reference/android/com/google/firebase/remoteconfig/FirebaseRemoteConfig#setDefaultsAsync(Map<String,Object>))
  and their synchronous counterparts would cause `getByteArray` to return an
  object reference instead of the Byte Array. Byte Arrays set via the
  Firebase console were unaffected by this bug.


## Kotlin
The Kotlin extensions library transitively includes the updated
`firebase-config` library. The Kotlin extensions library has no additional
updates.

# 19.0.2
* [unchanged] Updated to accommodate the release of the [remote_config]
  Kotlin extensions library.


## Kotlin
* [feature] The beta release of a [remote_config] Android library with
  Kotlin extensions is now available. The Kotlin extensions library transitively
  includes the base `firebase-config` library. To learn more,  visit the
  [[remote_config] KTX documentation](/docs/reference/kotlin/com/google/firebase/remoteconfig/ktx/package-summary).

# 19.0.1
* [fixed] Resolved known issue where certain unicode characters were not
  encoded correctly. The issue was introduced in v19.0.0.

# 19.0.0
* [changed] Versioned to add nullability annotations to improve the Kotlin
  developer experience. No other changes.

# 17.0.0
* [feature] Added an asynchronous way to set config settings: [`FirebaseRemoteConfig.setConfigSettingsAsync(FirebaseRemoteConfigSettings)`](/docs/reference/android/com/google/firebase/remoteconfig/FirebaseRemoteConfig#setConfigSettingsAsync(FirebaseRemoteConfigSettings)).
* [feature] Added [`FirebaseRemoteConfigServerException`](/docs/reference/android/com/google/firebase/remoteconfig/FirebaseRemoteConfigServerException) and [`FirebaseRemoteConfigClientException`](/docs/reference/android/com/google/firebase/remoteconfig/FirebaseRemoteConfigClientException) to provide more nuanced error reporting.
* [changed] Updated all "cache expiration" references to "minimum fetch interval" and "cache" references to "local storage".
* [deprecated] Deprecated developer mode. Use [`FirebaseRemoteConfigSettings.Builder.setMinimumFetchIntervalInSeconds(0L)`](/docs/reference/android/com/google/firebase/remoteconfig/FirebaseRemoteConfigSettings.Builder#setMinimumFetchIntervalInSeconds(long)) instead.
* [deprecated] Deprecated the synchronous [`FirebaseRemoteConfig.setConfigSettings(FirebaseRemoteConfigSettings)`](/docs/reference/android/com/google/firebase/remoteconfig/FirebaseRemoteConfig#setConfigSettings(FirebaseRemoteConfigSettings)). Use the asynchronous  [`FirebaseRemoteConfig.setConfigSettingsAsync(FirebaseRemoteConfigSettings)`](/docs/reference/android/com/google/firebase/remoteconfig/FirebaseRemoteConfig#setConfigSettingsAsync(FirebaseRemoteConfigSettings)) instead.
* [deprecated] Deprecated [`FirebaseRemoteConfigFetchException`](/docs/reference/android/com/google/firebase/remoteconfig/FirebaseRemoteConfigFetchException). Use the more granular [`FirebaseRemoteConfigServerException`](/docs/reference/android/com/google/firebase/remoteconfig/FirebaseRemoteConfigServerException) and [`FirebaseRemoteConfigClientException`](/docs/reference/android/com/google/firebase/remoteconfig/FirebaseRemoteConfigClientException) instead.
* [removed] Removed all namespace methods.
* [removed] Removed all default constructors for Exception classes.
* [changed] Updated minSdkVersion to API level 16.

# 16.5.0
* [feature] Enabled multi-App support. Use [`FirebaseRemoteConfig.getInstance(FirebaseApp)`](/docs/reference/android/com/google/firebase/remoteconfig/FirebaseRemoteConfig#getInstance(FirebaseApp)) to retrieve a singleton instance of [`FirebaseRemoteConfig`](/docs/reference/android/com/google/firebase/remoteconfig/FirebaseRemoteConfig) for the given [`FirebaseApp`](/docs/reference/android/com/google/firebase/FirebaseApp).
* [feature] Added a method that fetches configs and activates them: [`FirebaseRemoteConfig.fetchAndActivate()`](/docs/reference/android/com/google/firebase/remoteconfig/FirebaseRemoteConfig#fetchAndActivate()).
* [feature] Network connection timeout for fetch requests is now customizable. To set the network timeout, use [`FirebaseRemoteConfigSettings.Builder.setFetchTimeoutInSeconds(long)`](/docs/reference/android/com/google/firebase/remoteconfig/FirebaseRemoteConfigSettings.Builder#setFetchTimeoutInSeconds(long)).
* [feature] The default minimum fetch interval is now customizable. To set the default minimum fetch interval, use [`FirebaseRemoteConfigSettings.Builder.setMinimumFetchIntervalInSeconds(long)`](/docs/reference/android/com/google/firebase/remoteconfig/FirebaseRemoteConfigSettings.Builder#setMinimumFetchIntervalInSeconds(long)).
* [feature] Added a way to get all activated configs as a Java `Map`: [`FirebaseRemoteConfig.getAll()`](/docs/reference/android/com/google/firebase/remoteconfig/FirebaseRemoteConfig#getAll()).
* [feature] Added the ability to reset a Firebase Remote Config instance: [`FirebaseRemoteConfig.reset()`](/docs/reference/android/com/google/firebase/remoteconfig/FirebaseRemoteConfig#reset()).
* [feature] Added a way to determine if the Firebase Remote Config instance has finished initializing. To get a task that will complete when the Firebase Remote Config instance is finished initializing, use [`FirebaseRemoteConfig.ensureInitialized()`](/docs/reference/android/com/google/firebase/remoteconfig/FirebaseRemoteConfig#ensureInitialized()).
* [feature] Added an asynchronous way to activate configs: [`FirebaseRemoteConfig.activate()`](/docs/reference/android/com/google/firebase/remoteconfig/FirebaseRemoteConfig#activate()).
* [feature] Added an asynchronous way to set defaults: [`FirebaseRemoteConfig.setDefaultsAsync(int)`](/docs/reference/android/com/google/firebase/remoteconfig/FirebaseRemoteConfig#setDefaultsAsync(int)) and [`FirebaseRemoteConfig.setDefaultsAsync(Map<String,Object>)`](/docs/reference/android/com/google/firebase/remoteconfig/FirebaseRemoteConfig#setDefaultsAsync(Map<String,Object>)).
* [deprecated] Deprecated the synchronous [`FirebaseRemoteConfig.activateFetched()`](/docs/reference/android/com/google/firebase/remoteconfig/FirebaseRemoteConfig#activateFetched()). Use the asynchronous [`FirebaseRemoteConfig.activate()`](/docs/reference/android/com/google/firebase/remoteconfig/FirebaseRemoteConfig#activate()) instead.
* [deprecated] Deprecated the synchronous [`FirebaseRemoteConfig.setDefaults(int)`](/docs/reference/android/com/google/firebase/remoteconfig/FirebaseRemoteConfig#setDefaults(int)) and [`FirebaseRemoteConfig.setDefaults(Map<String,Object>)`](/docs/reference/android/com/google/firebase/remoteconfig/FirebaseRemoteConfig#setDefalts(Map<String,Object>)). Use the asynchronous  [`FirebaseRemoteConfig.setDefaultsAsync(int)`](/docs/reference/android/com/google/firebase/remoteconfig/FirebaseRemoteConfig#setDefaultsAsync(int)) and [`FirebaseRemoteConfig.setDefaultsAsync(Map<String,Object>)`](/docs/reference/android/com/google/firebase/remoteconfig/FirebaseRemoteConfig#setDefaultsAsync(Map<String,Object>)) instead.
* [deprecated] Deprecated [`FirebaseRemoteConfig.getByteArray(String)`](/docs/reference/android/com/google/firebase/remoteconfig/FirebaseRemoteConfig#getByteArray(String)).
* [deprecated] Deprecated all methods with a namespace parameter.

# 16.4.1
* [changed] The SDK now enforces Android API Key restrictions.
* [fixed] Resolved known issue where the local cache was not honored even if
  it had not expired. The issue was introduced in version 16.3.0.

# 16.4.0
* [changed] Internal changes to ensure functionality alignment with other SDK releases.

# 16.3.0
* [changed] The [firebase_remote_config] SDK requires the
  [firebase_remote_config] REST API. For Firebase projects created before
  March 7, 2018, you must manually enable the REST API. For more information,
  see our
  [[remote_config] REST API user guide](https://firebase.google.com/docs/remote-config/use-config-rest#before_you_begin_enable_the_rest_api).
* [changed] Refactored the implementation of [remote_config] to improve SDK
  stability and speed, and to remove the Google Play Services dependency.
* [changed] Improved error logs and exception messages.
* [changed] Updated the Android documentation to reflect that
  [remote_config] uses `Locale` to retrieve location information, similar to
  iOS's use of `countryCode`.

# 16.1.3
* [fixed] Fixed an issue where [remote_config] experiments were not
  collecting results.

# 16.1.0
* [fixed] Bug fixes and internal improvements to support Firebase Performance Monitoring features.

