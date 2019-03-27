# Firestore Kotlin Extensions

## Getting Started

To use the Cloud Firestore Android SDK with Kotlin Extenstions, add the following
to your app's `build.gradle` file:

```groovy
// This library transitively includes the Firestore Android SDK
implementation 'com.google.firebase:firebase-firestore-ktx:18.1.0
```

## Features

### Convert a DocumentSnapshot field to a POJO

**Kotlin**
```kotlin
val snapshot: DocumentSnapshot = ...
val myObject = snapshot.get("fieldPath", MyClass::class.java)
```

**Kotlin + KTX**
```kotlin
val snapshot: DocumentSnapshot = ...
val myObject = snapshot.get<MyClass>("fieldPath")
```

### Get an instance of FirebaseFirestore

**Kotlin**
```kotlin
val firestore = FirebaseFirestore.getInstance()
```

**Kotlin + KTX**
```kotlin
val firestore = Firebase.firestore
```

### Convert a DocumentSnapshot to a POJO

**Kotlin**
```kotlin
val snapshot: DocumentSnapshot = ...
val myObject = snapshot.toObject(MyClass::class.java)
```

**Kotlin + KTX**
```kotlin
val snapshot: DocumentSnapshot = ...
val myObject = snapshot.toObject<MyClass>()
```

### Convert a QuerySnapshot to a list of POJOs

**Kotlin**
```kotlin
val snapshot: QuerySnapshot = ...
val objectList = snapshot.toObjects(MyClass::class.java)
```

**Kotlin + KTX**
```kotlin
val snapshot: QuerySnapshot = ...
val objectList = snapshot.toObjects<MyClass>()
```