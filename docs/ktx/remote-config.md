# Remote Config Kotlin Extensions

## Getting Started

To use the Firebase Remote Config Android SDK with Kotlin Extensions, add the following
to your app's `build.gradle` file:

```groovy
// See maven.google.com for the latest versions
// This library transitively includes the firebase-config library
implementation 'com.google.firebase:firebase-config-ktx:$VERSION'
```

## Features

### Get the FirebaseRemoteConfig instance of the default app

**Kotlin**
```kotlin
val remoteConfig = FirebaseRemoteConfig.getInstance()
```

**Kotlin + KTX**
```kotlin
val remoteConfig = Firebase.remoteConfig
```

### Get the FirebaseRemoteConfig of a given FirebaseApp

**Kotlin**
```kotlin
val remoteConfig = FirebaseRemoteConfig.getInstance(app)
```

**Kotlin + KTX**
```kotlin
val remoteConfig = Firebase.remoteConfig(app)
```

### Get parameter values from FirebaseRemoteConfig

**Kotlin**
```kotlin
val isEnabled = remoteConfig.getBoolean("is_enabled")

val fileBytes = remoteConfig.getByteArray("file_bytes")

val audioVolume = remoteConfig.getDouble("audio_volume")

val maxCharacters = remoteConfig.getLong("max_characters")

val accessKey = remoteConfig.getString("access_key")
```

**Kotlin + KTX**
```kotlin
val isEnabled = remoteConfig["is_enabled"].asBoolean()

val fileBytes = remoteConfig["file_bytes"].asByteArray()

val audioVolume = remoteConfig["audio_volume"].asDouble()

val maxCharacters = remoteConfig["max_characters"].asLong()

val accessKey = remoteConfig["access_key"].asString()
```

### Set Remote Config Settings

**Kotlin**
```kotlin
val configSettings = FirebaseRemoteConfigSettings.Builder()
        .setMinimumFetchIntervalInSeconds(3600)
        .setFetchTimeoutInSeconds(60)
        .build()
remoteConfig.setConfigSettingsAsync(configSettings)
```

**Kotlin + KTX**
```kotlin
val configSettings = remoteConfigSettings {
    minimumFetchIntervalInSeconds = 3600
    fetchTimeoutInSeconds = 60
}
remoteConfig.setConfigSettingsAsync(configSettings)
```