# In-App Messaging Display Kotlin Extensions

## Getting Started

To use the Firebase In-App Messaging Display Android SDK with Kotlin Extensions, add the following
to your app's `build.gradle` file:

```groovy
// See maven.google.com for the latest versions
// This library transitively includes the firebase-inappmessaging-display library
implementation 'com.google.firebase:firebase-inappmessaging-display-ktx:$VERSION'
```

## Features

### Get an instance of FirebaseInAppMessagingDisplay

**Kotlin**
```kotlin
val fiamUI = FirebaseInAppMessagingDisplay.getInstance()
```

**Kotlin + KTX**
```kotlin
val fiamUI = Firebase.inAppMessagingDisplay
```
