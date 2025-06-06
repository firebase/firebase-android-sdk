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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Configuration parameters to use for live content generation.
 *
 * @property temperature A parameter controlling the degree of randomness in token selection. A
 * temperature of 0 means that the highest probability tokens are always selected. In this case,
 * responses for a given prompt are mostly deterministic, but a small amount of variation is still
 * possible.
 *
 * @property topK The `topK` parameter changes how the model selects tokens for output. A `topK` of
 * 1 means the selected token is the most probable among all the tokens in the model's vocabulary,
 * while a `topK` of 3 means that the next token is selected from among the 3 most probable using
 * the `temperature`. For each token selection step, the `topK` tokens with the highest
 * probabilities are sampled. Tokens are then further filtered based on `topP` with the final token
 * selected using `temperature` sampling. Defaults to 40 if unspecified.
 *
 * @property topP The `topP` parameter changes how the model selects tokens for output. Tokens are
 * selected from the most to least probable until the sum of their probabilities equals the `topP`
 * value. For example, if tokens A, B, and C have probabilities of 0.3, 0.2, and 0.1 respectively
 * and the topP value is 0.5, then the model will select either A or B as the next token by using
 * the `temperature` and exclude C as a candidate. Defaults to 0.95 if unset.
 *
 * @property candidateCount The maximum number of generated response messages to return. This value
 * must be between [1, 8], inclusive. If unset, this will default to 1.
 *
 * - Note: Only unique candidates are returned. Higher temperatures are more likely to produce
 * unique candidates. Setting `temperature` to 0 will always produce exactly one candidate
 * regardless of the `candidateCount`.
 *
 * @property presencePenalty Positive penalties.
 *
 * @property frequencyPenalty Frequency penalties.
 *
 * @property maxOutputTokens Specifies the maximum number of tokens that can be generated in the
 * response. The number of tokens per word varies depending on the language outputted. Defaults to 0
 * (unbounded).
 *
 * @property responseModality Specifies the format of the data in which the server responds to
 * requests
 *
 * @property speechConfig Specifies the voice configuration of the audio response from the server.
 *
 * Refer to the
 * [Control generated output](https://cloud.google.com/vertex-ai/generative-ai/docs/multimodal/control-generated-output)
 * guide for more details.
 */
@PublicPreviewAPI
@Deprecated(
  """The Vertex AI in Firebase SDK (firebase-vertexai) has been replaced with the FirebaseAI SDK (firebase-ai) to accommodate the evolving set of supported features and services.
For migration details, see the migration guide: https://firebase.google.com/docs/vertex-ai/migrate-to-latest-sdk"""
)
public class LiveGenerationConfig
private constructor(
  internal val temperature: Float?,
  internal val topK: Int?,
  internal val topP: Float?,
  internal val candidateCount: Int?,
  internal val maxOutputTokens: Int?,
  internal val presencePenalty: Float?,
  internal val frequencyPenalty: Float?,
  internal val responseModality: ResponseModality?,
  internal val speechConfig: SpeechConfig?
) {

  /**
   * Builder for creating a [LiveGenerationConfig].
   *
   * Mainly intended for Java interop. Kotlin consumers should use [liveGenerationConfig] for a more
   * idiomatic experience.
   *
   * @property temperature See [LiveGenerationConfig.temperature].
   *
   * @property topK See [LiveGenerationConfig.topK].
   *
   * @property topP See [LiveGenerationConfig.topP].
   *
   * @property presencePenalty See [LiveGenerationConfig.presencePenalty]
   *
   * @property frequencyPenalty See [LiveGenerationConfig.frequencyPenalty]
   *
   * @property candidateCount See [LiveGenerationConfig.candidateCount].
   *
   * @property maxOutputTokens See [LiveGenerationConfig.maxOutputTokens].
   *
   * @property responseModality See [LiveGenerationConfig.responseModality]
   *
   * @property speechConfig See [LiveGenerationConfig.speechConfig]
   */
  @Deprecated(
    """The Vertex AI in Firebase SDK (firebase-vertexai) has been replaced with the FirebaseAI SDK (firebase-ai) to accommodate the evolving set of supported features and services.
For migration details, see the migration guide: https://firebase.google.com/docs/vertex-ai/migrate-to-latest-sdk"""
  )
  public class Builder {
    @JvmField public var temperature: Float? = null
    @JvmField public var topK: Int? = null
    @JvmField public var topP: Float? = null
    @JvmField public var candidateCount: Int? = null
    @JvmField public var maxOutputTokens: Int? = null
    @JvmField public var presencePenalty: Float? = null
    @JvmField public var frequencyPenalty: Float? = null
    @JvmField public var responseModality: ResponseModality? = null
    @JvmField public var speechConfig: SpeechConfig? = null

    public fun setTemperature(temperature: Float?): Builder = apply {
      this.temperature = temperature
    }
    public fun setTopK(topK: Int?): Builder = apply { this.topK = topK }
    public fun setTopP(topP: Float?): Builder = apply { this.topP = topP }
    public fun setCandidateCount(candidateCount: Int?): Builder = apply {
      this.candidateCount = candidateCount
    }
    public fun setMaxOutputTokens(maxOutputTokens: Int?): Builder = apply {
      this.maxOutputTokens = maxOutputTokens
    }
    public fun setPresencePenalty(presencePenalty: Float?): Builder = apply {
      this.presencePenalty = presencePenalty
    }
    public fun setFrequencyPenalty(frequencyPenalty: Float?): Builder = apply {
      this.frequencyPenalty = frequencyPenalty
    }
    public fun setResponseModality(responseModality: ResponseModality?): Builder = apply {
      this.responseModality = responseModality
    }
    public fun setSpeechConfig(speechConfig: SpeechConfig?): Builder = apply {
      this.speechConfig = speechConfig
    }

    /** Create a new [LiveGenerationConfig] with the attached arguments. */
    public fun build(): LiveGenerationConfig =
      LiveGenerationConfig(
        temperature = temperature,
        topK = topK,
        topP = topP,
        candidateCount = candidateCount,
        maxOutputTokens = maxOutputTokens,
        presencePenalty = presencePenalty,
        frequencyPenalty = frequencyPenalty,
        speechConfig = speechConfig,
        responseModality = responseModality
      )
  }

  internal fun toInternal(): Internal {
    return Internal(
      temperature = temperature,
      topP = topP,
      topK = topK,
      candidateCount = candidateCount,
      maxOutputTokens = maxOutputTokens,
      frequencyPenalty = frequencyPenalty,
      presencePenalty = presencePenalty,
      speechConfig = speechConfig?.toInternal(),
      responseModalities =
        if (responseModality != null) listOf(responseModality.toInternal()) else null
    )
  }

  @Serializable
  internal data class Internal(
    val temperature: Float?,
    @SerialName("top_p") val topP: Float?,
    @SerialName("top_k") val topK: Int?,
    @SerialName("candidate_count") val candidateCount: Int?,
    @SerialName("max_output_tokens") val maxOutputTokens: Int?,
    @SerialName("presence_penalty") val presencePenalty: Float? = null,
    @SerialName("frequency_penalty") val frequencyPenalty: Float? = null,
    @SerialName("speech_config") val speechConfig: SpeechConfig.Internal? = null,
    @SerialName("response_modalities") val responseModalities: List<String>? = null
  )

  public companion object {

    /**
     * Alternative casing for [LiveGenerationConfig.Builder]:
     * ```
     * val config = LiveGenerationConfig.builder()
     * ```
     */
    public fun builder(): Builder = Builder()
  }
}

/**
 * Helper method to construct a [LiveGenerationConfig] in a DSL-like manner.
 *
 * Example Usage:
 * ```
 * liveGenerationConfig {
 *   temperature = 0.75f
 *   topP = 0.5f
 *   topK = 30
 *   candidateCount = 4
 *   maxOutputTokens = 300
 *   ...
 * }
 * ```
 */
@OptIn(PublicPreviewAPI::class)
public fun liveGenerationConfig(
  init: LiveGenerationConfig.Builder.() -> Unit
): LiveGenerationConfig {
  val builder = LiveGenerationConfig.builder()
  builder.init()
  return builder.build()
}
