To build run `./gradlew :publishToMavenLocal`

To integrate: add the following to your app's gradle file:

```kotlin
plugins {
    id("com.google.devtools.ksp")
}
dependencies {
    implementation("com.google.firebase:firebase-ai:<latest_version>")
    ksp("com.google.firebase:firebase-ai-processor:1.0.0")
}
```

