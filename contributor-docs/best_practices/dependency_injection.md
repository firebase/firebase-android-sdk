---
parent: Best Practices
---

# Dependency Injection

While [Firebase Components]({{ site.baseurl }}{% components/components.md %}) provides basic
Dependency Injection capabilities for interop between Firebase SDKs, it's not ideal as a general purpose
DI framework or a couple of reasons, to name a few:

* It's verbose, i.e. requires manually specifying dependencies and constructing instances of components in Component
  definitions.
* It has a runtime cost, i.e. initialization time is linear in the number of Components present in the graph

As a result using [Firebase Components]({{ site.baseurl }}{% components/components.md %}) is appropriate only
for inter-SDK injection and scoping instances per `FirebaseApp`.

On the other hand, manually instantiating SDKs is often tedious, errorprone, and often leads to code smells.
So it's recommended to use [Dagger](https://dagger.dev) for internal dependency injection within the SDKs,
while using components.

TODO: Provide an example where passing a dependency from Components all the way to the class in the sdk that needs it is tedious.

## How to get started

Since [Dagger] does not strictly follow semver and requires the dagger-compiler version to match with the dagger library version
it's not safe to depend on it via a pom level dependency, see [This comment](https://github.com/firebase/firebase-android-sdk/issues/1677#issuecomment-645669608) for context. For this reason in Firebase SDKs we "vendor/repackage" Dagger into the SDK itself under
`com.google.firebase.{sdkname}.dagger`, while it incurs a size increase, it's usually on the order of a couple of KB and is considered
negligible.

To use Dagger in your SDK use the following in your Gradle build file:

```groovy
plugins {
    id("firebase-vendor")
}

dependencies {
    implementation(libs.javax.inject)
    vendor(libs.dagger) {
        exclude group: "javax.inject", module: "javax.inject"
    }
    annotationProcessor(libs.dagger.compiler)
}
```

## General Dagger setup

TODO
