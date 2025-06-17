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

package com.google.firebase.ai.type

import kotlinx.serialization.Serializable

/**
 * The model's response to a count tokens request.
 *
 * **Important:** The counters in this class do not include billable image, video or other non-text
 * input. See [Vertex AI pricing](https://cloud.google.com/vertex-ai/generative-ai/pricing) for
 * details.
 *
 * @property totalTokens The total number of tokens in the input given to the model as a prompt.
 * @property totalBillableCharacters The total number of billable characters in the text input given
 * to the model as a prompt. **Important:** this property does not include billable image, video or
 * other non-text input. See
 * [Vertex AI pricing](https://cloud.google.com/vertex-ai/generative-ai/pricing) for details.
 * @deprecated This field is deprecated and will be removed in a future version.
 * @property promptTokensDetails The breakdown, by modality, of how many tokens are consumed by the
 * prompt.
 */
public class CountTokensResponse(
  public val totalTokens: Int,
  @Deprecated("This field is deprecated and will be removed in a future version.")
  public val totalBillableCharacters: Int? = null,
  public val promptTokensDetails: List<ModalityTokenCount> = emptyList(),
) {
  public operator fun component1(): Int = totalTokens

  public operator fun component2(): Int? = totalBillableCharacters

  public operator fun component3(): List<ModalityTokenCount>? = promptTokensDetails

  @Serializable
  internal data class Internal(
    val totalTokens: Int,
    val totalBillableCharacters: Int? = null,
    val promptTokensDetails: List<ModalityTokenCount.Internal>? = null
  ) : Response {

    internal fun toPublic(): CountTokensResponse {
      return CountTokensResponse(
        totalTokens,
        totalBillableCharacters ?: 0,
        promptTokensDetails?.map { it.toPublic() } ?: emptyList()
      )
    }
  }
}
