In order to format the code in this directory and all subdirectories run this command:

../../gradlew --configure-on-demand :firebase-dataconnect:spotlessApply

In order to compile any code in the ./src/main directory, run this command:

../../gradlew --configure-on-demand :firebase-dataconnect:compileDebugKotlin

In order to compile any code in the ./src/test directory, run this command:

../../gradlew --configure-on-demand :firebase-dataconnect:compileDebugUnitTestKotlin

In order to compile any code in the ./src/androidTest directory, run this command:

../../gradlew --configure-on-demand :firebase-dataconnect:compileDebugAndroidTestKotlin

In order to run the unit tests in the ./src/test directory, run this command:

../../gradlew --configure-on-demand :firebase-dataconnect:testDebugUnitTest

In order to run the unit tests from a specific test class in the ./src/test directory (e.g. com.google.firebase.dataconnect.LocalDateUnitTest), run this command:

../../gradlew --configure-on-demand :firebase-dataconnect:testDebugUnitTest --tests com.google.firebase.dataconnect.LocalDateUnitTest

replacing "com.google.firebase.dataconnect.LocalDateUnitTest" with the fully qualified class name.

In order to run the integration tests in the ./src/androidTest directory, run this command:

../../gradlew --configure-on-demand :firebase-dataconnect:connectedDebugAndroidTest

In order to run the integration tests from a specific test class in the ./src/androidTest directory (e.g. com.google.firebase.dataconnect.EnumIntegrationTest), run this command:

../../gradlew --configure-on-demand :firebase-dataconnect:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.google.firebase.dataconnect.EnumIntegrationTest

replacing "com.google.firebase.dataconnect.EnumIntegrationTest" with the fully qualified class name.

Whenever you complete a coding task you **MUST** ensure that the code compiles.

After verifying that the code compiles you **MUST** format the code before reporting the task to be complete.
