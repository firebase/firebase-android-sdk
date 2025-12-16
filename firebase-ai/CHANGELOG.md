# Unreleased

- [feature] Added support for configuring thinking levels with Gemini 3 series
  models and onwards. (#7599)

# 17.7.0

- [changed] Added `LiveAudioConversationConfig` to control different aspects of the conversation
  while using the `startAudioConversation` function.
- [fixed] Fixed an issue causing streaming chat interactions to drop thought signatures. (#7562)

# 17.6.0

- [feature] Added support for server templates via `TemplateGenerativeModel` and
  `TemplateImagenModel`. (#7503)

# 17.5.0

- [changed] Added better scheduling and louder output for Live API.
- [changed] Added support for input and output transcription. (#7482)
- [feature] Added support for sending realtime audio and video in a `LiveSession`.
- [changed] Removed redundant internal exception types. (#7475)

# 17.4.0

- [changed] **Breaking Change**: Removed the `candidateCount` option from `LiveGenerationConfig`
- [changed] Added support for user interrupts for the `startAudioConversation` method in the
  `LiveSession` class. (#7413)
- [changed] Added support for the URL context tool, which allows the model to access content from
  provided public web URLs to inform and enhance its responses. (#7382)
- [changed] Added better error messages to `ServiceConnectionHandshakeFailedException` (#7412)
- [changed] Marked the public constructor for `UsageMetadata` as deprecated (#7420)
- [changed] Using Firebase AI Logic with the Gemini Developer API is now Generally Available (GA).
- [changed] Using Firebase AI Logic with the Imagen generation APIs is now Generally Available (GA).

# 17.3.0

- [changed] Bumped internal dependencies.
- [feature] Added support for code execution.
- [changed] Marked the public constructors for `ExecutableCodePart` and `CodeExecutionResultPart` as
  deprecated.
- [feature] Introduced `MissingPermissionsException`, which is thrown when the necessary permissions
  have not been granted by the user.
- [feature] Added helper functions to `LiveSession` to allow developers to track the status of the
  audio session and the underlying websocket connection.
- [changed] Added new values to `HarmCategory` (#7324)
- [fixed] Fixed an issue that caused unknown or empty `Part`s to throw an exception. Instead, we now
  log them and filter them from the response (#7333)

# 17.2.0

- [feature] Added support for returning thought summaries, which are synthesized versions of a
  model's internal reasoning process.
- [fixed] Fixed an issue causing the accessor methods in `GenerateContentResponse` to throw an
  exception when the response contained no candidates.
- [changed] Added better description for requests which fail due to the Gemini API not being
  configured.
- [changed] Added a `dilation` parameter to `ImagenMaskReference.generateMaskAndPadForOutpainting`
  (#7260)
- [feature] Added support for limited-use tokens with Firebase App Check. These limited-use tokens
  are required for an upcoming optional feature called _replay protection_. We recommend
  [enabling the usage of limited-use tokens](https://firebase.google.com/docs/ai-logic/app-check)
  now so that when replay protection becomes available, you can enable it sooner because more of
  your users will be on versions of your app that send limited-use tokens. (#7285)

# 17.1.0

=======

- [feature] added support for Imagen Editing, including inpainting, outpainting, control, style
  transfer, and subject references (#7075)
- [feature] **Preview:** Added support for bidirectional streaming in Gemini Developer Api

# 17.0.0

- [feature] Added support for configuring the "thinking" budget when using Gemini 2.5 series models.
  (#6990)
- [feature] **Breaking Change**: Add support for grounding with Google Search (#7042).
  - **Action Required:** Update all references of `groundingAttributions`, `webSearchQueries`,
    `retrievalQueries` in `GroundingMetadata` to be non-optional.
- [changed] require at least one argument for `generateContent()`, `generateContentStream()` and
  `countTokens()`.
- [feature] Added new overloads for `generateContent()`, `generateContentStream()` and
  `countTokens()` that take a `List<Content>` parameter.
- [changed] **Breaking Change**: Updated minSdkVersion to API level 23 or higher.

# 16.2.0

- [changed] Deprecate the `totalBillableCharacters` field (only usable with pre-2.0 models). (#7042)
- [feature] Added support for extra schema properties like `title`, `minItems`, `maxItems`,
  `minimum` and `maximum`. As well as support for the `anyOf` schema. (#7013)

# 16.1.0

- [fixed] Fixed `FirebaseAI.getInstance` StackOverflowException (#6971)
- [fixed] Fixed an issue that was causing the SDK to send empty `FunctionDeclaration` descriptions
  to the API.
- [changed] Introduced the `Voice` class, which accepts a voice name, and deprecated the `Voices`
  class.
- [changed] **Breaking Change**: Updated `SpeechConfig` to take in `Voice` class instead of `Voices`
  class.
  - **Action Required:** Update all references of `SpeechConfig` initialization to use `Voice`
    class.
- [fixed] Fix incorrect model name in count token requests to the developer API backend

# 16.0.0

- [feature] Initial release of the Firebase AI SDK (`firebase-ai`). This SDK _replaces_ the previous
  Vertex AI in Firebase SDK (`firebase-vertexai`) to accommodate the evolving set of supported
  features and services.
  - The new Firebase AI SDK provides **Preview** support for the Gemini Developer API, including its
    free tier offering.
  - Using the Firebase AI SDK with the Vertex AI Gemini API is still generally available (GA).

If you're using the old `firebase-vertexai`, we recommend
[migrating to `firebase-ai`](/docs/ai-logic/migrate-to-latest-sdk) because all new development and
features will be in this new SDK.

- [feature] **Preview:** Added support for specifying response modalities in `GenerationConfig`
  (only available in the new `firebase-ai` package). This includes support for image generation
  using [specific Gemini models](/docs/vertex-ai/models).

Note: This feature is in Public Preview, which means that it is not subject to any SLA or
deprecation policy and could change in backwards-incompatible ways.
