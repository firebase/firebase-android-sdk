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

/**
 * Represents the response from the model for live content updates.
 *
 * This class encapsulates the content data, the status of the response, and any function calls
 * included in the response.
 */
@PublicPreviewAPI
@Deprecated(
  """The Firebase VertexAI SDK (firebase-vertexai) has been replaced with the FirebaseAI SDK (firebase-ai) to accommodate the evolving set of supported features and services.
For migration details, see the migration guide: https://firebase.google.com/docs/vertex-ai/migrate-to-latest-sdk"""
)
public class LiveContentResponse
internal constructor(

  /** The main content data of the response. This can be `null` if there is no content. */
  public val data: Content?,

  /**
   * The model was interrupted while generating data.
   *
   * An interruption occurs when the client sends a message while the model is actively sending
   * data.
   */
  public val interrupted: Boolean?,

  /**
   * The model has finished sending data in the current interaction.
   *
   * Can be set alongside content, signifying that the content is the last in the turn.
   */
  public val turnComplete: Boolean?,

  /**
   * A list of [FunctionCallPart] included in the response, if any.
   *
   * This list can be null or empty if no function calls are present.
   */
  public val functionCalls: List<FunctionCallPart>?
) {

  /**
   * Convenience field representing all the text parts in the response as a single string, if they
   * exists.
   */
  public val text: String? =
    data?.parts?.filterIsInstance<TextPart>()?.joinToString(" ") { it.text }
}
