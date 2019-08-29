# Cloud Functions Kotlin Extensions

## Getting Started

To use the Cloud Functions Android SDK with Kotlin Extensions, add the following
to your app's `build.gradle` file:

```groovy
// See maven.google.com for the latest versions
// This library transitively includes the firebase-functions library
implementation 'com.google.firebase:firebase-functions-ktx:$VERSION'
```

## Features

### Get the FirebaseFunctions instance of the default app

**Kotlin**
```kotlin
val functions = FirebaseFunctions.getInstance()
```

**Kotlin + KTX**
```kotlin
val functions = Firebase.functions
```

### Get the FirebaseFunctions of a given region

**Kotlin**
```kotlin
val functions = FirebaseFunctions.getInstance(region)
```

**Kotlin + KTX**
```kotlin
val functions = Firebase.functions(region)
```

### Get the FirebaseFunctions of a given FirebaseApp

**Kotlin**
```kotlin
val functions = FirebaseFunctions.getInstance(app)
```

**Kotlin + KTX**
```kotlin
val functions = Firebase.functions(app)
```

### Get the FirebaseFunctions of a given region and FirebaseApp

**Kotlin**
```kotlin
val functions = FirebaseFunctions.getInstance(app, region)
```

**Kotlin + KTX**
```kotlin
val functions = Firebase.functions(app, region)
```
