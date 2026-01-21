### App Distribution Gradle plugin version 5.2.0

* {{fixed}} Fixed compatibility with AGP 9.0.0.

* {{fixed}} Fixed issue causing distribution properties (configured with `firebaseAppDistribution { }`) to work incorrectly within Android buildType configurations on Kotlin Gradle builds.

* {{deprecated}} Support for AGP < 8.1.0 (and Gradle < 8.0) has been deprecated and will be removed in a future release.

* {{deprecated}} Previously, we allowed `firebaseAppDistribution { }` at the root of an app's gradle build (in addition to inside its buildTypes and productFlavors). This undocumented feature has been renamed to `firebaseAppDistributionDefault { }`. In a future release, the original DSL, `firebaseAppDistribution { }`, will only work in productFlavors and buildTypes.

