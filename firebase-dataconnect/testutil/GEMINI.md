# Unit testing library utility classes

This directory contains a collection of random, but useful, clases for use in unit tests.

All the code is written in Kotlin.

## Directory Layout

The directory layout follows the Android Gradle project conventions:
* ./main/kotlin - The main Kotlin source code for the unit testing utility library.
* ./test/kotlin - The unit tests for code in ./main/kotlin
* ./androidTest/kotlin - The instrumentation tests (a.k.a. "integration" tests) for code in ./main/kotlin

## Gradle commands

The commands below show how to run Gradle to build, run tests, and format code.
Replace GRADLEW with the path to the Gradle wrapper in all the commands.
To find the path to the Gradle wrapper, look two directory levels up
from the directory containing this file. That is: ../../gradlew

## Format code

GRADLEW --configure-on-demand :firebase-dataconnect:testutil:spotlessApply

## Format code

In order to compile any code in the ./src/main directory, run this command:

## Compile Kotlin and Java code

This command compiles 3 source sets:
* :firebase-dataconnect:testutil:compileDebugSources compiles code in ./main
* :firebase-dataconnect:testutil:compileDebugUnitTestKotlin compiles code in ./test

These source sets may be selectively removed from the Gradle command, if desired.

GRADLEW --configure-on-demand :firebase-dataconnect:testutil:compileDebugSources :firebase-dataconnect:testutil:compileDebugUnitTestKotlin

## Run unit tests

In order to run the unit tests in the ./src/test directory, run this command:

GRADLEW --configure-on-demand :firebase-dataconnect:testutil:testDebugUnitTest

In order to run the unit tests from a specific test class in the ./src/test directory (e.g. com.google.firebase.dataconnect.AnyValueUnitTest), run this command:

GRADLEW --configure-on-demand :firebase-dataconnect:testutil:testDebugUnitTest --tests com.google.firebase.dataconnect.AnyValueUnitTest

replacing "com.google.firebase.dataconnect.LocalDateUnitTest" with the fully qualified class name.

## Run instrumentation tests

This "testutil" submodule has no instrumentation tests.
