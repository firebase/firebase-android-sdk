# Realtime Database Kotlin Extensions

## Getting Started

To use the Firebase Realtime Database Android SDK with Kotlin Extensions, add the following
to your app's `build.gradle` file:

```groovy
// See maven.google.com for the latest versions
// This library transitively includes the firebase-database library
implementation 'com.google.firebase:firebase-database-ktx:$VERSION'
```

## Features

### Get an instance of FirebaseDatabase

**Kotlin**
```kotlin
val database = FirebaseDatabase.getInstance()
val anotherDatabase = FirebaseDatabase.getInstance(FirebaseApp.getInstance("myApp"))
```

**Kotlin + KTX**
```kotlin
val database = Firebase.database
val anotherDatabase = Firebase.database(Firebase.app("myApp"))
```

### Get the FirebaseDatabase for the specified url

**Kotlin**
```kotlin
val database = FirebaseDatabase.getInstance(url)
```

**Kotlin + KTX**
```kotlin
val database = Firebase.database(url)
```


### Get the FirebaseDatabase of the given FirebaseApp and url

**Kotlin**
```kotlin
val database = FirebaseDatabase.getInstance(app, url)
```

**Kotlin + KTX**
```kotlin
val database = Firebase.database(app, url)
```

### Convert a DataSnapshot to a POJO

**Kotlin**
```kotlin
val snapshot: DataSnapshot = ...
val myObject = snapshot.getValue(MyClass::class.java)
```

**Kotlin + KTX**
```kotlin
val snapshot: DocumentSnapshot = ...
val myObject = snapshot.getValue<MyClass>()
```

### Convert a DataSnapshot to generic types such as List or Map

**Kotlin**
```kotlin
val snapshot: DataSnapshot = ...
val typeIndicator = object : GenericTypeIndicator<List<Message>>() {}
val messages: List<Message> = snapshot.getValue(typeIndicator)
```

**Kotlin + KTX**
```kotlin
val snapshot: DocumentSnapshot = ...
val messages: List<Message> = snapshot.getValue<List<@JvmSuppressWildcards Message>>()
```

### Convert a MutableData to a POJO in a Transaction

**Kotlin**
```kotlin
override fun doTransaction(mutableData: MutableData): Transaction.Result {
    val post = mutableData.getValue(Post::class.java)
    // ...
}
```

**Kotlin + KTX**
```kotlin
override fun doTransaction(mutableData: MutableData): Transaction.Result {
    val post = mutableData.getValue<Post>()
    // ...
}
```
