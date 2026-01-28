# Firebase Performance Monitoring Gradle plugin (aka `perf-plugin`)

Refer to [GMaven](https://maven.google.com/web/index.html?q=perf-plug#com.google.firebase:perf-plugin) for the release artifacts and [Firebase Android Release Notes](https://firebase.google.com/support/release-notes/android) for the release info.

## Example Changelog

```
## vX.Y.Z

* {{feature}} You can now do this cool new thing. For more details, refer to the
  [documentation](docs/path).

* {{fixed}} Fixed the `methodFoo` warning.

* {{changed}} You need to do _this_ instead of _that_.
```

### v2.0.2

* {{fixed}} Fixed compatibility with AGP 9.0.0. [#7293]

### v2.0.1

* {{fixed}} Fixed an incompatibility between Firebase Performance and Gradle isolated projects.
  ([Github Issue #6748](//github.com/firebase/firebase-android-sdk/issues/6748){: .external})

## v2.0.0

* {{changed}} **Breaking change**: This release increases the minimum required versions of Gradle to `7.3.3` and AGP to `7.0.0`.
* {{fixed}} Replaced usage of deprecated GradleVersion APIs to fix (#7092).

## v1.4.2

* {{fixed}} Upgraded the class bytecode instrumentation APIs to be compatible with [AGP 7.2](https://developer.android.com/studio/releases/gradle-plugin-api-updates#agp-7-2-0).

## v1.4.1

* {{fixed}} Migrated away from the deprecated Android Gradle plugin APIs.
* {{fixed}} Filtered out classes in META-INF/ directory from instrumentation.
  ([GitHub Issue #3155](//github.com/firebase/firebase-android-sdk/issues/3155 {:.external})

## v1.4.0

* {{feature}} Enabled parallel transform by migrating to the new ASM classes transform API.

## v1.3.5

* {{feature}} Added support for [Configuration Cache](https://docs.gradle.org/current/userguide/configuration_cache.html).

## v1.3.4

* {{changed}} Added Firebase Datatransport to event instrumentation denylist.
  This is to prevent infinite event generation because Firebase Performance
  SDK has integrated with Firebase Datatransport for performance log dispatch
  mechanism.

## v1.3.3

* {{changed}} Plugin now differentiates processing outputs based on `Jar` and
  `Directory` format. Since dex output for jars are cached by gradle this should
  help improve the overall build time during incremental runs.

* {{changed}} Updated the incremental processing logic for the Directory inputs
  to only process changed files. This should improve the transformation runtime
  during incremental runs.

* {{fixed}} Fixed the `DexMergerException` build-time failure (during
  *dex merging*) by correctly handling the changed files during incremental
  processing.

  ([IssueTracker #164332661](https://issuetracker.google.com/issues/164332661)
  and [IssueTracker #162430661](https://issuetracker.google.com/issues/162430661))

* {{fixed}} Fixed the `ClassNotFoundException` run-time failure (when the app is
  build on non-case preserving file system) by updating the transformation logic
  to process Jar inputs to Jar outputs instead of extracting Jars to Directories
  (which was overwriting files with similar names but casing difference).

  ([IssueTracker #132840182](https://issuetracker.google.com/issues/132840182),
  [IssueTracker #135171107](https://issuetracker.google.com/issues/135171107),
  [GitHub Issue Stripe #1139](https://github.com/stripe/stripe-android/issues/1139),
  [GitHub Issue Stripe #1141](https://github.com/stripe/stripe-android/issues/1141),
  [GitHub Issue Stripe #1476](https://github.com/firebase/firebase-android-sdk/issues/1476))

* {{fixed}} Fixed the transformed output file names to generate readable names
  (by using the [getName()](https://google.github.io/android-gradle-dsl/javadoc/1.5/com/android/build/api/transform/QualifiedContent.html#getName--)
  API provided by the Transform API instead of MD5 hashing the input file path)
  so that they are helpful to later transforms.

  ([GitHub Issue](https://github.com/minsko/FirebasePerformanceTransformIssue))

## v1.3.2

* {{fixed}} Fixed an `ArrayIndexOutOfBoundsException` when processing Kotlin class files that use both [Inline Functions](https://kotlinlang.org/docs/reference/inline-functions.html#inline-functions) and [Multiplatform Projects](https://kotlinlang.org/docs/reference/whatsnew12.html#multiplatform-projects-experimental) by upgrading to the latest ASM API which contains the [fix](https://gitlab.ow2.org/asm/asm/-/merge_requests/290).
  ([GitHub Issue #1556](https://github.com/firebase/firebase-android-sdk/issues/1556))

* {{changed}} Upgraded [`asm`](https://search.maven.org/artifact/org.ow2.asm/asm) POM dependency from v7.0 to v9.0 and added a new POM dependency on [`asm-commons`](https://search.maven.org/artifact/org.ow2.asm/asm-commons) v9.0.

## v1.3.1

* {{fixed}} This release contains some minor fixes and improvements.

## v1.3.0

* {{changed}} With this release, you can disable the Firebase Performance Monitoring Gradle plugin for a specific [build variant](https://developer.android.com/studio/build/build-variants) (including [buildTypes](https://developer.android.com/studio/build/build-variants#build-types) or [productFlavors](https://developer.android.com/studio/build/build-variants#product-flavors)). For more details, refer to the [disabling Performance Monitoring documentation](https://firebase.google.com/docs/perf-mon/disable-sdk?platform=android#disable-gradle-plugin).

## v1.2.1

* {{changed}} With this release, you must [add `perf-plugin` explicitly](https://firebase.google.com/docs/perf-mon/get-started-android) rather than adding it via `firebase-plugins` (which is now deprecated).

## v1.2.0

* {{changed}} Updates to the Firebase Gradle Plugins (`firebase-plugins` and `perf-plugin`) for Android Studio 3.x are now available.
  This release:
  - Provides support for `JDK 11` with ASM API upgrade to "7.0".
  - Fixes an issue with build failure when the transform is applied but disabled.
  - Removes the requirement that the Firebase Performance Monitoring plugin must be listed after the Android application plugin for it to work.
  - Improves the build performance by disabling the instrumentation and making it non-operational when the project property flag `firebasePerformanceInstrumentationEnabled = false` is specified in the `gradle.properties` file.

## v1.1.2

