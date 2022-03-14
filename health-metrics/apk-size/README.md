# APK Size

This directory contains the test app used for measuring binary size for each Firebase Android SDK.

The size measurements displayed in
[pull requests](https://github.com/firebase/firebase-android-sdk/pulls) and on the
[dashboard](https://firebase.google.com/docs/android/sdk-metrics#binary-measurements) are based on
the APK files built with this sample app.

## Usage

Within the directory `firebase-android-sdk/health-metrics/apk-size`, run

```
./gradlew assemble -Psdks=<sdk-1>,<sdk-2>, ... ,<sdk-n>
```

where an `<sdk>` is formatted as `<groupd-id>:<artifact-id>:<version>`. Upon successful builds,
APK files will appear in the gradle build output folder (`build/outputs/apk`).

**NOTE**: For unpublished SDK artifacts, they need to be staged in a local maven repository (for
example, in this project: `build/m2repository/`) in order for gradle to find them during the build.

## Toolchains

* Gradle 6.5.1 ([`gradle-wrapper.properties`](./gradle/wrapper/gradle-wrapper.properties))
* Android Gradle Plugin 4.1.0-beta02 ([`apk-size.gradle`](./apk-size.gradle))
