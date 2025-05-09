# Unreleased
* [feature] Initial release of the Firebase AI SDK (`firebase-ai`). This SDK *replaces* the previous
 Vertex AI in Firebase SDK (`firebase-vertexai`) to accommodate the evolving set of supported
 features and services.
  * The new Firebase AI SDK provides **public preview** support for the Gemini Developer API, including its free tier offering.
  * Using the Firebase AI SDK with the Vertex AI Gemini API is still generally available (GA).
* [feature] **Public Preview:** Added support for specifying response modalities in GenerationConfig
 (only available in the new `firebase-ai` package). This includes support for image generation using
 specific Gemini models; for details, see https://firebase.google.com/docs/vertex-ai/models.

 Note: This feature is in Public Preview and relies on experimental models, which means that it is not subject to any SLA or
 deprecation policy and could change in backwards-incompatible ways.

