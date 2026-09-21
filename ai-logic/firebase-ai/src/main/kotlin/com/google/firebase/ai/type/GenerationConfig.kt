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
 * Configuration parameters to use for content generation.
 *
 * @property temperature *Deprecated. Unsupported in Gemini 3.x and later models.*
 *
 * @property topK *Deprecated. Unsupported in Gemini 3.x and later models.*
 *
 * @property topP *Deprecated. Unsupported in Gemini 3.x and later models.*
 *
 * @property candidateCount *Deprecated. Unsupported in Gemini 3.x and later models.*
 *
 * @property presencePenalty *Deprecated. Unsupported in Gemini 3.x and later models.*
 *
 * @property frequencyPenalty *Deprecated. Unsupported in Gemini 3.x and later models.*
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
 * [responseMimeType] must also be set. This is mutually exclusive with [responseJsonSchema]. Unlike
 * [responseJsonSchema] this will encode to an OpenAPI schema.
 *
 * @property responseJsonSchema Output schema of the generated candidate text. If set, a compatible
 * [responseMimeType] must also be set. This is mutually exclusive with [responseSchema]. Unlike
 * [responseSchema] this will encode to an JsonSchema schema, which is the standard moving forward.
 *
 * Compatible MIME types:
 * - `application/json`: Schema for JSON response.
 *
 * Refer to the
 * [Control generated output](https://cloud.google.com/vertex-ai/generative-ai/docs/multimodal/control-generated-output)
 * guide for more details.
 *
 * @property responseModalities The format of data in which the model should respond with.
 * @property imageConfig Configuration for the aspect ratio and size of generated images.
 * @property speechConfig The configuration for controlling the voice of the model during
 * conversation.
 */
@OptIn(PublicPreviewAPI::class)
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
  internal val responseJsonSchema: JsonSchema<*>?,
  internal val responseModalities: List<ResponseModality>?,
  internal val thinkingConfig: ThinkingConfig?,
  internal val imageConfig: ImageConfig?,
  internal val speechConfig: SpeechConfig?,
) {

  /**
   * Builder for creating a [GenerationConfig].
   *
   * Mainly intended for Java interop. Kotlin consumers should use [generationConfig] for a more
   * idiomatic experience.
   *
   * @property temperature *Deprecated.* See [GenerationConfig.temperature].
   *
   * @property topK *Deprecated.* See [GenerationConfig.topK].
   *
   * @property topP *Deprecated.* See [GenerationConfig.topP].
   *
   * @property presencePenalty *Deprecated.* See [GenerationConfig.presencePenalty]
   *
   * @property frequencyPenalty *Deprecated.* See [GenerationConfig.frequencyPenalty]
   *
   * @property candidateCount *Deprecated.* See [GenerationConfig.candidateCount].
   *
   * @property maxOutputTokens See [GenerationConfig.maxOutputTokens].
   *
   * @property stopSequences See [GenerationConfig.stopSequences].
   *
   * @property responseMimeType See [GenerationConfig.responseMimeType].
   *
   * @property responseSchema See [GenerationConfig.responseSchema].
   *
   * @property responseJsonSchema See [GenerationConfig.responseJsonSchema]
   *
   * @property responseModalities See [GenerationConfig.responseModalities].
   *
   * @property imageConfig See [GenerationConfig.imageConfig].
   *
   * @property speechConfig See [GenerationConfig.speechConfig].
   *
   * @see [generationConfig]
   */
  public class Builder {
    @Deprecated("`temperature` is unsupported in Gemini 3.x and later models")
    @JvmField
    public var temperature: Float? = null
    @Deprecated("`topK` is unsupported in Gemini 3.x and later models")
    @JvmField
    public var topK: Int? = null
    @Deprecated("`topP` is unsupported in Gemini 3.x and later models")
    @JvmField
    public var topP: Float? = null
    @Deprecated("`candidateCount` is unsupported in Gemini 3.x and later models")
    @JvmField
    public var candidateCount: Int? = null
    @JvmField public var maxOutputTokens: Int? = null
    @Deprecated("`presencePenalty` is unsupported in Gemini 3.x and later models")
    @JvmField
    public var presencePenalty: Float? = null
    @Deprecated("`frequencyPenalty` is unsupported in Gemini 3.x and later models")
    @JvmField
    public var frequencyPenalty: Float? = null
    @JvmField public var stopSequences: List<String>? = null
    @JvmField public var responseMimeType: String? = null
    @JvmField public var responseSchema: Schema? = null
    @JvmField public var responseJsonSchema: JsonSchema<*>? = null
    @JvmField public var responseModalities: List<ResponseModality>? = null
    @JvmField public var thinkingConfig: ThinkingConfig? = null
    @JvmField public var imageConfig: ImageConfig? = null
    @JvmField public var speechConfig: SpeechConfig? = null

    public constructor()

    internal constructor(
      temperature: Float?,
      topK: Int?,
      topP: Float?,
      candidateCount: Int?,
      maxOutputTokens: Int?,
      presencePenalty: Float?,
      frequencyPenalty: Float?,
      stopSequences: List<String>?,
      responseMimeType: String?,
      responseSchema: Schema?,
      responseJsonSchema: JsonSchema<*>?,
      responseModalities: List<ResponseModality>?,
      thinkingConfig: ThinkingConfig?,
      imageConfig: ImageConfig?,
      speechConfig: SpeechConfig?,
    ) {
      this.temperature = temperature
      this.topK = topK
      this.topP = topP
      this.candidateCount = candidateCount
      this.maxOutputTokens = maxOutputTokens
      this.stopSequences = stopSequences
      this.presencePenalty = presencePenalty
      this.frequencyPenalty = frequencyPenalty
      this.responseMimeType = responseMimeType
      this.responseSchema = responseSchema
      this.responseJsonSchema = responseJsonSchema
      this.responseModalities = responseModalities
      this.thinkingConfig = thinkingConfig
      this.imageConfig = imageConfig
      this.speechConfig = speechConfig
    }

    @Deprecated("`temperature` is unsupported in Gemini 3.x and later models")
    public fun setTemperature(temperature: Float?): Builder = apply {
      this.temperature = temperature
    }

    @Deprecated("`topK` is unsupported in Gemini 3.x and later models")
    public fun setTopK(topK: Int?): Builder = apply { this.topK = topK }

    @Deprecated("`topP` is unsupported in Gemini 3.x and later models")
    public fun setTopP(topP: Float?): Builder = apply { this.topP = topP }

    @Deprecated("`candidateCount` is unsupported in Gemini 3.x and later models")
    public fun setCandidateCount(candidateCount: Int?): Builder = apply {
      this.candidateCount = candidateCount
    }

    public fun setMaxOutputTokens(maxOutputTokens: Int?): Builder = apply {
      this.maxOutputTokens = maxOutputTokens
    }

    @Deprecated("`presencePenalty` is unsupported in Gemini 3.x and later models")
    public fun setPresencePenalty(presencePenalty: Float?): Builder = apply {
      this.presencePenalty = presencePenalty
    }

    @Deprecated("`frequencyPenalty` is unsupported in Gemini 3.x and later models")
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
    public fun setResponseSchemaJson(responseSchemaJson: JsonSchema<*>?): Builder = apply {
      this.responseJsonSchema = responseSchemaJson
    }
    public fun setResponseModalities(responseModalities: List<ResponseModality>?): Builder = apply {
      this.responseModalities = responseModalities
    }
    public fun setThinkingConfig(thinkingConfig: ThinkingConfig?): Builder = apply {
      this.thinkingConfig = thinkingConfig
    }
    public fun setImageConfig(imageConfig: ImageConfig?): Builder = apply {
      this.imageConfig = imageConfig
    }
    public fun setSpeechConfig(speechConfig: SpeechConfig?): Builder = apply {
      this.speechConfig = speechConfig
    }

    /** Create a new [GenerationConfig] with the attached arguments. */
    public fun build(): GenerationConfig {
      if (responseSchema != null && responseJsonSchema != null) {
        throw InvalidStateException("responseSchema and responseJsonSchema are mutually exclusive.")
      }
      return GenerationConfig(
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
        responseJsonSchema = responseJsonSchema,
        responseModalities = responseModalities,
        thinkingConfig = thinkingConfig,
        imageConfig = imageConfig,
        speechConfig = speechConfig,
      )
    }
  }

  public fun toBuilder(): Builder =
    Builder(
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
      responseJsonSchema = responseJsonSchema,
      responseModalities = responseModalities,
      thinkingConfig = thinkingConfig,
      imageConfig = imageConfig,
      speechConfig = speechConfig,
    )

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
      responseJsonSchema = responseJsonSchema?.toInternalJson(),
      responseModalities = responseModalities?.map { it.toInternal() },
      thinkingConfig = thinkingConfig?.toInternal(),
      imageConfig = imageConfig?.toInternal(),
      speechConfig = speechConfig?.toInternal(),
    )

  @Serializable
  internal data class Internal(
    val temperature: Float?,
    val topP: Float?,
    val topK: Int?,
    val candidateCount: Int?,
    val maxOutputTokens: Int?,
    val stopSequences: List<String>?,
    val responseMimeType: String? = null,
    val presencePenalty: Float? = null,
    val frequencyPenalty: Float? = null,
    val responseSchema: Schema.InternalOpenAPI? = null,
    val responseJsonSchema: Schema.InternalJson? = null,
    val responseModalities: List<String>? = null,
    val thinkingConfig: ThinkingConfig.Internal? = null,
    val imageConfig: ImageConfig.Internal? = null,
    val speechConfig: SpeechConfig.Internal? = null,
  )

  public companion object {

    /**
     * Alternative casing for [GenerationConfig.Builder]:
     * ```
     * val config = GenerationConfig.builder()
     * ```
     */
    @JvmStatic public fun builder(): Builder = Builder()
  }
}

/**
 * Helper method to construct a [GenerationConfig] in a DSL-like manner.
 *
 * Example Usage:
 * ```
 * generationConfig {
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
