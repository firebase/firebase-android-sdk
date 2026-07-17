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

import java.util.Objects

/** Represents a reference to a backend for generative AI. */
public class GenerativeBackend
internal constructor(internal val location: String, internal val backend: GenerativeBackendEnum) {
  public companion object {

    /** References the Google Developer API backend. */
    @JvmStatic
    public fun googleAI(): GenerativeBackend =
      GenerativeBackend("", GenerativeBackendEnum.GOOGLE_AI)

    /**
     * References the VertexAI Gemini API backend.
     *
     * @param location passes a valid cloud server location, defaults to "us-central1"
     */
    @Deprecated(
      message =
        "Use agentPlatform instead. Note that agentPlatform defaults to location \"global\" while" +
          " vertexAI defaulted to \"us-central1\", which should be considered when migrating.",
      replaceWith = ReplaceWith("agentPlatform(location)")
    )
    @JvmStatic
    @JvmOverloads
    public fun vertexAI(location: String = "us-central1"): GenerativeBackend {
      if (location.isBlank() || location.contains("/")) {
        throw InvalidLocationException(location)
      }
      return GenerativeBackend(location, GenerativeBackendEnum.VERTEX_AI)
    }

    /**
     * References the Agent Platform Gemini API backend.
     *
     * @param location passes a valid cloud server location, defaults to "global"
     */
    @JvmStatic
    @JvmOverloads
    public fun agentPlatform(location: String = "global"): GenerativeBackend {
      if (location.isBlank() || location.contains("/")) {
        throw InvalidLocationException(location)
      }
      return GenerativeBackend(location, GenerativeBackendEnum.AGENT_PLATFORM)
    }
  }

  override fun equals(other: Any?): Boolean {
    if (other is GenerativeBackend) {
      return when (other.backend) {
        GenerativeBackendEnum.GOOGLE_AI -> {
          other.backend == this.backend
        }
        GenerativeBackendEnum.VERTEX_AI,
        GenerativeBackendEnum.AGENT_PLATFORM -> {
          other.backend == this.backend && other.location == this.location
        }
      }
    }
    return false
  }

  override fun hashCode(): Int = Objects.hash(this.backend, this.location)
}

internal enum class GenerativeBackendEnum {
  GOOGLE_AI,
  VERTEX_AI,
  AGENT_PLATFORM,
}
