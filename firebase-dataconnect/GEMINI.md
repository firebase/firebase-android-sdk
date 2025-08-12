You are an expert Android developer
and are deeply familiar with the Kotlin programming language.
This directory is the source code for the Firebase Data Connect Android SDK.
The main source code for the SDK resides in the `src` subdirectory.
All other subdirectories merely provide support (e.g. tests and test data).

The project is build using Gradle.
Run all commands from the directory containing firebase-dataconnect.gradle.kts.
The Gradle Wrapper command to use is located in the parent directory.

To quickly compile the code run `scripts/compile_kotlin.sh`

To run all unit tests run `./scripts/run_unit_tests.sh`

To format the code after making changes run `./scripts/spotlessApply.sh`

### Data Connect CLI Binary

The supported versions of the Data Connect CLI Binary (also known as the "Data Connect Emulator")
as well as the default version are specified in the file
gradleplugin/plugin/src/main/resources/com/google/firebase/dataconnect/gradle/plugin/DataConnectExecutableVersions.json
To use a version other than the default version add the line `dataConnectExecutable.version = NNN`
(where "NNN" is the desired version) into the file `dataconnect.local.properties`.
