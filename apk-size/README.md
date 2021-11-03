# APK Size

This directory contains the test app used for collecting APK size of Firebase Android SDKs. The size
measurements displayed in pull requests and on the dashboard are based on the APKs built with this
test app.

## Usage

Run

```
./gradlew assemble -Psdks=<sdk-1>,<sdk-2>, ... ,<sdk-n>
```
where an `<sdk>` is formatted as `<groupd-id>:<artifact-id>:<version>` at directory
`firebase-android-sdk/apk-size`. Upon successful builds, APK files will appear in the gradle build
output folder (`build/outputs/apk`).

**_NOTE:_** For unpublished artifacts, they need to be staged in a local maven repository (for
example, in this project: `build/m2repository/`) in order for gradle to find them during the build.

## Toolchains

* Gradle 6.5.1 ([`gradle-wrapper.properties`](./gradle/wrapper/gradle-wrapper.properties))
* Android Gradle Plugin 4.1.0-beta02 ([`apk-size.gradle`](./apk-size.gradle))

## CI Setup

See [job history](https://android-ci.firebaseopensource.com/job-history/android-ci/logs/postsubmit-binary-size)
to find out more information on measurement runs on CI.
