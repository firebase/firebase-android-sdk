# Firestore Kotlin Extensions

## Getting Started

To use the Cloud Firestore Android SDK with Kotlin Extensions, add the following
to your app's `build.gradle` file:

```groovy
// See maven.google.com for the latest versions
// This library transitively includes the firebase-firestore library
implementation 'com.google.firebase:firebase-firestore-ktx:$VERSION'
```

## Features

### Get an instance of FirebaseFirestore

**Kotlin**
```kotlin
val firestore = FirebaseFirestore.getInstance()
val anotherFirestore = FirebaseFirestore.getInstance(FirebaseApp.getInstance("myApp"))
```

**Kotlin + KTX**
```kotlin
val firestore = Firebase.firestore
val anotherFirestore = Firebase.firestore(Firebase.app("myApp"))
```

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

### Setup Firestore with a local emulator

**Kotlin**
```kotlin
val settings = FirebaseFirestoreSettings.Builder()
                   .setHost("10.0.2.2:8080")
                   .setSslEnabled(false)
                   .setPersistenceEnabled(false)
                   .build()

firestore.setFirestoreSettings(settings)
```

**Kotlin + KTX**
```kotlin
firestore.firestoreSettings = firestoreSettings {
    host = "http://10.0.2.2:8080"
    isSslEnabled = false
    isPersistenceEnabled = false
}
```
