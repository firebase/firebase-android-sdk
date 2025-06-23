# Unreleased

* [feature] Add support for Grounding with Google Search (#7042).
* [fixed] Fixed `FirebaseAI.getInstance` StackOverflowException (#6971)
* [fixed] Fixed an issue that was causing the SDK to send empty `FunctionDeclaration` descriptions to the API. 

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

