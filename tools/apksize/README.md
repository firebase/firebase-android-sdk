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
generateMeasurements`. This will output a human readable report to standard out.
Appending `-Ppull_request=999` will instead generate the report to upload, where
`999` is the pull request number to place in the report.

The uploadMeasurements task is not intended to be invoked manually. However, it
may be invoked with the above pull request flag and `-Pdatabase_config=path`
where `path` is the path to the config file. The config file must have the
following structure where the values in all-caps are placeholders for the
relevant pieces of configuration:

```
host:HOST
database:DATABASE
user:USER
password:PASSWORD
```

## Current Support

All projects in this repository are supported with an aggressive ProGuard
profile. Less aggressive ProGuard profiles will be added at a future date.
