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

package com.google.firebase.ai.type

/** Represents a reference to a backend for generative AI. */
public class GenerativeBackend
internal constructor(internal val location: String, internal val backend: GenerativeBackendEnum) {
  public companion object {

    /** References the Google Developer API backend. */
    @JvmStatic
    public fun googleAI(): GenerativeBackend =
      GenerativeBackend("", GenerativeBackendEnum.GOOGLE_AI)

    /**
     * References the VertexAI Enterprise backend.
     *
     * @param location passes a valid cloud server location, defaults to "us-central1"
     */
    @JvmStatic
    @JvmOverloads
    public fun vertexAI(location: String = "us-central1"): GenerativeBackend {
      if (location.isBlank() || location.contains("/")) {
        throw InvalidLocationException(location)
      }
      return GenerativeBackend(location, GenerativeBackendEnum.VERTEX_AI)
    }
  }
}

internal enum class GenerativeBackendEnum {
  GOOGLE_AI,
  VERTEX_AI,
}
