To build run `./gradlew :publishToMavenLocal`

To integrate: add the following to your app's gradle file:

```declarative
plugins {
    id("com.google.devtools.ksp")
}
dependencies {
    implementation("com.google.firebase:firebase-ai-ksp-processor:1.0.0")
    ksp("com.google.firebase:firebase-ai-ksp-processor:1.0.0")
}
```
