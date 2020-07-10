# Crashlytics Kotlin Extensions

## Getting Started

To use the Firebase Crashlytics Android SDK with Kotlin Extensions, add the following
to your app's `build.gradle` file:

```groovy
// See maven.google.com for the latest versions
// This library transitively includes the firebase-crashlytics library
implementation 'com.google.firebase:firebase-crashlytics-ktx:$VERSION'
```

## Features

### Get an instance of FirebaseCrashlytics

**Kotlin**
```kotlin
val crashlytics = FirebaseCrashlytics.getInstance()
```

**Kotlin + KTX**
```kotlin
val crashlytics = Firebase.crashlytics
```

### Set custom keys

**Kotlin**
```kotlin
crashlytics.setCustomKey("str_key", "hello")
crashlytics.setCustomKey("bool_key", true)
crashlytics.setCustomKey("int_key", 1)
crashlytics.setCustomKey("long_key", 1L)
crashlytics.setCustomKey("float_key", 1.0f)
crashlytics.setCustomKey("double_key", 1.0)
```

**Kotlin + KTX**
```kotlin
crashlytics.setCustomKeys {
    key("str_key", "hello")
    key("bool_key", true)
    key("int_key", 1)
    key("long_key", 1L)
    key("float_key", 1.0f)
    key("double_key", 1.0)
}
```
