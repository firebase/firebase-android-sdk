# Unreleased
* [feature] Added support for configuring the "thinking" budget when using Gemini
  2.5 series models. (#6990)
* [feature] **Breaking Change**: Add support for Grounding with Google Search (#7042).
    * **Action Required:** Update all references of `groundingAttributions`, `webSearchQueries`, `retrievalQueries` in `GroundingMetadata` to be non-optional.
* [changed] require at least one argument for `generateContent()`, `generateContentStream()` and
  `countTokens()`.
* [feature] Added new overloads for `generateContent()`, `generateContentStream()` and
 `countTokens()` that take a `List<Content>` parameter. 

# 16.2.0
* [changed] Deprecate the `totalBillableCharacters` field (only usable with pre-2.0 models). (#7042)
* [feature] Added support for extra schema properties like `title`, `minItems`, `maxItems`, `minimum`
 and `maximum`. As well as support for the `anyOf` schema. (#7013)

# 16.1.0
* [fixed] Fixed `FirebaseAI.getInstance` StackOverflowException (#6971)
* [fixed] Fixed an issue that was causing the SDK to send empty `FunctionDeclaration` descriptions to the API.
* [changed] Introduced the `Voice` class, which accepts a voice name, and deprecated the `Voices` class.
* [changed] **Breaking Change**: Updated `SpeechConfig` to take in `Voice` class instead of `Voices` class.
    * **Action Required:** Update all references of `SpeechConfig` initialization to use `Voice` class.
* [fixed] Fix incorrect model name in count token requests to the developer API backend

# 16.0.0
* [feature] Initial release of the Firebase AI SDK (`firebase-ai`). This SDK *replaces* the previous
 Vertex AI in Firebase SDK (`firebase-vertexai`) to accommodate the evolving set of supported
 features and services.
  * The new Firebase AI SDK provides **Preview** support for the Gemini Developer API, including its
  free tier offering.
  * Using the Firebase AI SDK with the Vertex AI Gemini API is still generally available (GA).

 If you're using the old `firebase-vertexai`, we recommend
 [migrating to `firebase-ai`](/docs/ai-logic/migrate-to-latest-sdk)
 because all new development and features will be in this new SDK.
* [feature] **Preview:** Added support for specifying response modalities in `GenerationConfig`
 (only available in the new `firebase-ai` package). This includes support for image generation using
 [specific Gemini models](/docs/vertex-ai/models).

 Note: This feature is in Public Preview, which means that it is not subject to any SLA or
 deprecation policy and could change in backwards-incompatible ways.

