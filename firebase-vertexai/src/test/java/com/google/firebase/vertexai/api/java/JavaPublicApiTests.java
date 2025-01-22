package com.google.firebase.vertexai.api.java;

import com.google.firebase.vertexai.type.ApiVersion;
import com.google.firebase.vertexai.type.RequestOptions;

/** Build tests for the Vertex AI in Firebase Java public API surface. */
final class JavaPublicApiTests {
  /** {@link RequestOptions} API */
  void requestOptionsCodeSamples() {
    new RequestOptions();
    new RequestOptions(30_000L);
    new RequestOptions(ApiVersion.V1);
    new RequestOptions(60_000L, ApiVersion.V1BETA);
  }
}
