# Crashlytics Buildtools

This directory includes the
[Firebase Crashlytics build tools](https://firebase.google.com/docs/crashlytics/get-started?platform=android#add-plugin),
which allows collecting proguard mappings and NDK symbols to deobfuscate and symbolicate crashes.

## Subprojects

### crashlytics-buildtools

This subproject produces a standalone jar that is used when building an app
that includes the Crashlytics Android SDK. It is responsible for uploading
mappings files, and generating and uploading csyms for NDK builds.

To build and install to the local maven repo:
`./gradlew :firebase-crashlytics-buildtools:publishToMavenLocal`

The produced artifact is "com.google.firebase:firebase-crashlytics-buildtools".

### crashlytics-gradle

This subproject produces a Gradle Plugin which facilitates the use cases of
crashlytics-buildtools for Android apps built with Gradle.

To build and install to the local maven repo:
`./gradlew :firebase-crashlytics-gradle:publishToMavenLocal`

The produced artifact is "com.google.firebase:firebase-crashlytics-gradle". To use the plugin from
Gradle:
`apply plugin: 'com.google.firebase.crashlytics`

## Tests

The functional tests require the environment variable `ANDROID_HOME` to be set. The easiest way to
set it is in "Run/Debug Configurations" dialog and set it to the same thing as `sdk.dir` in
local.properties file.

## Debugging

See the [root README](../README.md#debugging-gradle-plugins) for instructions
on running these projects using the IntelliJ debugger.

Note that to step into Java source from the Groovy code, you'll need source
jars for the Gradle plugin's java dependencies. You can locally publish a
source jar for the Crashlytics Buildtools with:

`./gradlew firebase-crashlytics:crashlytics-buildtools:publishToMavenLocal -PpublishBuildtoolsSource=true`

## Building the standalone / experimental NDK uploader utility & plugin

Customers trying out experimental NDK features, or customers struggling to get
NDK symbols uploaded via Gradle, can use custom builds of the buildtools jar
and Gradle plugin. See crashlytics-buildtools/README.md for customer-facing
instructions.

To build this distribution:

1. If building with the breakpad binaries, build them and add them to the
   buildtool's `resources` directory using the `buildAndCopyToResourcesDir.bsh`
   script in `firebase-crashlytics/crashlytics-breakpad-binaries`

2. Delete the local maven repo:
   `rm -Rf ~/.m2/repository`

3. Build the buildtools 'uberJar' that bundles all dependencies:
   `./gradlew firebase-crashlytics:crashlytics-buildtools:uberJar`

   To support customers still using JDK 8, we need to specify a JDK 8 compiler. If
   your default $JAVA_HOME is not JDK 8, use the `-Dorg.gradle.java.home` option:

   `./gradlew -Dorg.gradle.java.home=$JDK8_HOME firebase-crashlytics:crashlytics-buildtools:uberJar`

4. Install the fatjar to the local repo (double-check that version matches gradle.properties file!):

   ```
   setenv BUILDTOOLS_VERSION=<X.Y.Z>

   mvn install:install-file \
     -Dfile=firebase-crashlytics/crashlytics-buildtools/build/libs/crashlytics-buildtools-all-$BUILDTOOLS_VERSION.jar \
     -Dversion=$BUILDTOOLS_VERSION \
     -DgroupId=com.google.firebase -DartifactId=firebase-crashlytics-buildtools \
     -Dpackaging=jar -DgeneratePom=true
   ```
5. Build the gradle plugin & install to local maven repo:
   `./gradlew firebase-crashlytics:crashlytics-gradle:publishToMavenLocal`
   or
   `./gradlew -Dorg.gradle.java.home=$JDK8_HOME firebase-crashlytics:crashlytics-gradle:publishToMavenLocal`
6. Copy all necessary files to a convenient location and zip up the distribution:
   (Make sure the version numbers in the README file match the files you just made!)

```
rm -Rf buildtools-dist-temp && mkdir -p buildtools-dist-temp/crashlytics-buildtools/repository
cp -R ~/.m2/repository/com buildtools-dist-temp/crashlytics-buildtools/repository/.
cp firebase-crashlytics/crashlytics-buildtools/README.md buildtools-dist-temp/crashlytics-buildtools && \
  cp firebase-crashlytics/crashlytics-buildtools/src/main/resources/LICENSES.txt buildtools-dist-temp/crashlytics-buildtools
cd buildtools-dist-temp && zip -r ../crashlytics-buildtools.zip * && cd ../
```

