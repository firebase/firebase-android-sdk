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
public class HarmCategory private constructor(public val ordinal: Int) {
  public companion object {
    /** A new and not yet supported value. */
    @JvmField public val UNKNOWN: HarmCategory = HarmCategory(0)

    /** Harassment content. */
    @JvmField public val HARASSMENT: HarmCategory = HarmCategory(1)

    /** Hate speech and content. */
    @JvmField public val HATE_SPEECH: HarmCategory = HarmCategory(2)

    /** Sexually explicit content. */
    @JvmField public val SEXUALLY_EXPLICIT: HarmCategory = HarmCategory(3)

    /** Dangerous content. */
    @JvmField public val DANGEROUS_CONTENT: HarmCategory = HarmCategory(4)
  }
}
