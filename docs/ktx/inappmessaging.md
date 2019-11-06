# In-App Messaging Kotlin Extensions

## Getting Started

To use the Firebase In-App Messaging Android SDK with Kotlin Extensions, add the following
to your app's `build.gradle` file:

```groovy
// See maven.google.com for the latest versions
// This library transitively includes the firebase-inappmessaging library
implementation 'com.google.firebase:firebase-inappmessaging-ktx:$VERSION'
```

## Features

### Get an instance of FirebaseInAppMessaging

**Kotlin**
```kotlin
val fiamUI = FirebaseInAppMessaging.getInstance()
```

**Kotlin + KTX**
```kotlin
val fiamUI = Firebase.inAppMessaging
```
