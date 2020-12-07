# Firebase Android Open Source Development

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

### Unit Testing

These are tests that run on your machine's local Java Virtual Machine (JVM). At
runtime, these tests are executed against a modified version of android.jar
where all final modifiers have been stripped off. This lets us sandbox behaviors
at desired places and use popular mocking libraries.

Unit tests can be executed on the command line by running
```bash
./gradlew :<firebase-project>:check
```

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
