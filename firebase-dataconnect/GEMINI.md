# General Rules

* Never commit any changes into Git.
* When making only kdoc or comment changes to a file, the only command you may run is one to format the code.
* When writing kdoc comments, make them thorough and detailed.
* If at any point you need more information, ask a question.

# Miscellaneous Information

## Proto Struct and Value

If more information about Struct and Value classes are needed,
see the proto specification at:
https://github.com/protocolbuffers/protobuf/blob/main/src/google/protobuf/struct.proto

See some example usages Struct and Value in the files
https://github.com/protocolbuffers/protobuf/blob/main/java/util/src/test/java/com/google/protobuf/util/StructsTest.java
and
https://github.com/protocolbuffers/protobuf/blob/main/java/util/src/test/java/com/google/protobuf/util/ValuesTest.java

### DataConnectPath

The DataConnectPath, DataConnectPathSegment, and extension functions on those classes
are defined in src/main/kotlin/com/google/firebase/dataconnect/DataConnectPathSegment.kt.

To gain an even further understanding of these classes and function, read their unit tests in the file
src/test/kotlin/com/google/firebase/dataconnect/DataConnectPathSegmentUnitTest.kt

## Gradle Commands

To verify that code compiles successfully, run this command:

```
../gradlew --configure-on-demand compileDebugKotlin
```

To run the unit tests defined in class aa.bb.cc.XXX, run this command:

```
../gradlew --configure-on-demand testDebugUnitTest --tests aa.bb.cc.XXX
```

After making any changes to Kotlin files, format the code by running this command:

```
../gradlew --configure-on-demand spotlessApply
```

