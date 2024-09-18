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
 */
class GenerationConfig
private constructor(
  val temperature: Float?,
  val topK: Int?,
  val topP: Float?,
  val candidateCount: Int?,
  val maxOutputTokens: Int?,
  val stopSequences: List<String>?,
  val responseMimeType: String?,
  val responseSchema: Schema<*>? = null,
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
   * @property candidateCount See [GenerationConfig.candidateCount].
   *
   * @property maxOutputTokens See [GenerationConfig.maxOutputTokens].
   *
   * @property stopSequences See [GenerationConfig.stopSequences].
   *
   * @property responseMimeType See [GenerationConfig.responseMimeType].
   *
   * @property responseSchema See [GenerationConfig.responseSchema].
   * @see [generationConfig]
   */
  class Builder {
    @JvmField var temperature: Float? = null
    @JvmField var topK: Int? = null
    @JvmField var topP: Float? = null
    @JvmField var candidateCount: Int? = null
    @JvmField var maxOutputTokens: Int? = null
    @JvmField var stopSequences: List<String>? = null
    @JvmField var responseMimeType: String? = null
    @JvmField var responseSchema: Schema<*>? = null

    /** Create a new [GenerationConfig] with the attached arguments. */
    fun build() =
      GenerationConfig(
        temperature = temperature,
        topK = topK,
        topP = topP,
        candidateCount = candidateCount,
        maxOutputTokens = maxOutputTokens,
        stopSequences = stopSequences,
        responseMimeType = responseMimeType,
        responseSchema = responseSchema,
      )
  }

  companion object {

    /**
     * Alternative casing for [GenerationConfig.Builder]:
     * ```
     * val config = GenerationConfig.builder()
     * ```
     */
    fun builder() = Builder()
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
fun generationConfig(init: GenerationConfig.Builder.() -> Unit): GenerationConfig {
  val builder = GenerationConfig.builder()
  builder.init()
  return builder.build()
}
