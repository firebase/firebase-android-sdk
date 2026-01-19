### Crashlytics Gradle plugin version 3.0.6

* [fixed] Fixed an incompatibility between Crashlytics and Gradle isolated projects.
  ([Github Issue #6748](//github.com/firebase/firebase-android-sdk/issues/6748){: .external})

### Crashlytics Gradle plugin version 3.0.5

* [added] Added a task to inject version control info as a string resource, enabling the SDK to
  consume it more efficiently.

### Crashlytics Gradle plugin version 3.0.4

* [changed] Enhance task performance and correctness by explicitly defining path sensitivity and
  caching

### Crashlytics Gradle plugin version 3.0.2

* [fixed] Fixed issue in generateCrashlyticsSymbolFile task preventing config caching.
* [fixed] Fixed compatibility issue with Intel-based Macs.

### Crashlytics Gradle plugin version 3.0.1

* [fixed] Fixed an incompatibility between Crashlytics and viewBinder.
  ([Github Issue #5925](//github.com/firebase/firebase-android-sdk/issues/5925){: .external})

### Crashlytics Gradle plugin version 3.0.0

Warning: The latest release of the Crashlytics Gradle plugin is a major version (v3.0.0) and
modernizes the SDK by dropping support for older versions of Gradle and the Android Gradle plugin.
Additionally, the changes in this release resolve issues with AGP v8.1+ and improve support for
native apps and customized builds. This release includes the following API changes:

* {{removed}} Removed old deprecated fields `mappingFile` and `strippedNativeLibsDir`.
* {{changed}} **Breaking change**: The closure field `symbolGenerator` has been replaced with two
  new fields: `symbolGeneratorType` and `breakpadBinary`.
* {{changed}} The `unstrippedNativeLibsDir` field is now cumulative. For more information,
  see [Upgrade to Crashlytics Gradle plugin v3](https://firebase.google.com/docs/crashlytics/upgrade-to-crashlytics-gradle-plugin-v3).
* {{changed}} **Breaking change**: This release increases the minimum required versions to use
  Crashlytics:
  * Gradle 8
  * Android Gradle plugin 8.1
  * Google-Services plugin 4.4.1

