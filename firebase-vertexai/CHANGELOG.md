# Unreleased
* [fixed] Fixed the missing exported dependency on firebase-common dependency [#6271](//github.com/firebase/firebase-android-sdk/issues/6271)
* [fixed] Fixed missing proguard configuration that broke the SDK when using R8 minimization [#6279](//github.com/firebase/firebase-android-sdk/issues/6279)

# 16.0.0-beta05
* [changed] Merged core networking code into VertexAI from a separate library
* [feature] added support for `responseSchema` in `GenerationConfig`.

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

