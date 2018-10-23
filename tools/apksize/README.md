# APK Size Tooling

## Purpose

This tooling measures the size of APKs using Firebase. The APKs are simple apps
that exercise only a small faction of the API surface. These numbers help to
show how an app's size might grow if Firebase is included.

## How to Use

There are two tasks defined in this subproject: generateMeasurements and
uploadMeasurements. The former gathers the measurements and writes them to a
file in the build directory. The latter is invoked by CI and uploads the report
to an SQL database.

The generateMeasurements task may be manually run with `./gradlew -q
generateMeasurements -Ppull_request=999`. A pull request number is required to
generate the report.

## Current Support

All projects in this repository are supported with an aggressive ProGuard
profile. Less aggressive ProGuard profiles will be added at a future date.
