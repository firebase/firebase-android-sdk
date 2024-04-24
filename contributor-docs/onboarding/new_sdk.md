---
parent: Onboarding
---

# Creating a new Firebase SDK
{: .no_toc}

1. TOC
{:toc}

Want to create a new SDK in
[firebase/firebase-android-sdk](https://github.com/firebase/firebase-android-sdk)?
Read on.

{:toc}

## Repository layout and Gradle

[firebase/firebase-android-sdk](https://github.com/firebase/firebase-android-sdk)
uses a multi-project Gradle build to organize the different libraries it hosts.
As a consequence, each project/product within this repo is hosted under its own
subdirectory with its respective build file(s).

```bash
firebase-android-sdk
├── buildSrc
├── appcheck
│   └── firebase-appcheck
│   └── firebase-appcheck-playintegrity
├── firebase-annotations
├── firebase-common
│   └── firebase-common.gradle # note the name of the build file
│   └── ktx
│      └── ktx.gradle.kts # it can also be kts
└── build.gradle # root project build file.
```

Most commonly, SDKs are located as immediate child directories of the root
directory, with the directory name being the exact name of the Maven artifact ID
the library will have once released. e.g. `firebase-common` directory
hosts code for the `com.google.firebase:firebase-common` SDK.

{: .warning }
Note that the build file name for any given SDK is not `build.gradle` or `build.gradle.kts`
but rather mirrors the name of the sdk, e.g.
`firebase-common/firebase-common.gradle` or `firebase-common/firebase-common.gradle.kts`.

All of the core Gradle build logic lives in `buildSrc` and is used by all
SDKs.

SDKs can be grouped together for convenience by placing them in a directory of
choice.

## Creating an SDK

Let's say you want to create an SDK named `firebase-foo`

1. Create a directory called `firebase-foo`.
1. Create a file `firebase-foo/firebase-foo.gradle.kts`.
1. Add `firebase-foo` line to `subprojects.cfg` at the root of the tree.

### Update `firebase-foo.gradle.kts` with the following content

<details open markdown="block">
  <summary>
    firebase-foo.gradle.kts
  </summary>
```kotlin
plugins {
  id("firebase-library")
  // Uncomment for Kotlin
  // id("kotlin-android")
}

firebaseLibrary {
    // enable this only if you have tests in `androidTest`.
    testLab.enabled = true
    publishSources = true
    publishJavadoc = true
}

android {
  val targetSdkVersion : Int by rootProject
  val minSdkVersion : Int by rootProject

  compileSdk = targetSdkVersion
  defaultConfig {
    namespace = "com.google.firebase.foo"
    // change this if you have custom needs.
    minSdk = minSdkVersion
    targetSdk = targetSdkVersion
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
  implementation("com.google.firebase:firebase-common:21.0.0")
  implementation("com.google.firebase:firebase-components:18.0.0")
}

```
</details>

### Create `src/main/AndroidManifest.xml` with the following content:

<details open markdown="block">
  <summary>
    src/main/AndroidManifest.xml
  </summary>
```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Copyright {{ 'now' | date: "%Y" }} Google LLC -->
<!-- -->
<!-- Licensed under the Apache License, Version 2.0 (the "License"); -->
<!-- you may not use this file except in compliance with the License. -->
<!-- You may obtain a copy of the License at -->
<!-- -->
<!--      http://www.apache.org/licenses/LICENSE-2.0 -->
<!-- -->
<!-- Unless required by applicable law or agreed to in writing, software -->
<!-- distributed under the License is distributed on an "AS IS" BASIS, -->
<!-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. -->
<!-- See the License for the specific language governing permissions and -->
<!-- limitations under the License. -->

<manifest xmlns:android="http://schemas.android.com/apk/res/android">
  <application>
      <service android:name="com.google.firebase.components.ComponentDiscoveryService"
          android:exported="false">
          <meta-data
              android:name="com.google.firebase.components:com.google.firebase.foo.FirebaseFooRegistrar"
              android:value="com.google.firebase.components.ComponentRegistrar" />
      </service>
  </application>
</manifest>
```

</details>

### Create `com.google.firebase.foo.FirebaseFoo`

For Kotlin
<details open markdown="block">
  <summary>
    src/main/kotlin/com/google/firebase/foo/FirebaseFoo.kt
  </summary>

```kotlin
class FirebaseFoo {
  companion object {
    @JvmStatic
    val instance: FirebaseFoo
      get() = getInstance(Firebase.app)

    @JvmStatic fun getInstance(app: FirebaseApp): FirebaseFoo = app.get(FirebaseFoo::class.java)
  }
}
```

</details>

For Java
<details markdown="block">
  <summary>
    src/main/java/com/google/firebase/foo/FirebaseFoo.java
  </summary>

```java
public class FirebaseFoo {
  public static FirebaseFoo getInstance() {
    return getInstance(FirebaseApp.getInstance());
  }
  public static FirebaseFoo getInstance(FirebaseApp app) {
    return app.get(FirebaseFoo.class);
  }
}
```

</details>

### Create `com.google.firebase.foo.FirebaseFooRegistrar`

For Kotlin
<details open markdown="block">
  <summary>
    src/main/kotlin/com/google/firebase/foo/FirebaseFooRegistrar.kt
  </summary>

{: .warning }
You should strongly consider using [Dependency Injection]({{ site.baseurl }}{% link best_practices/dependency_injection.md %})
to instantiate your sdk instead of manually constructing its instance in the `factory()` below.

```kotlin
class FirebaseFooRegistrar : ComponentRegistrar {
  override fun getComponents() =
    listOf(
      Component.builder(FirebaseFoo::class.java).factory { container -> FirebaseFoo() }.build(),
      LibraryVersionComponent.create("fire-foo", BuildConfig.VERSION_NAME)
    )
}
```

</details>

For Java
<details markdown="block">
  <summary>
    src/main/java/com/google/firebase/foo/FirebaseFooRegistrar.java
  </summary>

```java
public class FirebaseFooRegistrar implements ComponentRegistrar {
  @Override
  public List<Component<?>> getComponents() {
    return Arrays.asList(
        Component.builder(FirebaseFoo.class).factory(c -> new FirebaseFoo()).build(),
        LibraryVersionComponent.create("fire-foo", BuildConfig.VERSION_NAME));

  }
}
```

</details>

Continue to [How Firebase works]({{ site.baseurl }}{% link how_firebase_works.md %}).
