# Main library code, unit tests, and instrumentation tests.

This directory contains the main library code, including the public APIs shipped to customers for Firebase Data Connect.

All the code is written in Kotlin.

## Directory Layout

The directory layout follows the Android Gradle project conventions:
* ./main/kotlin - The main Kotlin source code
* ./main/proto - Protobuf proto files that define the protocol for communicating with the Data Connect backend servers
* ./test/kotlin - The unit tests for code in ./main/kotlin
* ./androidTest/kotlin - The instrumentation tests (a.k.a. "integration" tests) for code in ./main/kotlin

## Protobuf files

The Protobuf files located in ./main/proto are automatically compiled into Java and Kotlin code
during the Gradle build process.

Protobuf generated Java and Kotlin sources are located at ../build/generated/source/proto/debug/java

For example, the `StreamRequest` message defined in ./src/main/proto/google/firebase/dataconnect/proto/connector_stream_service.proto
gets compiled into ../build/generated/source/proto/debug/java/google/firebase/dataconnect/proto/StreamRequest.java

Normally you ignore files in "build" directories;
HOWEVER, **DO NOT IGNORE THE JAVA AND KOTLIN SOURCES IN THE "build" DIRECTORIES**

## Gradle commands

The commands below show how to run Gradle to build, run tests, and format code.
Replace GRADLEW with the path to the Gradle wrapper in all the commands.
To find the path to the Gradle wrapper, look two directory levels up
from the directory containing this file. That is: ../../gradlew

## Format code

GRADLEW --configure-on-demand :firebase-dataconnect:spotlessApply

## Format code

In order to compile any code in the ./src/main directory, run this command:

## Compile Kotlin and Java code

This command compiles 3 source sets:
* :firebase-dataconnect:compileDebugSources compiles code in ./main
* :firebase-dataconnect:compileDebugUnitTestKotlin compiles code in ./test
* :firebase-dataconnect:compileDebugAndroidTestKotlin compiles code in ./androidTest

These source sets may be selectively removed from the Gradle command, if desired.

GRADLEW --configure-on-demand :firebase-dataconnect:compileDebugSources :firebase-dataconnect:compileDebugUnitTestKotlin :firebase-dataconnect:compileDebugAndroidTestKotlin

## Write unit tests

The following unit tests **SHOULD** be referenced and used as templates for the coding style, test coverage, and coding idioms of unit tests:

* test/kotlin/com/google/firebase/dataconnect/util/ClearableValueUnitTest.kt
* test/kotlin/com/google/firebase/dataconnect/util/LaterValueUnitTest.kt
* test/kotlin/com/google/firebase/dataconnect/util/MaybeValueUnitTest.kt
* test/kotlin/com/google/firebase/dataconnect/util/ProtoUtilUnitTest.kt

## Run unit tests

In order to run the unit tests in the ./src/test directory, run this command:

GRADLEW --configure-on-demand :firebase-dataconnect:testDebugUnitTest

In order to run the unit tests from a specific test class in the ./src/test directory (e.g. com.google.firebase.dataconnect.LocalDateUnitTest), run this command:

GRADLEW --configure-on-demand :firebase-dataconnect:testDebugUnitTest --tests com.google.firebase.dataconnect.LocalDateUnitTest

replacing "com.google.firebase.dataconnect.LocalDateUnitTest" with the fully qualified class name.

## Run instrumentation tests

In order to run the instrumentation tests in the ./src/androidTest directory, run this command:

GRADLEW --configure-on-demand :firebase-dataconnect:connectedDebugAndroidTest

In order to run the integration tests from a specific test class in the ./src/androidTest directory (e.g. com.google.firebase.dataconnect.EnumIntegrationTest), run this command:

GRADLEW --configure-on-demand :firebase-dataconnect:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.google.firebase.dataconnect.EnumIntegrationTest

replacing "com.google.firebase.dataconnect.EnumIntegrationTest" with the fully qualified class name.
