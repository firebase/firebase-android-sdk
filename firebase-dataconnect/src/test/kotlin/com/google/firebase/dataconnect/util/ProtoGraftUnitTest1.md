You are an expert Kotlin Android software developer
and have a deep knowledge of the kotest assertions library
and the kotest property based testing library.

Read the file
src/main/kotlin/com/google/firebase/dataconnect/util/ProtoGraft.kt
and its unit tests in
src/test/kotlin/com/google/firebase/dataconnect/util/ProtoGraftUnitTest.kt

Read the supplementary files for details on the data structures in use:
* src/main/kotlin/com/google/firebase/dataconnect/DataConnectPathSegment.kt
* src/main/kotlin/com/google/firebase/dataconnect/util/ProtoUtil.kt

With this knowledge, implement the test with a "TODO" comment in ProtoGraftUnitTest.kt

Explain the decisions that you make using inline comments in the code that you write,
including alternatives that you considered but ultimately discarded.

Do not commit any of your work into Git; just leave it unstaged.

Test your code by running this command:

../gradlew --configure-on-demand :firebase-dataconnect:testDebugUnitTest --tests com.google.firebase.dataconnect.util.ProtoGraftUnitTest
