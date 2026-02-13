/*
 * Copyright 2024 Google LLC
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

import kotlinx.serialization.Serializable

/**
 * Usage metadata about response(s).
 *
 * @param promptTokenCount Number of tokens in the request.
 * @param candidatesTokenCount Number of tokens in the response(s).
 * @param totalTokenCount Total number of tokens.
 * @param promptTokensDetails The breakdown, by modality, of how many tokens are consumed by the
 * prompt.
 * @param candidatesTokensDetails The breakdown, by modality, of how many tokens are consumed by the
 * candidates.
 * @param toolUsePromptTokensDetails The breakdown, by modality, of how many tokens are consumed by
 * tools.
 * @param thoughtsTokenCount The number of tokens used by the model's internal "thinking" process.
 * @param toolUsePromptTokenCount The number of tokens used by tools.
 */
public class UsageMetadata
internal constructor(
  public val promptTokenCount: Int,
  public val candidatesTokenCount: Int?,
  public val totalTokenCount: Int,
  public val cachedContentTokenCount: Int,
  public val promptTokensDetails: List<ModalityTokenCount>,
  public val candidatesTokensDetails: List<ModalityTokenCount>,
  public val cacheTokensDetails: List<ModalityTokenCount>,
  public val thoughtsTokenCount: Int,
  public val toolUsePromptTokenCount: Int,
  public val toolUsePromptTokensDetails: List<ModalityTokenCount>
) {

  @Deprecated("Not intended for public use")
  public constructor(
    promptTokenCount: Int,
    candidatesTokenCount: Int?,
    totalTokenCount: Int,
    promptTokensDetails: List<ModalityTokenCount>,
    candidatesTokensDetails: List<ModalityTokenCount>,
    thoughtsTokenCount: Int
  ) : this(
    promptTokenCount,
    candidatesTokenCount,
    totalTokenCount,
    0,
    promptTokensDetails,
    candidatesTokensDetails,
    emptyList(),
    thoughtsTokenCount,
    0,
    emptyList()
  )

  @Serializable
  internal data class Internal(
    val promptTokenCount: Int? = null,
    val candidatesTokenCount: Int? = null,
    val totalTokenCount: Int? = null,
    val cachedContentTokenCount: Int? = null,
    val promptTokensDetails: List<ModalityTokenCount.Internal>? = null,
    val candidatesTokensDetails: List<ModalityTokenCount.Internal>? = null,
    val cacheTokensDetails: List<ModalityTokenCount.Internal>? = null,
    val thoughtsTokenCount: Int? = null,
    val toolUsePromptTokenCount: Int? = null,
    val toolUsePromptTokensDetails: List<ModalityTokenCount.Internal>? = null,
  ) {

    internal fun toPublic(): UsageMetadata =
      UsageMetadata(
        promptTokenCount ?: 0,
        candidatesTokenCount ?: 0,
        totalTokenCount ?: 0,
        cachedContentTokenCount ?: 0,
        promptTokensDetails = promptTokensDetails?.map { it.toPublic() } ?: emptyList(),
        candidatesTokensDetails = candidatesTokensDetails?.map { it.toPublic() } ?: emptyList(),
        cacheTokensDetails = cacheTokensDetails?.map { it.toPublic() } ?: emptyList(),
        thoughtsTokenCount ?: 0,
        toolUsePromptTokenCount ?: 0,
        toolUsePromptTokensDetails = toolUsePromptTokensDetails?.map { it.toPublic() }
            ?: emptyList(),
      )
  }
}
