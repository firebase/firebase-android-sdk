# Unreleased


# 16.0.2
* [fixed] Improved error message when using an invalid location. (#6428)
* [fixed] Fixed issue where Firebase App Check error tokens were unintentionally missing from the requests. (#6409)
* [fixed] Clarified in the documentation that `Schema.integer` and `Schema.float` only provide hints to the model. (#6420)
* [fixed] Fixed issue were `Schema.double` set the format parameter in `Schema`. (#6432)

# 16.0.1
* [fixed] Fixed issue where authorization headers weren't correctly formatted and were ignored by the backend. (#6400)

# 16.0.0
* [feature] {{firebase_vertexai}} is now Generally Available (GA) and can be
  used in production apps.

  Use the {{firebase_vertexai_sdk}} to call the {{gemini_api_vertexai_long}}
  directly from your app. This client SDK is built specifically for use with
  Android apps, offering security options against unauthorized clients
  as well as integrations with other Firebase services.

    * If you're new to this library, visit the
      [getting started guide](/docs/vertex-ai/get-started?platform=android).

    * If you were using the preview version of the library, visit the
      [migration guide](/docs/vertex-ai/migrate-to-ga?platform=android) to learn
      about some important updates.
* [changed] **Breaking Change**: Changed `functionCallingConfig` parameter type to be nullable in `ToolConfig`. (#6373)
* [changed] **Breaking Change**: Removed `functionResponse` accessor method from `GenerateContentResponse`. (#6373)
* [changed] **Breaking Change**: Migrated `FirebaseVertexAIException` from a sealed class to an abstract class, and marked constructors as internal. (#6368)
* [feature] Added support for `title` and `publicationDate` in citations. (#6309)
* [feature] Added support for `frequencyPenalty`, `presencePenalty`, and `HarmBlockMethod`. (#6309)
* [changed] **Breaking Change**: Introduced `Citations` class. Now `CitationMetadata` wraps that type. (#6276)
* [changed] **Breaking Change**: Reworked `Schema` declaration mechanism. (#6258)
* [changed] **Breaking Change**: Reworked function calling mechanism to use the new `Schema` format. Function calls no longer use native types, nor include references to the actual executable code. (#6258)
* [changed] **Breaking Change**: Made `totalBillableCharacters` field in `CountTokens` nullable and optional. (#6294)
* [changed] **Breaking Change**: Removed `UNKNOWN` option for the `HarmBlockThreshold` enum. (#6294)
* [changed] **Breaking Change**: Removed `UNSPECIFIED` option for the `HarmBlockThreshold`, `HarmProbability`, `HarmSeverity`, and `BlockReason` enums. (#6294)
* [changed] **Breaking Change**: Renamed `BlockThreshold` as `HarmBlockThreshold`. (#6262)
* [changed] **Breaking Change**: Renamed all types and methods starting with `blob` to start with `inlineData`. (#6309)
* [changed] **Breaking Change**: Changed the order of arguments in `InlineDataPart` to match `ImagePart`. (#6340)
* [changed] **Breaking Change**: Changed `RequestOption` to accept only `long` timeout values. (#6289)
* [changed] **Breaking Change**: Moved `requestOptions` to the last positional argument in the `generativeModel` argument list. (#6292)
* [changed] **Breaking Change**: Replaced sealed classes with abstract classes for `StringFormat`. (#6334)
* [changed] **Breaking Change**: Refactored enum classes to be normal classes. (#6340)
* [changed] **Breaking Change**: Marked `GenerativeModel` properties as private. (#6309)
* [changed] **Breaking Change**: Changed `method` parameter type to be nullable in `SafetySettings`. (#6379)

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

