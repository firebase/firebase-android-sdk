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

### ProtoUtil

Read src/main/kotlin/com/google/firebase/dataconnect/util/ProtoUtil.kt and use the helper
functions/classes defined therein whenever possible.

## 2. Work Parameters

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

5. Use a "test-driven-delevlopment" work style. This means write a test for a single scenario, make it pass,
   then commit that work into Git, then move onto the next scenario. Note that there are already some existing
   tests in ProtoGraftUnitTest.kt, so the first task is to make those tests pass.

## 3. Detailed Execution Plan

1.  **Handle trivial cases:**
    *   If `structsByPath` is empty, return the receiver `Struct` instance.
    *   If `structsByPath` contains only the empty path, return the `Struct` associated with it.

2.  **Initialize the result `Struct`:**
    *   Create a mutable copy of the `structsByPath` map.
    *   Check for the empty path in the mutable map. If present, remove it and use its corresponding `Struct` as the base for the result `Struct.Builder`.
    *   If the empty path is not present, use the receiver `Struct` as the base.

3.  **Sort paths:** Sort the paths in `structsByPath` by their length. This will ensure that parent paths are created before child paths.

4.  **Iterate and graft:** For each `(path, structToGraft)` in the sorted map:
    a. **Validate path:**
        *   Throw `LastPathSegmentNotFieldException` if the last segment is not a `Field`.
        *   Throw `FirstPathSegmentNotFieldException` if the first segment is not a `Field`. This only applies if the path is not the empty path, which is handled already.

    b. **Navigate to the parent `Struct`:**
        *   Start from the root `Struct.Builder`.
        *   For each segment in the path (except the last one), traverse down the structure.
        *   If a segment is a `Field`, get the corresponding value. If it's a `Struct`, continue with its builder. If it doesn't exist, create a new empty `Struct`, add it, and continue with its builder. If it exists but is not a `Struct`, throw `InsertIntoNonStructException`.
        *   If a segment is a `ListIndex`, the parent path segment must refer to a `ListValue`. The element at the `ListIndex` must exist and be a `Struct`; otherwise, throw `GraftingIntoNonStructInListException`. Continue traversal from the `Struct` found in the list.

    c. **Graft the new `Struct`:**
        *   Get the last segment of the path (which must be a `Field`).
        *   Check if the key already exists in the parent `Struct.Builder`. If it does, throw `KeyExistsException`.
        *   Add the `structToGraft` to the parent `Struct.Builder` at the specified key.

5.  **Return the result:** Build the final `Struct` from the `Struct.Builder` and return it.
