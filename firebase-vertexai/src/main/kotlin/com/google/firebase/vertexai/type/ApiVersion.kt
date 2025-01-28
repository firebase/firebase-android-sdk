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

package com.google.firebase.vertexai.type

/** Versions of the Vertex AI in Firebase server API. */
public class ApiVersion private constructor(internal val value: String) {
  public companion object {
    /** The stable channel for version 1 of the API. */
    @JvmField public val V1: ApiVersion = ApiVersion("v1")

    /** The beta channel for version 1 of the API. */
    @JvmField public val V1BETA: ApiVersion = ApiVersion("v1beta")
  }
}
