# Firebase Performance Monitoring Development Workflow
[Firebase Performance Monitoring](https://firebase.google.com/docs/perf-mon/get-started-android) is 
a free mobile app performance analytics service that helps you to gain insight into the performance 
characteristics of your app. 

For more information about app performance and many other cool mobile services, check out [Firebase](https://firebase.google.com/).

## Clone the repository

Follow the instruction in the [Root README](https://github.com/firebase/firebase-android-sdk#getting-started)
to clone the repository.

## Local Environment Setup

Add the following lines to your `~/.bash_profile`:

For Mac:
```
export ANDROID_HOME=~/Library/Android/sdk
export ANDROID_SDK_ROOT=~/Library/Android/sdk
```

For Linux:
```
export ANDROID_HOME=~/Android/Sdk
export ANDROID_SDK_ROOT=~/Android/Sdk
```

## Build Firebase Performance SDK

### Fetch master branch

```
firebase-android-sdk$ git fetch origin master:master
firebase-android-sdk$ git checkout master
```

### Build SDK

Build the SDK in `firebase-android-sdk` folder:

```
firebase-android-sdk$ ./gradlew :clean :firebase-perf:build --stacktrace
```

Please note that `:clean` and `--stacktrace` are optional. The build output will be generated in
 `firebase-android-sdk/firebase-perf/build/outputs/aar`.

## Run Unit Tests

### Running all the tests

```
firebase-android-sdk$ ./gradlew :clean :firebase-perf:test --stacktrace
```

### Running a specific test

```
firebase-android-sdk$ ./gradlew :clean :firebase-perf:testDebugUnitTest --tests "PackageName.ClassName.TestMethodName" --stacktrace
```

> `TestMethodName` is optional, if not provided, all tests under `PackageName.ClassName` will be run.
Similar mechanism applies to `ClassName` as well.

## Run Integration Tests

Follow the instructions [here](https://github.com/firebase/firebase-android-sdk/blob/master/README.md#integration-testing) 
for the initial one time setup.

### Running Integration Tests on Local Emulator

```
firebase-android-sdk$ ./gradlew :firebase-perf:connectedCheck
```

### Running Integration Tests on Firebase Test Lab (triggered locally)

```
firebase-android-sdk$ ./gradlew :firebase-perf:deviceCheck
```

## Integrate SDK with 3P App

### Creating a Release Candidate

```
firebase-android-sdk$ ./gradlew -PpublishMode=SNAPSHOT -PprojectsToPublish="firebase-perf" firebasePublish
```

### Publish project locally

The simplest way to publish a project and all its associated dependencies is to just publish all 
projects. The following command builds **SNAPSHOT** dependencies of all projects. All pom level 
dependencies within the published artifacts will also point to SNAPSHOT versions that are 
co-published.

```
firebase-android-sdk$ ./gradlew publishAllToLocal
```

Alternative, publish `firebase-perf` only:

```
firebase-android-sdk$ ./gradlew -PprojectsToPublish=":firebase-perf"  publishProjectsToMavenLocal
```

The location of published project `~/.m2/repository/com/google/firebase/`.

### Read SDK from mavenLocal()

Add **mavenLocal()** to your project-level `build.gradle` file

```
buildscript {
    repositories {
        mavenLocal()
        google()
        jcenter()
    }
   
    dependencies {
        .       .       .

        # Adds several features that are specific to building Android apps
        classpath 'com.android.tools.build:gradle:<agp-version>'

        # Required for loading the 'google-services.json' file
        classpath 'com.google.gms:google-services:<google-services-plugin-version>'

        # Specify the version of 'perf-plugin'
        classpath 'com.google.firebase:perf-plugin:<perf-plugin-version>'
    }
}

allprojects {
    repositories {
        mavenLocal()
        google()
        jcenter()
    }
}
```

### Set Snapshot SDK version

Add **implementation** dependency to your module-level `build.gradle` file

```
apply plugin: 'com.android.application'

# Applies the 'perf-plugin'
apply plugin: 'com.google.firebase.firebase-perf'

.       .       .

dependencies {
   implementation 'com.google.firebase:firebase-perf:x.y.z-SNAPSHOT'
}
```

where the version number can be found in `~/.m2/repository/com/google/firebase/firebase-perf/maven-metadata-local.xml`.
See [Reference](https://github.com/firebase/firebase-android-sdk#commands) for detail.

## Check Code Coverage

Generate code coverage:

```
firebase-android-sdk$ ./gradlew firebase-perf:checkCoverage --stacktrace
```

Open the report in:

```
firebase-android-sdk$ open firebase-perf/build/reports/jacoco/firebase-perf/html/com.google.firebase.perf/index.html
```

## Releasing Firebase Performance SDK

Checkout internal release guidelines at go/fireperf-android.
