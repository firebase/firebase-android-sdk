# Firebase Common Kotlin Extensions

## Getting Started

To use the Firebase Common Android SDK with Kotlin Extensions, add the following
to your app's `build.gradle` file:

```groovy
// See maven.google.com for the latest versions
// This library transitively includes the firebase-common library
implementation 'com.google.firebase:firebase-common-ktx:$VERSION'
```

## Features

### Get the default FirebaseApp and FirebaseOptions

**Kotlin**
```kotlin
val defaultApp = FirebaseApp.getInstance()
val defaultOptions = defaultApp.options
```

**Kotlin + KTX**
```kotlin
val defaultApp = Firebase.app
val defaultOptions = Firebase.options
```

### Initialize a FirebaseApp

**Kotlin**
```kotlin
val options = FirebaseApp.getInstance().options
val anotherApp = FirebaseApp.initializeApp(context, options, "myApp")
```

**Kotlin + KTX**
```kotlin
var anotherApp = Firebase.initialize(context, Firebase.options, "myApp")
```

