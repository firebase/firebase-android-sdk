# Unreleased
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
