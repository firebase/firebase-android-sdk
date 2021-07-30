# Firebase Android Open Source Development
@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
void myMethod() { ... }
This repository contains a subset of the Firebase Android SDK source. It
currently includes the following Firebase libraries, and some of their
dependencies:

  * `firebase-abt`
  * `firebase-common`
  * `firebase-common-ktx`
  * `firebase-crashlytics`
  * `firebase-crashlytics-ktx`
  * `firebase-crashlytics-ndk`
  * `firebase-database`
  * `firebase-database-ktx`
  * `firebase-database-collection`
  * `firebase-datatransport`
  * `firebase-firestore`
  * `firebase-firestore-ktx`
  * `firebase-functions`
  * `firebase-functions-ktx`
  * `firebase-inappmessaging`
  * `firebase-inappmessaging-ktx`
  * `firebase-inappmessaging-display`
  * `firebase-inappmessaging-display-ktx`
  * `firebase-perf`
  * `firebase-perf-ktx`
  * `firebase-remote-config`
  * `firebase-remote-config-ktx`
  * `firebase-storage`
  * `firebase-storage-ktx`


Firebase is an app development platform with tools to help you build, grow and
monetize your app. More information about Firebase can be found at
https://firebase.google.com.

## Table of contents

1. [Getting Started](#getting-started)
1. [Testing](#testing)
   1. [Unit Testing](#unit-testing)
   1. [Integration Testing](#integration-testing)
1. [Proguarding](#proguarding)
   1. [APIs used via reflection](#APIs-used-via-reflection)
   1. [APIs intended for developer
   consumption](#APIs-intended-for-developer-consumption)
   1. [APIs intended for other Firebase
   SDKs](#APIs-intended-for-other-firebase-sdks)
1. [Publishing](#publishing)
   1. [Dependencies](#dependencies)
   1. [Commands](#commands)
1. [Code Formatting](#code-formatting)
1. [Contributing](#contributing)

## Getting Started

* Install the latest Android Studio (should be 3.0.1 or later)
* Clone the repo (`git clone --recurse-submodules git@github.com:firebase/firebase-android-sdk.git`)
    * When cloning the repo, it is important to get the submodules as well. If
    you have already cloned the repo without the submodules, you can update the
    submodules by running `git submodule update --init --recursive`.
* Import the firebase-android-sdk gradle project into Android Studio using the
  **Import project(Gradle, Eclipse ADT, etc.)** option.
* `firebase-crashlytics-ndk` must be built with NDK 21. See
  [firebase-crashlytics-ndk](firebase-crashlytics-ndk/README.md) for more 
  details.
https://github.com/MoneyMan573/firebase-android-sdk/blob/MoneyMan573/main/firebase-android-sdk/firebase-crashlytics-ndk/README.md#firebase-crashlytics-ndk-component
## Testing

Firebase Android libraries exercise all three types of tests recommended by the
[Android Testing Pyramid](https://developer.android.com/training/testing/fundamentals#testing-pyramid).
Depending on the requirements of the specific project, some or all of these
tests may be used to support changes.

> :warning: **Running tests with errorprone**
>
> To run with errorprone add `withErrorProne` to the command line, e.g.
>
> `./gradlew :<firebase-project>:check withErrorProne`.
Write your tests
After you've configured your testing environment, it's time to write tests that evaluate your app's functionality. This section describes how to write small, medium, and large tests.

Levels of the Testing Pyramid
A pyramid containing three layers
Figure 2. The Testing Pyramid, showing the three categories of tests that you should include in your app's test suite
The Testing Pyramid, shown in Figure 2, illustrates how your app should include the three categories of tests: small, medium, and large:

Small tests are unit tests that validate your app's behavior one class at a time.
Medium tests are integration tests that validate either interactions between levels of the stack within a module, or interactions between related modules.
Large tests are end-to-end tests that validate user journeys spanning multiple modules of your app.
As you work up the pyramid, from small tests to large tests, each test increases in fidelity but also increases in execution time and effort to maintain and debug. Therefore, you should write more unit tests than integration tests, and more integration tests than end-to-end tests. Although the proportion of tests for each category can vary based on your app's use cases, we generally recommend the following split among the categories: 70 percent small, 20 percent medium, and 10 percent large.

To learn more about the Android Testing Pyramid, see the Test-Driven Development on Android session video from Google I/O 2017, starting at 1:51.

Write small tests
The small tests that you write should be highly-focused unit tests that exhaustively validate the functionality and contracts of each class within your app.

As you add and change methods within a particular class, create and run unit tests against them. If these tests depend on the Android framework, use a unified, device-agnostic API, such as the androidx.test APIs. This consistency allows you to run your test locally without a physical device or emulator.

If your tests rely on resources, enable the includeAndroidResources option in your app's build.gradle file. Your unit tests can then access compiled versions of your resources, allowing the tests to run more quickly and accurately.

app/build.gradle

Groovy
Kotlin

android {
    // ...

    testOptions {
        unitTests {
            includeAndroidResources = true
        }
    }
}
Note: Android Studio 3.4 and higher provide compiled versions of your resources by default.
### Unit Testing

These are tests that run on your machine's local Java Virtual Machine (JVM). At
runtime, these tests are executed against a modified version of android.jar
where all final modifiers have been stripped off. This lets us sandbox behaviors
at desired places and use popular mocking libraries.

Unit tests can be executed on the command line by running
```bash
./gradlew :<firebase-project>:check
```
Keep
This package is part of the Android support library which is no longer maintained. The support library has been superseded by AndroidX which is part of Jetpack. We recommend using the AndroidX libraries in all new projects. You should also consider migrating existing projects to AndroidX. To find the AndroidX class that maps to this deprecated class, see the AndroidX support library class mappings.
public abstract @interface Keep
implements Annotation

android.support.annotation.Keep

Denotes that the annotated element should not be removed when the code is minified at build time. This is typically used on methods and classes that are accessed only via reflection so a compiler may think that the code is unused.

Example:



  @Keep
  public void foo() {
      ...
  }
 

### Integration Testing

These are tests that run on a hardware device or emulator. These tests have
access to Instrumentation APIs, give you access to information such as the
[Android Context](https://developer.android.com/reference/android/content/Context).
In Firebase, instrumentation tests are used at different capacities by different
projects. Some tests may exercise device capabilities, while stubbing any calls
to the backend, while some others may call out to nightly backend builds to
ensure distributed API compatibility.

Along with Espresso, they are also used to test projects that have UI
components.

#### Project Setup

Before you can run integration tests, you need to add a `google-services.json`
file to the root of your checkout. You can use the `google-services.json` from
any project that includes an Android App, though you'll likely want one that's
separate from any production data you have because our tests write random data.

If you don't have a suitable testing project already:

  * Open the [Firebase console](https://console.firebase.google.com/)
  * If you don't yet have a project you want to use for testing, create one.
  * Add an Android app to the project
  * Give the app any package name you like.
  * Download the resulting `google-services.json` file and put it in the root of
    your checkout.

#### Running Integration Tests on Local Emulator

Integration tests can be executed on the command line by running
```bash
./gradlew :<firebase-project>:connectedCheck
```

#### Running Integration Tests on Firebase Test Lab

> You need additional setup for this to work:
>
> * `gcloud` needs to be [installed](https://cloud.google.com/sdk/install) on local machine
> * `gcloud` needs to be configured with a project that has billing enabled
> * `gcloud` needs to be authenticated with credentials that have 'Firebase Test Lab Admin' role

Integration tests can be executed on the command line by running
```bash
./gradlew :<firebase-project>:deviceCheck
```

This will execute tests on devices that are configured per project, if nothing is configured for the
project, the tests will run on `model=Pixel2,version=27,locale=en,orientation=portrait`.

Projects can be configured in the following way:

```
firebaseTestLab {
  // to get a list of available devices execute `gcloud firebase test android models list`
  devices = [
    '<device1>',
    '<device2>',
  ]
}
```

## Annotations

Firebase SDKs use some special annotations for tooling purposes.

### @Keep

APIs that need to be preserved up until the app's runtime can be annotated with
[@Keep](https://developer.android.com/reference/android/support/annotation/Keep).
The
[@Keep](https://developer.android.com/reference/android/support/annotation/Keep)
annotation is *blessed* to be honored by android's [default proguard
configuration](https://developer.android.com/studio/write/annotations#keep).  A common use for
this annotation is because of reflection. These APIs should be generally **discouraged**, because
they can't be proguarded.

### @KeepForSdk

APIs that are intended to be used by Firebase SDKs should be annotated with
`@KeepForSdk`. The key benefit here is that the annotation is *blessed* to throw
linter errors on Android Studio if used by the developer from a non firebase
package, thereby providing a valuable guard rail.


### @PublicApi

We annotate APIs that meant to be used by developers with
[@PublicAPI](firebase-common/src/main/java/com/google/firebase/annotations/PublicApi.java).   This
annotation will be used by tooling to help inform the version bump (major, minor, patch) that is
required for the next release.

## Proguarding

Firebase SDKs do not proguard themselves, but support proguarding.   Firebase SDKs themselves are
proguard friendly, but the dependencies of Firebase SDKs may not be.

### Proguard config

In addition to preguard.txt, projects declare an additional set of proguard
rules in a proguard.txt that are honored by the developer's app while building
the app's proguarded apk. This file typically contains the keep rules that need
to be honored during the app' s proguarding phase.

As a best practice, these explicit rules should be scoped to only libraries
whose source code is outside the firebase-android-sdk codebase making annotation
based approaches insufficient.The combination of keep rules resulting from the
annotations, the preguard.txt and the proguard.txt collectively determine the
APIs that are preserved at **runtime**.

## Publishing

Firebase is published as a collection of libraries each of which either
represents a top level product, or contains shared functionality used by one or
more projects. The projects are published as managed maven artifacts available
at [Google's Maven Repository](https://maven.google.com). This section helps
reason about how developers may make changes to firebase projects and have their
apps depend on the modified versions of Firebase.

### Dependencies

Any dependencies, within the projects, or outside of Firebase are encoded as
[maven dependencies](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html)
into the `pom` file that accompanies the published artifact. This allows the
developer's build system (typically Gradle) to build a dependency graph and
select the dependencies using its own [resolution
strategy](https://docs.gradle.org/current/dsl/org.gradle.api.artifacts.ResolutionStrategy.html)

### Commands

The simplest way to publish a project and all its associated dependencies is to
just publish all projects. The following command builds SNAPSHOT dependencies of
all projects. All pom level dependencies within the published artifacts will
also point to SNAPSHOT versions that are co-published.

```bash
./gradlew publishAllToLocal
```

Developers may take a dependency on these locally published versions by adding
the `mavenLocal()` repository to your [repositories
block](https://docs.gradle.org/current/userguide/declaring_repositories.html) in
your app module's build.gradle.

For more advanced use cases where developers wish to make changes to a project,
but have transitive dependencies point to publicly released versions, individual
projects may be published as follows.

```bash
# e.g. to publish Firestore and Functions
./gradlew -PprojectsToPublish=":firebase-firestore,:firebase-functions" \
    publishProjectsToMavenLocal
```

### Code Formatting

Code in this repo is formatted with the google-java-format tool. You can enable
this formatting in Android Studio by downloading and installing the
[google-java-format plugin](https://github.com/google/google-java-format).
The plugin is disabled by default, but the repo contains configuration information
and links to additional plugins.

To run formatting on your entire project you can run
```bash
./gradlew :<firebase-project>:googleJavaFormat
```

### Contributing

We love contributions! Please read our
[contribution guidelines](/CONTRIBUTING.md) to get started.
Android Developers
Docs
Guides
Rate and review



Android Architecture Components   
Part of Android Jetpack.
Android architecture components are a collection of libraries that help you design robust, testable, and maintainable apps. Start with classes for managing your UI component lifecycle and handling data persistence.

Learn the basics of putting together a robust app with the Guide to app architecture.
Manage your app's lifecycle. New lifecycle-aware components help you manage your activity and fragment lifecycles. Survive configuration changes, avoid memory leaks and easily load data into your UI.
Use LiveData to build data objects that notify views when the underlying database changes.
ViewModel stores UI-related data that isn't destroyed on app rotations.
Room is a SQLite object mapping library. Use it to avoid boilerplate code and easily convert SQLite table data to Java objects. Room provides compile time checks of SQLite statements and can return RxJava, Flowable and LiveData observables.
Latest news and videos

MEDIUM

Advanced Usage of WorkManager in multi-process apps
In WorkManager 2.5, we made it easier for multi-process apps to reach out to a specific WorkManager instance running in a designated process. Now, in WorkManager 2.6, we’ve taken it a step further to add support for Workers to run in any process and


MEDIUM

Now in Android #41
Welcome to Now in Android, your ongoing guide to what’s new and notable in the world of Android development. The second beta release for Android 12 is now available! Read the blog for more details on what’s new, including the Privacy Dashboard with a


MEDIUM

Migrating from LiveData to Kotlin’s Flow
LiveData was something we needed back in 2017. The observer pattern made our lives easier, but options such as RxJava were too complex for beginners at the time. The Architecture Components team created LiveData: a very opinionated observable data


MEDIUM

Background Task Inspector
Android Studio includes multiple inspectors, such as the Layout Inspector and Database Inspector, to help you investigate and understand the internal state of your running app. With Android Studio Arctic Fox, we are releasing a new inspector to help


MEDIUM

Room auto-migrations
Easily move your tables between rooms Implementing database migrations with Room just became easier, with the help of auto-migrations, introduced in version 2.4.0-alpha01. Until now, whenever your database schema changes you had to implement a


BLOG

MAD Skills WorkManager : Wrap-Up
In case you missed it, we’ve just finished a MAD Skills series on WorkManager. We started by introducing WorkManager for those new to the library and then proceeded to talk more about advanced usages including how to test and debug your WorkManager


MEDIUM

WorkManager 2.5.0 stable released
📝 The recent release of WorkManager 2.5.0 enables easier usage in a multi-process environment and provides several stability improvements. So if you have an app that manages multiple processes, and you need a robust way to manage background work ( no


BLOG

Improving urban GPS accuracy for your app
At Android, we want to make it as easy as possible for developers to create the most helpful apps for their users. That’s why we aim to provide the best location experience with our APIs like the Fused Location Provider API (FLP). However, we’ve


BLOG

MAD Skills Navigation Wrap-Up
It’s a Wrap! We’ve just finished the first series in the MAD Skills series of videos and articles on Modern Android Development. This time, the topic was Navigation component, the API and tool that helps you create and edit navigation paths through

BLOG

What’s New in Navigation 2020
The latest versions of the Jetpack Navigation library (2.2.0 and 2.3.0) added a lot of requested features and functionality, including dynamic navigation, navigation back stack entries, a library for navigation testing, additional features for deep


BLOG

Getting on the same page with Paging 3
The Paging library enables you to load large sets of data gradually and gracefully, reducing network usage and system resources. You told us that the Paging 2.0 API was not enough - that you wanted easier error handling, more flexibility to implement


BLOG

Unifying Background Task Scheduling on Android
Android users care a lot about the battery life on their phones. In particular, how your app schedules deferrable background tasks play an important role in battery life. To help you build more battery-friendly apps, we introduced WorkManager as the


BLOG

Gesture Navigation: A Backstory
One of the biggest changes in Android Q is the introduction of a new gesture navigation. Just to recap - with the new system navigation mode - users can navigate back (left/right edge swipe), to the home screen (swipe up from the bottom), and trigger


BLOG

What’s New with Android Jetpack and Jetpack Compose
Last year, we launched Android Jetpack, a collection of software components designed to accelerate Android development and make writing high-quality apps easier. Jetpack was built with you in mind -- to take the hardest, most common developer


BLOG

Android Jetpack Navigation Stable Release
Today we're happy to announce the stable release of the Android Jetpack Navigation component. The Jetpack Navigation component's suite of libraries, tooling and guidance provides a robust, complete navigation framework, freeing you from the


BLOG

Android Jetpack WorkManager Stable Release
Simplify how you manage background work with WorkManager Today, we're happy to announce the release of Android Jetpack WorkManager 1.0 Stable. We want to thank so many of you in our dev community who have given us feedback and logged bugs along the


BLOG

Modern background execution in Android
This is the third in a series of blog posts in which outline strategies and guidance in Android with regard to power. Over the years, executing background tasks on Android has evolved. To write modern apps, it's important to learn how to run your


YOUTUBE

Improve your App's Architecture
Learn about Android Architecture Components


#31DaysOfKotlin — Week 4 Recap
An article summarizing the last week of tweets from #31DaysOfKotlin.


#31DaysOfKotlin — Week 3 Recap
An article summarizing the third week of tweets from #31DaysOfKotlin.


#31DaysOfKotlin — Week 2 Recap
An article summarizing the second week of tweets from #31DaysOfKotlin.


#31DaysOfKotlin — Week 1 Recap
An article summarizing the first week of tweets from #31DaysOfKotlin.


Build an App with Architecture Components
Walks you through the different architecture levels that comprise an Android app.


7 Pro-Tips for Room
Some tips on getting the most out of Room.

expand_lessLess
Additional resources
To learn more about Android Architecture Components, consult the following resources.

Samples
Sunflower, a gardening app illustrating Android development best practices with Android Jetpack.
Android Architecture Components GitHub Browser sample
(more...)
Codelabs
Android Room with a View (Java) (Kotlin)
Android Data Binding codelab
(more...)
Training
Udacity: Developing Android Apps with Kotlin
Blog posts
Android Data Binding Library — From Observable Fields to LiveData in two steps
Easy Coroutines in Android: viewModelScope
(more...)
Videos
What's New in Architecture Components (Google I/O'19)
Jetpack Navigation (Google I/O'19)
(more...)
