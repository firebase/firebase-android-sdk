# Storage Kotlin Extensions

## Getting Started

To use the Cloud Storage Android SDK with Kotlin Extensions, add the following
to your app's `build.gradle` file:

```groovy
// See maven.google.com for the latest versions
// This library transitively includes the firebase-storage library
implementation 'com.google.firebase:firebase-storage-ktx:$VERSION'
```

## Features

### Get an instance of FirebaseStorage

**Kotlin**
```kotlin
val storage = FirebaseStorage.getInstance()
val anotherStorage = FirebaseStorage.getInstance(FirebaseApp.getInstance("myApp"))
```

**Kotlin + KTX**
```kotlin
val storage = Firebase.storage
val anotherStorage = Firebase.storage(Firebase.app("myApp"))
```

### Get the FirebaseStorage for a custom storage bucket url

**Kotlin**
```kotlin
val storage = FirebaseStorage.getInstance("gs://my-custom-bucket")
val anotherStorage = FirebaseStorage.getInstance(FirebaseApp.getInstance("myApp"), "gs://my-custom-bucket")
```

**Kotlin + KTX**
```kotlin
val storage = Firebase.storage("gs://my-custom-bucket")
val anotherStorage = Firebase.storage(Firebase.app("myApp"), "gs://my-custom-bucket")
```

### Create file metadata

**Kotlin**
```kotlin
val metadata = StorageMetadata.Builder()
        .setContentType("image/jpg")
        .setContentDisposition("attachment")
        .setCustomMetadata("location", "Maputo, MOZ")
        .build()
```

**Kotlin + KTX**
```kotlin
val metadata = storageMetadata {
    contentType = "image/jpg"
    contentDisposition = "attachment"
    setCustomMetadata("location", "Maputo, MOZ")
}
```

### Upload files with metadata

**Kotlin**
```kotlin
var metadata = StorageMetadata.Builder()
        .setContentType("image/jpg")
        .setContentDisposition("attachment")
        .build()

// Works with putFile(), putStream() or putBytes()
storageRef.child("images/mountains.jpg").putFile(file, metadata)
```

**Kotlin + KTX**
```kotlin
// Works with putFile(), putStream() or putBytes()
storageRef.child("images/mountains.jpg").putFile(file) {
    contentType = "image/jpg"
    contentDisposition = "attachment"
}
```

### Update file metadata

**Kotlin**
```kotlin
var metadata = StorageMetadata.Builder()
        .setContentType("image/jpg")
        .setCustomMetadata("location", "Harare, ZIM")
        .build()

storageRef.child("images/mountains.jpg").updateMetadata(metadata)
```

**Kotlin + KTX**
```kotlin
storageRef.child("images/mountains.jpg").updateMetadata {
    contentType = "image/jpg"
    contentDisposition = "attachment"
    setCustomMetadata("location", "Harare, ZIM")
}
```