# Firebase Android Open Source Development

This repository contains the source code for all Android Firebase SDKs except Analytics and Auth.

Firebase is an app development platform with tools to help you build, grow and monetize your app.
More information about Firebase can be found at https://firebase.google.com.

## Table of Contents

1. [Getting Started](#getting-started)
2. [Testing](#testing)
   1. [Unit Testing](#unit-testing)
   2. [Integration Testing](#integration-testing)
3. [Annotations](#annotations)
   1. [@Keep](#keep)
   2. [@KeepForSdk](#keepforsdk)
4. [Public API Surface](#public-api-surface)
5. [Proguarding](#proguarding)
   1. [Proguard config](#proguard-config)
6. [Publishing](#publishing)
   1. [Dependencies](#dependencies)
   2. [Commands](#commands)
7. [Code Formatting](#code-formatting)
8. [Contributing](#contributing)

## Getting Started

- Install JDK 17 (required to build and run all SDKs).
- Install the latest stable Android Studio. The minimum supported version is dictated by the
  `androidGradlePlugin` version in `gradle/libs.versions.toml` (currently AGP 8.13, which requires
  Narwhal 3 Feature Drop | 2025.1.3 or later).
- Clone the repo (`git clone --recurse-submodules git@github.com:firebase/firebase-android-sdk.git`).
  - When cloning the repo, it is important to get the submodules as well. If you have already cloned
    the repo without the submodules, they will be initialized automatically when
    `firebase-crashlytics-ndk` is built (by its `preBuild` task), or you can update them manually by
    running `git submodule update --init --recursive`.
- Open the `firebase-android-sdk` Gradle project in Android Studio.
- `firebase-crashlytics-ndk` requires Android NDK 27 (`27.2.12479018`). See
  [firebase-crashlytics-ndk](firebase-crashlytics-ndk/README.md) for more details on building and
  testing that module.

## Testing

Firebase Android libraries exercise all three types of tests recommended by the
[Android Testing Pyramid](https://developer.android.com/training/testing/fundamentals#testing-pyramid).
Depending on the requirements of the specific project, some or all of these tests may be used to
support changes.

> :warning: **Running tests with Error Prone**
>
> To run with Error Prone, add `withErrorProne` to the command line, e.g.:
>
> `./gradlew :<firebase-project>:check withErrorProne`.

### Unit Testing

These are tests that run on your machine's local Java Virtual Machine (JVM). Most projects use
[Robolectric](https://robolectric.org/), which runs the tests against an instrumented version of the
Android framework classes. This lets us sandbox behaviors at desired places and use popular mocking
libraries.

Unit tests can be executed on the command line by running:

```bash
./gradlew :<firebase-project>:check
```

### Integration Testing

These are tests that run on a hardware device or emulator. These tests have access to
Instrumentation APIs and provide access to information such as the
[Android Context](https://developer.android.com/reference/android/content/Context). In Firebase,
instrumentation tests are used in different capacities by different projects. Some tests may
exercise device capabilities while stubbing any calls to the backend, whereas others may call
out to nightly backend builds to ensure distributed API compatibility.

Along with Espresso, they are also used to test projects that have UI components.

#### Project Setup

Before you can run integration tests, you need to add a `google-services.json` file to the root of
your checkout. You can use the `google-services.json` from any project that includes an Android app,
though you'll likely want one that's separate from any production data you have because our tests
write random data.

If you don't have a suitable testing project already:

- Open the [Firebase console](https://console.firebase.google.com/).
- If you don't yet have a project you want to use for testing, create one.
- Add an Android app to the project.
- Give the app any package name you like.
- Download the resulting `google-services.json` file and put it in the root of your checkout.

#### Running Integration Tests on Local Emulator

Integration tests can be executed on the command line by running:

```bash
./gradlew :<firebase-project>:connectedCheck
```

#### Running Integration Tests on Firebase Test Lab

> You need additional setup for this to work:
>
> - `gcloud` needs to be [installed](https://cloud.google.com/sdk/install) on your local machine.
> - `gcloud` needs to be configured with a project that has billing enabled.
> - `gcloud` needs to be authenticated with credentials that have the 'Firebase Test Lab Admin' role.

Integration tests can be executed on the command line by running:

```bash
./gradlew :<firebase-project>:deviceCheck
```

This will execute tests on devices that are configured per project. If nothing is configured for the
project, the tests will run on `model=panther,version=33,locale=en,orientation=portrait`.

Projects can be configured in the following way:

```groovy
firebaseTestLab {
  // To get a list of available devices, execute `gcloud firebase test android models list`
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
[@Keep](https://developer.android.com/reference/androidx/annotation/Keep). The
[@Keep](https://developer.android.com/reference/androidx/annotation/Keep) annotation is
_blessed_ to be honored by Android's
[default ProGuard configuration](https://developer.android.com/studio/write/annotations#keep). This
annotation is commonly used for reflection. These APIs should be generally
**discouraged** because they can't be proguarded.

### @KeepForSdk

APIs that are intended to be used by Firebase SDKs should be annotated with `@KeepForSdk`. The key
benefit here is that the annotation is _blessed_ to throw linter errors in Android Studio if used by
the developer from a non-Firebase package, thereby providing a valuable guardrail.

## Public API Surface

There is no marker annotation for public APIs. Anything that is `public` or `protected` under
standard Java and Kotlin visibility rules is part of the public API surface, unless its doc comment
carries an `@hide` tag. Members annotated with `@KeepForSdk` must also be tagged `@hide`, otherwise
they are reported as public API.

The public API surface is tracked with Metalava in each project's `api.txt`. After changing a public
API, regenerate it by running:

```bash
./gradlew :<firebase-project>:generateApiTxtFile
```

The `apiInformation` and `metalavaSemver` tasks verify API compatibility and determine the version
bump (major, minor, patch) required for the next release.

## Proguarding

Firebase SDKs do not proguard themselves, but support proguarding. Firebase SDKs themselves are
proguard-friendly, but the dependencies of Firebase SDKs may not be.

### Proguard config

Projects that need consumer ProGuard rules declare them in a file (conventionally `proguard.txt`)
registered via `consumerProguardFiles` in the project's build file. These rules are honored by the
developer's app while building the app's proguarded APK, and typically contain the keep rules that
need to be honored during the app's proguarding phase.

As a best practice, these explicit rules should be scoped to only libraries whose source code is
outside the firebase-android-sdk codebase, making annotation-based approaches insufficient. The
combination of keep rules resulting from the annotations and `proguard.txt` collectively determines
the APIs that are preserved at **runtime**.

## Publishing

Firebase is published as a collection of libraries, each of which either represents a top-level
product or contains shared functionality used by one or more projects. The projects are published
as managed Maven artifacts available at [Google's Maven Repository](https://maven.google.com). This
section explains how developers can make changes to Firebase projects and have their apps depend on
the modified versions of Firebase.

### Dependencies

Any dependencies within the projects or outside of Firebase are encoded as
[Maven dependencies](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html)
into the `pom` file that accompanies the published artifact. This allows the developer's build
system (typically Gradle) to build a dependency graph and select the dependencies using its own
[resolution strategy](https://docs.gradle.org/current/dsl/org.gradle.api.artifacts.ResolutionStrategy.html).

### Commands

For more advanced use cases where developers wish to make changes to a project but have transitive
dependencies point to publicly released versions, individual projects may be published as follows:

```bash
# e.g. to publish Firestore and Functions
./gradlew -PprojectsToPublish="firebase-firestore,firebase-functions" \
    publishReleasingLibrariesToMavenLocal
```

Developers can depend on these locally published versions by adding the `mavenLocal()`
repository to the
[repositories block](https://docs.gradle.org/current/userguide/declaring_repositories.html) in their
app module's `build.gradle`.

## Code Formatting

Java, Kotlin, Gradle Kotlin DSL scripts (`.gradle.kts`), and Markdown files are formatted using
`spotless`.

To run formatting on a project, run:

```bash
./gradlew :<firebase-project>:spotlessApply
```

## Contributing

We love contributions! Please read our [contribution guidelines](/CONTRIBUTING.md) to get started.
