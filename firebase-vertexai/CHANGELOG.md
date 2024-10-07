# Unreleased
* [changed] Breaking Change: introduced `Citations` class. Now `CitationMetadata` wraps that type.
* [changed] Breaking Change: reworked Schema declaration mechanism (#6258).
* [changed] Breaking Change: reworked function calling mechanism to use the new Schema format. Function calls no longer use native types, nor include references to the actual executable code. (#6258).
* [changed] Breaking Change: changed `RequestOption` to accept only `long` timeout values (#6289).
* [changed] Breaking Change: moved `requestOptions` to the last positional argument in the `generativeModel` argument list (#6292).
* [changed] Breaking Change: made `totalBillableCharacters` field in `CountTokens` nullable and optional (#6294).
* [changed] Breaking Change: removed `UNKNOWN` option of the `HarmBlockThreshold` enum (#6294).
* [changed] Breaking Change: removed `UNSPECIFIED` option of the `HarmBlockThreshold`, `HarmProbability`, `HarmSeverity`, and `BlockReason` enums (#6294).
* [feature] Added support for `title`, and `publicationDate` in Citations (#6309).
* [feature] Added support for `frequencyPenalty`, `presencePenalty`, and `HarmBlockMethod` (#6309).
* [changed] Breaking Change: renamed all types and methods starting with `blob` to start with `inlineData` (#6309).
* [changed] Breaking Change: marked `GenerativeModel` properties private (#6309).
* [changed] Breaking Change: introduced `FunctionCall` and `FunctionResponse` types. Now `FunctionCallPart` and `FunctionResponsePart` wrap those types respectively (#6311).
* [changed] Breaking Change: renamed `BlockThreshold` as `HarmBlockThreshold` (#6262).
* [changed] Breaking Change: replaced sealed classes with abstract classes for `StringFormat`(#6334).
* [changed] Breaking Change: changed order of arguments in `InlineDataPart` to match `ImagePart`(#6340).
* [changed] Breaking Change: refactored enum classes to be normal classes (#6340).


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
