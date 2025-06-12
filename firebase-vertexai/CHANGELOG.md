# Unreleased


# 16.5.0
* [changed] **Renamed / Replaced:** Vertex AI in Firebase (`firebase-vertexai`) has been renamed and
 replaced by the new Firebase AI SDK: `firebase-ai`. This is to accommodate the evolving set of
 supported features and services. Please [**migrate to the new `firebase-ai` package**](/docs/vertex-ai/migrate-to-latest-sdk).

 Note: Existing users of the Vertex AI in Firebase SDK (`firebase-vertexai`) may continue to use the 
 SDK and receive bug fixes but, going forward, new features will only be added into the new Firebase
 AI SDK.

 The following changes and features are in the Vertex AI in Firebase SDK (`firebase-vertexai`), but
 we recommend that you accommodate them (as applicable) as part of migrating to the `firebase-ai` SDK.
* [changed] **Breaking Change**: Removed the `LiveContentResponse.Status` class, and instead have nested the status
  fields as properties of `LiveContentResponse`. (#6941)
* [changed] **Breaking Change**: Removed the `LiveContentResponse` class, and instead have provided subclasses
  of `LiveServerMessage` that match the responses from the model. (#6941)
* [feature] Added support for the `id` field on `FunctionResponsePart` and `FunctionCallPart`. (#6941)
* [feature] Added a helper field for getting all the `InlineDataPart` from a `GenerateContentResponse`. (#6941)

# 16.4.0
* [changed] **Breaking Change**: `LiveModelFutures.connect` now returns `ListenableFuture<LiveSessionFutures>` instead of `ListenableFuture<LiveSession>`.
    * **Action Required:** Remove any transformations from LiveSession object to LiveSessionFutures object. 
    * **Action Required:** Change type of variable handling `LiveModelFutures.connect` to `ListenableFuture<LiveSessionsFutures>`
* [changed] **Breaking Change**: Removed `UNSPECIFIED` value for enum class `ResponseModality`
    * **Action Required:** Remove all references to `ResponseModality.UNSPECIFIED`
* [changed] **Breaking Change**: Renamed `LiveGenerationConfig.setResponseModalities` to `LiveGenerationConfig.setResponseModality`
    * **Action Required:** Replace all references of `LiveGenerationConfig.setResponseModalities` with `LiveGenerationConfig.setResponseModality`
* [feature] Added support for `HarmBlockThreshold.OFF`. See the
  [model documentation](https://cloud.google.com/vertex-ai/generative-ai/docs/multimodal/configure-safety-filters#how_to_configure_content_filters){: .external}
  for more information.
* [fixed] Improved thread usage when using a `LiveGenerativeModel`. (#6870)
* [fixed] Fixed an issue with `LiveContentResponse` audio data not being present when the model was
  interrupted or the turn completed. (#6870)
* [fixed] Fixed an issue with `LiveSession` not converting exceptions to `FirebaseVertexAIException`. (#6870)
* [feature] Enable response generation in multiple modalities. (#6901)
* [changed] Removed the `LiveContentResponse.Status` class, and instead have nested the status
  fields as properties of `LiveContentResponse`. (#6906)

# 16.3.0
* [feature] Emits a warning when attempting to use an incompatible model with
  `GenerativeModel` or `ImagenModel`.
* [changed] Added new exception type for quota exceeded scenarios.
* [feature] `CountTokenRequest` now includes `GenerationConfig` from the model.
* [feature] **Public Preview:** Added support for streaming input and output (including audio) using the [Gemini Live API](/docs/vertex-ai/live-api?platform=android)
  **Note**: This feature is in Public Preview, which means that it is not subject to any SLA or deprecation policy and could change in backwards-incompatible ways.
* [changed] **Breaking Change**: `ImagenInlineImage.data` now returns the raw
  image bytes (in JPEG or PNG format, as specified in
  `ImagenInlineImage.mimeType`) instead of Base64-encoded data. (#6800)
    * **Action Required:** Remove any Base64 decoding from your
      `ImagenInlineImage.data` usage.
    * The `asBitmap()` helper method is unaffected and requires no code changes.

# 16.2.0
* [fixed] Added support for new values sent by the server for `FinishReason` and `BlockReason`.
* [changed] Added support for modality-based token count. (#6658)
* [feature] Added support for generating images with Imagen models.

# 16.1.0
* [changed] Internal improvements to correctly handle empty model responses.

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

