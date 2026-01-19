# [Firebase Performance](https://firebase.google.com/docs/perf-mon/get-started-android) Gradle Plugin
  
This directory includes the [Firebase Performance Gradle Plugin](https://firebase.google.com/docs/perf-mon/get-started-android#add-perfmon-plugin) 
that enables instrumentation on developer's app which provides the capability for 
`@AddTrace annotation processing` and `automatic HTTP/S network request monitoring`.

## Building the plugin

```
firebase-android-buildtools$ ./gradlew :firebase-perf-gradle:build
```

The build command eventually runs the tests as well. Outputs will get generated into 
`perf-plugin/build` folder.


## Testing the plugin

Below command runs both the Unit and Functional Tests:

```
firebase-android-buildtools$ ./gradlew :firebase-perf-gradle:test
```

## Build without running the tests

`perf-plugin` testing takes ~10-12 minutes because of [Functional Tests](./perf-plugin/src/test/java/com/google/firebase/perf/plugin/FirebasePerfTransformTest.java). 
Building the plugin without executing the tests each time helps speed up the development.

```
firebase-android-buildtools$ ./gradlew :firebase-perf-gradle:build -x test
```

## Publishing & Releasing

### Publish local Maven artifact

```
firebase-android-buildtools$ ./gradlew :firebase-perf-gradle:publishToMavenLocal
```

By default the released version will be published. To build a `SNAPSHOT` version for development 
purposes append `-PpublishMode=SNAPSHOT` to the above command.

The produced local Maven artifact is `com.google.firebase:perf-plugin:<pluginVersion>` 
(or `<pluginVersion>-SNAPSHOT`) and is placed in the `~/.m2/repository/com/google/firebase/perf-plugin/` 
directory with the version number specified in the [project.properties](./perf-plugin/src/main/resources/com/google/firebase/perf/plugin/project.properties).

### Integrate the published artifacts with a test app

1. In your test app's `buildSrc/build.gradle` (or [root-level (project-level) gradle](https://developer.android.com/studio/build#top-level) 
if your test app doesn't have [buildSrc](https://docs.gradle.org/current/userguide/organizing_gradle_projects.html#sec:build_sources) 
directory):

    1.1 Add `mavenLocal()` to the `repositories` block

    ```
        repositories {
            mavenLocal()

            google()
            mavenCentral()
        }
    ```

    1.2 Add `perf-plugin` dependency to the `dependencies` block

    ```
        dependencies {
            implementation 'com.google.firebase:perf-plugin:<pluginVersion>'
        }

    ```

    > **Note:** The `implementation` dependency can be changed to `classpath` dependency depending on your gradle file.

2. In your test app's [module-level (app-level)](https://developer.android.com/studio/build#module-level) 
build file:
 
    2.1 Add `apply plugin: 'com.google.firebase.firebase-perf`

    ```
    apply plugin: 'com.android.application'
    apply plugin: 'com.google.gms.google-services'
    
    // Apply the Performance Monitoring plugin
    apply plugin: 'com.google.firebase.firebase-perf'
    
    android {
      // ...
    }
    ```
    
### Enable debug logging

Build your app with the `-Dorg.gradle.debug` flag set to `true`:

```
./gradlew -Dorg.gradle.debug=true :app:assembleDebug
```
