# Firebase Android Open Source Development

This repository contains a subset of the Firebase Android SDK source. It
currently includes the following Firebase libraries, and some of their
dependencies:

  * `firebase-common`
  * `firebase-database`
  * `firebase-functions`
  * `firebase-firestore`
  * `firebase-storage`

Firebase is an app development platform with tools to help you build, grow and
monetize your app. More information about Firebase can be found at
https://firebase.google.com.

## Table of contents

1. [Getting Started](#getting-started)
1. [Building](#building)
1. [Testing](#testing)
   1. [Unit Testing](#unit-testing)
   1. [Integration Testing](#integration-testing)
1. [Proguarding](#proguarding)
   1. [APIs used via reflection](#apis-used-via-reflection)
   1. [APIs intended for developer
   consumption](#apis-intended-for-developer-consumption)
   1. [APIs intended for other Firebase
   SDKs](#apis-intended-for-other-firebase-sdks)
1. [Publishing](#publishing)
   1. [Dependencies](#dependencies)
   1. [Commands](#commands)
1. [Code Formatting](#code-formatting)
1. [Contributing](#contributing)

## Getting Started

* Install the latest Android Studio (should be 3.0.1 or later)
* Clone the repo (`git clone git@github.com:firebase/firebase-android-sdk.git`)
* Import the firebase-android-sdk gradle project into Android Studio using the
  **Import project(Gradle, Eclipse ADT, etc.** option.

## Building

Building the Firebase SDKs can be performed by invoking the following on the
command line:
```bash
./gradlew :<firebase-project>:assemble`
```

## Testing

Firebase Android libraries exercise all three types of tests recommended by the
[Android Testing Pyramid](https://developer.android.com/training/testing/fundamentals#testing-pyramid).
Depending on the requirements of the specific project, some or all of these
tests may be used to support changes.

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

For now, you have to disable security rule enforcement for the Realtime
Database, Cloud Firestore, and Cloud Storage in your test project (if running
the integration tests for any of those). Re-enable your security rules after
your test run.

#### Running Integration Tests

Integration tests can be executed on the command line by running
```bash
./gradlew :<firebase-project>:connectedCheck
```

## Proguarding

Firebase Android SDKs operate under the assumption that a vast majority of
developers do not proguard their apps. Artifacts published via the
[Publishing](#publishing) section are pre-proguarded (preguarded?) to reduce the
size impact on apps that consume them. There are three levels of retention that
APIs have, depending on how they are used.

### APIs used via reflection

APIs that need to be preserved up until the app's runtime can be annotated with
[@Keep](https://developer.android.com/reference/android/support/annotation/Keep).
The
[@Keep](https://developer.android.com/reference/android/support/annotation/Keep)
annotation is *blessed* to be honored by android's [default proguard
configuration](https://developer.android.com/studio/write/annotations#keep).
These APIs should be generally **discouraged**, because they can't be
proguarded.

#### Usage

- Annotate APIs with
  [@Keep](https://developer.android.com/reference/android/support/annotation/Keep)

### APIs intended for developer consumption

The
[@Keep](https://developer.android.com/reference/android/support/annotation/Keep)
mechanism described above is too restrictive for APIs that are not used via
reflection, which is the case for a vast majority of the Firebase public APIs.
We annotate these APIs with
[@PublicAPI](firebase-common/src/main/java/com/google/firebase/annotations/PublicApi.java).

#### Usage

- Annotate the necessary APIs with firebase-common's
  [@PublicApi](firebase-common/src/main/java/com/google/firebase/annotations/PublicApi.java)

### APIs intended for other Firebase SDKs

APIs that are intended to be used by Firebase SDKs may be annotated with
`@KeepForSdk`. Much like the custom annotation mechanism, the idea is to let
these APIs pass through preguarding, but not restrict the developer's app from
proguarding. The key benefit here is that the annotation is *blessed* to throw
linter errors on Android Studio if used by the developer from a non firebase
package, thereby providing a valuable guard rail.

#### Usage

- Annotate the APIs with `@KeepForSdk`
- This method may be used in conjunction with @Keep annotations to annotate APIs
  consumed by Firebase SDKs through reflection.

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

To generate the Maven dependency tree under `build/` instead, you can use
`firebasePublish` instead of `publishProjectsToMavenLocal`

### Code Formatting

Code in this repo is formatted with the google-java-format tool. You can enable
this formatting in Android Studio by downloading and installing the
[google-java-format plugin](https://plugins.jetbrains.com/plugin/8527-google-java-format).

To run formatting on your entire project you can run
```bash
./gradlew :<firebase-project>:googleJavaFormat
```

### Contributing

We love contributions! Please read our
[contribution guidelines](/CONTRIBUTING.md) to get started.
