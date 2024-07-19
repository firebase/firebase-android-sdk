# Unreleased
* [changed] Made `FunctionCallPart.args` nullable.

# 16.0.0-beta03
* [changed] Breaking Change: changed `Schema.int` to return 32 bit integers instead of 64 bit (long).
* [changed] Added `Schema.long` to return 64-bit integer numbers.
* [changed] Added `Schema.double` to handle floating point numbers.
* [changed] Marked `Schema.num` as deprecated, prefer using `Schema.double`.
* [fixed] Fixed an issue with decoding JSON literals (#6028).

# 16.0.0-beta01
* [feature] Added support for `responseMimeType` in `GenerationConfig`.
* [changed] Renamed `GoogleGenerativeAIException` to `FirebaseVertexAIException`.
* [changed] Updated the KDocs for various classes and functions.

