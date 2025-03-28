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

/* Represents the response from the server. */
@PublicPreviewAPI
public class LiveContentResponse
internal constructor(
  public val data: Content?,
  public val status: Status,
  public val functionCalls: List<FunctionCallPart>?
) {
  /**
   * Convenience field representing all the text parts in the response as a single string, if they
   * exists.
   */
  public val text: String? =
    data?.parts?.filterIsInstance<TextPart>()?.joinToString(" ") { it.text }

  @JvmInline
  public value class Status private constructor(private val value: Int) {
    public companion object {
      public val NORMAL: Status = Status(0)
      public val INTERRUPTED: Status = Status(1)
      public val TURN_COMPLETE: Status = Status(2)
    }
  }
}
