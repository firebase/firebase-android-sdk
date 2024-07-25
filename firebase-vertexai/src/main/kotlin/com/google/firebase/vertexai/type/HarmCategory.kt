/*
 * Copyright 2023 Google LLC
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

/** Category for a given harm rating. */
enum class HarmCategory {
  /** A new and not yet supported value. */
  UNKNOWN,

  /** Harassment content. */
  HARASSMENT,

  /** Hate speech and content. */
  HATE_SPEECH,

  /** Sexually explicit content. */
  SEXUALLY_EXPLICIT,

  /** Dangerous content. */
  DANGEROUS_CONTENT
}
