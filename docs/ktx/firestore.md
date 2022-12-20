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

### Get a document

**Kotlin**
```kotlin
firestore.collection("cities")
    .document("LON")
    .addSnapshotListener { document: DocumentSnapshot?, error:  ->
        if (error != null) { 
            // Handle error
            return@addSnapshotListener
        }
        if (document != null) {
            // Use document
        }
    }
```

**Kotlin + KTX**
```kotlin
firestore.collection("cities")
    .document("LON")
    .snapshots()
    .collect { document: DocumentSnapshot ->
        // Use document
    }
```

### Query documents

**Kotlin**
```kotlin
firestore.collection("cities")
    .whereEqualTo("capital", true)
    .addSnapshotListener { documents: QuerySnapshot?, error ->
        if (error != null) {
            // Handle error
            return@addSnapshotListener
        }
        if (documents != null) {
            for (document in documents) {
                // Use document
            }
        }
    }
```

**Kotlin + KTX**
```kotlin
firestore.collection("cities")
    .whereEqualTo("capital", true)
    .snapshots()
    .collect { documents: QuerySnapshot ->
        for (document in documents) {
            // Use document
        }
    }
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
