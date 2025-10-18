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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Configuration parameters to use for content generation.
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
 * @property stopSequences A set of up to 5 `String`s that will stop output generation. If
 * specified, the API will stop at the first appearance of a stop sequence. The stop sequence will
 * not be included as part of the response.
 *
 * @property responseMimeType Output response MIME type of the generated candidate text (IANA
 * standard).
 *
 * Supported MIME types depend on the model used, but could include:
 * - `text/plain`: Text output; the default behavior if unspecified.
 * - `application/json`: JSON response in the candidates.
 *
 * @property responseSchema Output schema of the generated candidate text. If set, a compatible
 * [responseMimeType] must also be set.
 *
 * Compatible MIME types:
 * - `application/json`: Schema for JSON response.
 *
 * Refer to the
 * [Control generated output](https://cloud.google.com/vertex-ai/generative-ai/docs/multimodal/control-generated-output)
 * guide for more details.
 *
 * @property responseModalities The format of data in which the model should respond with.
 */
public class GenerationConfig
private constructor(
  internal val temperature: Float?,
  internal val topK: Int?,
  internal val topP: Float?,
  internal val candidateCount: Int?,
  internal val maxOutputTokens: Int?,
  internal val presencePenalty: Float?,
  internal val frequencyPenalty: Float?,
  internal val stopSequences: List<String>?,
  internal val responseMimeType: String?,
  internal val responseSchema: Schema?,
  internal val responseModalities: List<ResponseModality>?,
  internal val thinkingConfig: ThinkingConfig?,
) {

  /**
   * Builder for creating a [GenerationConfig].
   *
   * Mainly intended for Java interop. Kotlin consumers should use [generationConfig] for a more
   * idiomatic experience.
   *
   * @property temperature See [GenerationConfig.temperature].
   *
   * @property topK See [GenerationConfig.topK].
   *
   * @property topP See [GenerationConfig.topP].
   *
   * @property presencePenalty See [GenerationConfig.presencePenalty]
   *
   * @property frequencyPenalty See [GenerationConfig.frequencyPenalty]
   *
   * @property candidateCount See [GenerationConfig.candidateCount].
   *
   * @property maxOutputTokens See [GenerationConfig.maxOutputTokens].
   *
   * @property stopSequences See [GenerationConfig.stopSequences].
   *
   * @property responseMimeType See [GenerationConfig.responseMimeType].
   *
   * @property responseSchema See [GenerationConfig.responseSchema].
   *
   * @property responseModalities See [GenerationConfig.responseModalities].
   *
   * @see [generationConfig]
   */
  public class Builder {
    @JvmField public var temperature: Float? = null
    @JvmField public var topK: Int? = null
    @JvmField public var topP: Float? = null
    @JvmField public var candidateCount: Int? = null
    @JvmField public var maxOutputTokens: Int? = null
    @JvmField public var presencePenalty: Float? = null
    @JvmField public var frequencyPenalty: Float? = null
    @JvmField public var stopSequences: List<String>? = null
    @JvmField public var responseMimeType: String? = null
    @JvmField public var responseSchema: Schema? = null
    @JvmField public var responseModalities: List<ResponseModality>? = null
    @JvmField public var thinkingConfig: ThinkingConfig? = null

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
    public fun setStopSequences(stopSequences: List<String>?): Builder = apply {
      this.stopSequences = stopSequences
    }
    public fun setResponseMimeType(responseMimeType: String?): Builder = apply {
      this.responseMimeType = responseMimeType
    }
    public fun setResponseSchema(responseSchema: Schema?): Builder = apply {
      this.responseSchema = responseSchema
    }
    public fun setResponseModalities(responseModalities: List<ResponseModality>?): Builder = apply {
      this.responseModalities = responseModalities
    }
    public fun setThinkingConfig(thinkingConfig: ThinkingConfig?): Builder = apply {
      this.thinkingConfig = thinkingConfig
    }

    /** Create a new [GenerationConfig] with the attached arguments. */
    public fun build(): GenerationConfig =
      GenerationConfig(
        temperature = temperature,
        topK = topK,
        topP = topP,
        candidateCount = candidateCount,
        maxOutputTokens = maxOutputTokens,
        stopSequences = stopSequences,
        presencePenalty = presencePenalty,
        frequencyPenalty = frequencyPenalty,
        responseMimeType = responseMimeType,
        responseSchema = responseSchema,
        responseModalities = responseModalities,
        thinkingConfig = thinkingConfig
      )
  }

  internal fun toInternal() =
    Internal(
      temperature = temperature,
      topP = topP,
      topK = topK,
      candidateCount = candidateCount,
      maxOutputTokens = maxOutputTokens,
      stopSequences = stopSequences,
      frequencyPenalty = frequencyPenalty,
      presencePenalty = presencePenalty,
      responseMimeType = responseMimeType,
      responseSchema = responseSchema?.toInternalOpenApi(),
      responseModalities = responseModalities?.map { it.toInternal() },
      thinkingConfig = thinkingConfig?.toInternal()
    )

  @Serializable
  internal data class Internal(
    val temperature: Float?,
    @SerialName("top_p") val topP: Float?,
    @SerialName("top_k") val topK: Int?,
    @SerialName("candidate_count") val candidateCount: Int?,
    @SerialName("max_output_tokens") val maxOutputTokens: Int?,
    @SerialName("stop_sequences") val stopSequences: List<String>?,
    @SerialName("response_mime_type") val responseMimeType: String? = null,
    @SerialName("presence_penalty") val presencePenalty: Float? = null,
    @SerialName("frequency_penalty") val frequencyPenalty: Float? = null,
    @SerialName("response_schema") val responseSchema: Schema.InternalOpenAPI? = null,
    @SerialName("response_modalities") val responseModalities: List<String>? = null,
    @SerialName("thinking_config") val thinkingConfig: ThinkingConfig.Internal? = null
  )

  public companion object {

    /**
     * Alternative casing for [GenerationConfig.Builder]:
     * ```
     * val config = GenerationConfig.builder()
     * ```
     */
    public fun builder(): Builder = Builder()
  }
}

/**
 * Helper method to construct a [GenerationConfig] in a DSL-like manner.
 *
 * Example Usage:
 * ```
 * generationConfig {
 *   temperature = 0.75f
 *   topP = 0.5f
 *   topK = 30
 *   candidateCount = 4
 *   maxOutputTokens = 300
 *   stopSequences = listOf("in conclusion", "-----", "do you need")
 * }
 * ```
 */
public fun generationConfig(init: GenerationConfig.Builder.() -> Unit): GenerationConfig {
  val builder = GenerationConfig.builder()
  builder.init()
  return builder.build()
}
