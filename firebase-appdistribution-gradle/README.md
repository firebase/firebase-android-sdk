# App Distribution Gradle Plugin

This directory contains the source code for the
[Firebase App Distribution Gradle Plugin](https://firebase.google.com/docs/app-distribution/android/distribute-gradle),
which allows uploading distributions, enabling access for testers, and adding release notes.

---

## appdistribution-gradle

This subproject produces a Gradle Plugin which faciliates the use cases of
appdistribution-buildtools for Android apps built with Gradle.

#### Build / Install

`./gradlew :firebase-appdistribution-gradle:publishToMavenLocal`

The produced local Maven artifact is "com.google.firebase:firebase-appdistribution-gradle".

#### Test

To run unit tests, run:

`./gradlew :firebase-appdistribution-gradle:test`

To run integration tests, run:

`./gradlew :firebase-appdistribution-gradle:integrationTest`

Integration tests require a valid service account private key,
app ID, and project number to be set in `local.properties`, for example:

> ```properties
> firebase.appId=1:1234567890:android:1234567890
> firebase.projectNumber=1234567890
> ```

Integration tests also require a service-credentials.json file to the firebase-appdistribution-gradle folder. To create that file:

- In the Google cloud dashboard, navigate to `IAM & Admin`. From `IAM & Admin` navigate to `Service Accounts`. In Service Accounts, ensure you have an account that is allowed to run.
  - If you do not, click "Create Service Account", select a name and id of your choosing (e.g., `FAD Gradle Tests` and `fad-gradle-tests`), then Select Firebase App Distribution Admin as a permission for the account.
  - Once you have a service account, click it to edit it, then navigate to the "keys" header inside the service account. From there, click "Add key", "Create New Key", and select JSON as the key type. Save this file to your firebase app distribution gradle plugin folder in a name called service-credentials.json

To test manually, add the plugin to your Android project

`apply plugin: 'com.google.firebase.appdistribution'`

## Release Process

1. Create a PR with the changes, and request a review to submit it to `main`. Add the changes in the
   `CHANGELOG.md`.
2. Once the changes have been submitted to `main` they will be released along with the next Android
   SDK release.

### Building the Maven artifact

Make sure to bump the [version of the plugin](gradle.properties#L3).

Use the
["Make Releases" GitHub Actions workflow](https://github.com/firebase/firebase-android-buildtools/actions/workflows/make-releases.yml)
([source](../.github/workflows/make-releases.yml)) to produce maven artifacts. Please refer to the
[GitHub Actions documentation](https://docs.github.com/en/actions/managing-workflow-runs/manually-running-a-workflow)
on how to trigger the workflow manually. The `m2repository.zip` can be downloaded from the
"Artifacts" section on the workflow execution page.

### Testing the artifact (optional)

Download the `m2repository.zip` artifact from GCS. Extract the contents of the zip file to a local
directory. In the project-level Gradle file of a test app add the path to the local maven
repository to the `repositories` block and
`classpath 'com.google.firebase:firebase-appdistribution-gradle:x.y.z'`
to the dependencies block.

```
    repositories {
        // ...

        // Add path to gradle plugin local maven repository
        maven {
            url 'file://<path_to_gradle_plugin_maven_repo>'
        }
    }
    dependencies {
        // ...

        // Add the pre version of the Gradle plugin
        classpath 'com.google.firebase:firebase-appdistribution-gradle:x.y.z'
    }
```

Sync your project and validate by running an app distribution gradle
command such as:

```
./gradlew assembleDebug appDistributionUploadDebug
```

