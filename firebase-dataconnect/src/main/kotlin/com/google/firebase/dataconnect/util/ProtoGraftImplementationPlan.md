# Plan to implement ProtoGraft.withGraftedInStructs()

## 1. Gather Background Information

### Proto Struct and Value

The implementation uses the proto Struct and Value classes.

The proto specification for these classes is available at
https://github.com/protocolbuffers/protobuf/blob/main/src/google/protobuf/struct.proto

Read that proto file and see some example usages of it in
https://github.com/protocolbuffers/protobuf/blob/main/java/util/src/test/java/com/google/protobuf/util/StructsTest.java
and
https://github.com/protocolbuffers/protobuf/blob/main/java/util/src/test/java/com/google/protobuf/util/ValuesTest.java

### DataConnectPath

Read src/main/kotlin/com/google/firebase/dataconnect/DataConnectPathSegment.kt
and get a full understanding of DataConnectPath and DataConnectPathSegment.

To gain further understanding, read its unit tests in the file
src/test/kotlin/com/google/firebase/dataconnect/DataConnectPathSegmentUnitTest.kt

### ProtoGraft

Read the file src/main/kotlin/com/google/firebase/dataconnect/util/ProtoGraft.kt

The function to implement is withGraftedInStructs().
Read the kdoc for that function as it specifies what the implementation must do.

Read src/test/kotlin/com/google/firebase/dataconnect/util/ProtoGraftUnitTest.kt
which defines some basic, non-comprehensive unit tests for the withGraftedInStructs()
function.

## 2. Work Outline

Here are some requirements and restrictions when implementing the withGraftedInStructs() function:

1. Commit to Git often. After any meaningful chunk of work, run `git commit`
   to save your work. Specifiy a meaningful description that includes the file name
   of any files modified.

2. To test that code compiles, run this command:
   ../gradlew --configure-on-demand :firebase-dataconnect:compileDebugUnitTestKotlin

3. Add unit test to ProtoGraftUnitTest.kt as you add functionality to withGraftedInStructs().
   To run the unit tests, run this command:
   ../gradlew --configure-on-demand :firebase-dataconnect:testDebugUnitTest --tests com.google.firebase.dataconnect.util.ProtoGraftUnitTest

4. If at any point you need more information, ask me a question.

## 3. Plan The Work

Think hard about a plan to implement the withGraftedInStructs() function.
Split the work into multiple steps.
Edit the file src/main/kotlin/com/google/firebase/dataconnect/util/ProtoGraftImplementationPlan.kt
by adding the detailed steps to the section "4. Detailed Execution Plan".

Ask me to review the plan.
Ask me if I want any changes to the plan.
If I say "yes" then apply my changes, and ask me again, until I say "no" I don't want any changes.

Finally, execute the plan that you created.

## 4. Detailed Execution Plan

[to be filled in]
