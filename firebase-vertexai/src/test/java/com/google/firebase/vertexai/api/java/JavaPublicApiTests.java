/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
