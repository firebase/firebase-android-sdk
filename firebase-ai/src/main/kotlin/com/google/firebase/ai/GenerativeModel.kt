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

package com.google.firebase.ai

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.common.APIController
import com.google.firebase.ai.common.AppCheckHeaderProvider
import com.google.firebase.ai.common.CountTokensRequest
import com.google.firebase.ai.common.GenerateContentRequest
import com.google.firebase.ai.hybrid.HybridRouter
import com.google.firebase.ai.ondevice.interop.FirebaseAIOnDeviceGenerativeModelFactory
import com.google.firebase.ai.ondevice.interop.FirebaseAIOnDeviceNotAvailableException
import com.google.firebase.ai.ondevice.interop.GenerateContentRequest as OnDeviceGenerateContentRequest
import com.google.firebase.ai.type.AutoFunctionDeclaration
import com.google.firebase.ai.type.Candidate
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.CountTokensResponse
import com.google.firebase.ai.type.FinishReason
import com.google.firebase.ai.type.FirebaseAIException
import com.google.firebase.ai.type.FirebaseAutoFunctionException
import com.google.firebase.ai.type.FunctionCallPart
import com.google.firebase.ai.type.FunctionResponsePart
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.GenerateObjectResponse
import com.google.firebase.ai.type.GenerationConfig
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.GenerativeBackendEnum
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.InvalidStateException
import com.google.firebase.ai.type.JsonSchema
import com.google.firebase.ai.type.PromptBlockedException
import com.google.firebase.ai.type.RequestOptions
import com.google.firebase.ai.type.ResponseStoppedException
import com.google.firebase.ai.type.SafetySetting
import com.google.firebase.ai.type.SerializationException
import com.google.firebase.ai.type.TextPart
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.ToolConfig
import com.google.firebase.ai.type.content
import com.google.firebase.appcheck.interop.InteropAppCheckTokenProvider
import com.google.firebase.auth.internal.InternalAuthProvider
import kotlin.collections.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Represents a multimodal model (like Gemini), capable of generating content based on various input
 * types.
 */
public class GenerativeModel
internal constructor(
  private val modelName: String,
  private val generationConfig: GenerationConfig? = null,
  private val safetySettings: List<SafetySetting>? = null,
  private val tools: List<Tool>? = null,
  private val toolConfig: ToolConfig? = null,
  private val systemInstruction: Content? = null,
  private val generativeBackend: GenerativeBackend = GenerativeBackend.googleAI(),
  private val onDeviceModel: com.google.firebase.ai.ondevice.interop.GenerativeModel? = null,
  private val onDeviceConfig: OnDeviceConfig,
  internal val controller: APIController,
) {
  internal constructor(
    modelName: String,
    apiKey: String,
    firebaseApp: FirebaseApp,
    useLimitedUseAppCheckTokens: Boolean,
    generationConfig: GenerationConfig? = null,
    safetySettings: List<SafetySetting>? = null,
    tools: List<Tool>? = null,
    toolConfig: ToolConfig? = null,
    systemInstruction: Content? = null,
    requestOptions: RequestOptions = RequestOptions(),
    onDeviceConfig: OnDeviceConfig,
    generativeBackend: GenerativeBackend,
    appCheckTokenProvider: InteropAppCheckTokenProvider? = null,
    onDeviceFactoryProvider: FirebaseAIOnDeviceGenerativeModelFactory? = null,
    internalAuthProvider: InternalAuthProvider? = null
  ) : this(
    modelName = modelName,
    generationConfig = generationConfig,
    safetySettings = safetySettings,
    tools = tools,
    toolConfig = toolConfig,
    systemInstruction = systemInstruction,
    generativeBackend = generativeBackend,
    onDeviceModel = onDeviceFactoryProvider?.newGenerativeModel(),
    onDeviceConfig = onDeviceConfig,
    controller =
      APIController(
        apiKey,
        modelName,
        requestOptions,
        "gl-kotlin/${KotlinVersion.CURRENT}-ai fire/${BuildConfig.VERSION_NAME}",
        firebaseApp,
        AppCheckHeaderProvider(
          TAG,
          useLimitedUseAppCheckTokens,
          appCheckTokenProvider,
          internalAuthProvider
        ),
      ),
  )

  private val hybridRouter = HybridRouter(onDeviceConfig.mode)

  /**
   * Generates new content from the input [Content] given to the model as a prompt.
   *
   * @param prompt The input(s) given to the model as a prompt.
   * @return The content generated by the model.
   * @throws [FirebaseAIException] if the request failed.
   * @see [FirebaseAIException] for types of errors.
   */
  public suspend fun generateContent(
    prompt: Content,
    vararg prompts: Content
  ): GenerateContentResponse = generateContent(listOf(prompt) + prompts.toList())

  /**
   * Generates an object from the input [Content] given to the model as a prompt.
   *
   * @param jsonSchema A schema for the output
   * @param prompt The input(s) given to the model as a prompt.
   * @return The content generated by the model.
   * @throws [FirebaseAIException] if the request failed.
   * @see [FirebaseAIException] for types of errors.
   */
  public suspend fun <T : Any> generateObject(
    jsonSchema: JsonSchema<T>,
    prompt: Content,
    vararg prompts: Content
  ): GenerateObjectResponse<T> {
    if (onDeviceConfig.mode != InferenceMode.ONLY_IN_CLOUD) {
      throw FirebaseAIException.from(
        IllegalArgumentException("On-device mode is not supported for `generateObject`")
      )
    }
    try {
      val config =
        (generationConfig?.toBuilder() ?: GenerationConfig.builder())
          .setResponseSchemaJson(jsonSchema)
          .setResponseMimeType("application/json")
          .build()
      return GenerateObjectResponse(
        controller
          .generateContent(GenerateContentRequest.fromPrompt(config, prompt, *prompts))
          .toPublic()
          .validate(),
        jsonSchema
      )
    } catch (e: Throwable) {
      throw FirebaseAIException.from(e)
    }
  }

  /**
   * Generates new content from the input [Content] given to the model as a prompt.
   *
   * @param prompt The input(s) given to the model as a prompt.
   * @return The content generated by the model.
   * @throws [FirebaseAIException] if the request failed.
   * @see [FirebaseAIException] for types of errors.
   */
  public suspend fun generateContent(prompt: List<Content>): GenerateContentResponse =
    hybridRouter.suspendRoute({ generateContentInCloud(prompt) }) {
      generateContentInOnDevice(prompt)
    }

  /**
   * Generates new content as a stream from the input [Content] given to the model as a prompt.
   *
   * @param prompt The input(s) given to the model as a prompt.
   * @return A [Flow] which will emit responses as they are returned by the model.
   * @throws [FirebaseAIException] if the request failed.
   * @see [FirebaseAIException] for types of errors.
   */
  public fun generateContentStream(
    prompt: Content,
    vararg prompts: Content
  ): Flow<GenerateContentResponse> = generateContentStream(listOf(prompt) + prompts.toList())

  /**
   * Generates new content as a stream from the input [Content] given to the model as a prompt.
   *
   * @param prompt The input(s) given to the model as a prompt.
   * @return A [Flow] which will emit responses as they are returned by the model.
   * @throws [FirebaseAIException] if the request failed.
   * @see [FirebaseAIException] for types of errors.
   */
  public fun generateContentStream(prompt: List<Content>): Flow<GenerateContentResponse> =
    hybridRouter.route(
      inCloudCallback = { generateContentStreamInCloud(prompt) },
      onDeviceCallback = { generateContentStreamInOnDevice(prompt) }
    )

  /**
   * Generates new content from the text input given to the model as a prompt.
   *
   * @param prompt The text to be send to the model as a prompt.
   * @return The content generated by the model.
   * @throws [FirebaseAIException] if the request failed.
   * @see [FirebaseAIException] for types of errors.
   */
  public suspend fun generateContent(prompt: String): GenerateContentResponse =
    generateContent(content { text(prompt) })

  /**
   * Generates an object from the text input given to the model as a prompt.
   *
   * @param jsonSchema A schema for the output
   * @param prompt The text to be send to the model as a prompt.
   * @return The content generated by the model.
   * @throws [FirebaseAIException] if the request failed.
   * @see [FirebaseAIException] for types of errors.
   */
  public suspend fun <T : Any> generateObject(
    jsonSchema: JsonSchema<T>,
    prompt: String
  ): GenerateObjectResponse<T> = generateObject(jsonSchema, content { text(prompt) })

  /**
   * Generates new content as a stream from the text input given to the model as a prompt.
   *
   * @param prompt The text to be send to the model as a prompt.
   * @return A [Flow] which will emit responses as they are returned by the model.
   * @throws [FirebaseAIException] if the request failed.
   * @see [FirebaseAIException] for types of errors.
   */
  public fun generateContentStream(prompt: String): Flow<GenerateContentResponse> =
    generateContentStream(content { text(prompt) })

  /**
   * Generates new content from the image input given to the model as a prompt.
   *
   * @param prompt The image to be converted into a single piece of [Content] to send to the model.
   * @return A [GenerateContentResponse] after some delay.
   * @throws [FirebaseAIException] if the request failed.
   * @see [FirebaseAIException] for types of errors.
   */
  public suspend fun generateContent(prompt: Bitmap): GenerateContentResponse =
    generateContent(content { image(prompt) })

  /**
   * Generates new content as a stream from the image input given to the model as a prompt.
   *
   * @param prompt The image to be converted into a single piece of [Content] to send to the model.
   * @return A [Flow] which will emit responses as they are returned by the model.
   * @throws [FirebaseAIException] if the request failed.
   * @see [FirebaseAIException] for types of errors.
   */
  public fun generateContentStream(prompt: Bitmap): Flow<GenerateContentResponse> =
    generateContentStream(content { image(prompt) })

  /** Creates a [Chat] instance using this model with the optionally provided history. */
  public fun startChat(history: List<Content> = emptyList()): Chat =
    Chat(this, history.toMutableList())

  /**
   * Counts the number of tokens in a prompt using the model's tokenizer.
   *
   * @param prompt The input(s) given to the model as a prompt.
   * @return The [CountTokensResponse] of running the model's tokenizer on the input.
   * @throws [FirebaseAIException] if the request failed.
   * @see [FirebaseAIException] for types of errors.
   */
  public suspend fun countTokens(prompt: Content, vararg prompts: Content): CountTokensResponse =
    countTokens(listOf(prompt) + prompts)

  /**
   * Counts the number of tokens in a prompt using the model's tokenizer.
   *
   * @param prompt The input(s) given to the model as a prompt.
   * @return The [CountTokensResponse] of running the model's tokenizer on the input.
   * @throws [FirebaseAIException] if the request failed.
   * @see [FirebaseAIException] for types of errors.
   */
  public suspend fun countTokens(prompt: List<Content>): CountTokensResponse =
    hybridRouter.suspendRoute(
      inCloudCallback = { countTokensInCloud(prompt) },
      onDeviceCallback = { countTokensInOnDevice(prompt) }
    )

  /**
   * Counts the number of tokens in a text prompt using the model's tokenizer.
   *
   * @param prompt The text given to the model as a prompt.
   * @return The [CountTokensResponse] of running the model's tokenizer on the input.
   * @throws [FirebaseAIException] if the request failed.
   * @see [FirebaseAIException] for types of errors.
   */
  public suspend fun countTokens(prompt: String): CountTokensResponse {
    return countTokens(content { text(prompt) })
  }

  /**
   * Counts the number of tokens in an image prompt using the model's tokenizer.
   *
   * @param prompt The image given to the model as a prompt.
   * @return The [CountTokensResponse] of running the model's tokenizer on the input.
   * @throws [FirebaseAIException] if the request failed.
   * @see [FirebaseAIException] for types of errors.
   */
  public suspend fun countTokens(prompt: Bitmap): CountTokensResponse {
    return countTokens(content { image(prompt) })
  }

  private fun generateContentStreamInCloud(prompt: List<Content>): Flow<GenerateContentResponse> =
    controller
      .generateContentStream(GenerateContentRequest.fromPrompt(prompt))
      .catch { throw FirebaseAIException.from(it) }
      .map { it.toPublic().validate() }

  private fun generateContentStreamInOnDevice(
    prompt: List<Content>
  ): Flow<GenerateContentResponse> {
    if (onDeviceModel == null) {
      throw FirebaseAIException.from(
        FirebaseAIOnDeviceNotAvailableException("On-device model is null")
      )
    }
    val request = buildOnDeviceGenerateContentRequest(prompt)
    return onDeviceModel
      .generateContentStream(request)
      .catch { throw FirebaseAIException.from(it) }
      // TODO: what about `validate` ?
      .map { GenerateContentResponse.fromOnDeviceResponse(it) }
  }

  private suspend fun countTokensInCloud(prompt: List<Content>): CountTokensResponse {
    try {
      return controller
        .countTokens(CountTokensRequest.fromPrompt(*prompt.toTypedArray()))
        .toPublic()
    } catch (e: Throwable) {
      throw FirebaseAIException.from(e)
    }
  }

  private suspend fun countTokensInOnDevice(prompt: List<Content>): CountTokensResponse {
    if (onDeviceModel == null) {
      throw FirebaseAIException.from(
        FirebaseAIOnDeviceNotAvailableException("On-device model is null")
      )
    }
    if (!onDeviceModel.isAvailable()) {
      throw FirebaseAIException.from(
        FirebaseAIOnDeviceNotAvailableException("On-device model is not available")
      )
    }

    val request = buildOnDeviceGenerateContentRequest(prompt)

    return try {
      val response = onDeviceModel.countTokens(request)
      CountTokensResponse(response.totalTokens)
    } catch (e: Throwable) {
      throw FirebaseAIException.from(e)
    }
  }

  private suspend fun generateContentInCloud(prompt: List<Content>): GenerateContentResponse =
    try {
      controller.generateContent(GenerateContentRequest.fromPrompt(prompt)).toPublic().validate()
    } catch (e: Throwable) {
      throw FirebaseAIException.from(e)
    }

  private suspend fun generateContentInOnDevice(prompt: List<Content>): GenerateContentResponse {
    if (onDeviceModel == null) {
      throw FirebaseAIException.from(
        FirebaseAIOnDeviceNotAvailableException("On-device model is null")
      )
    }
    if (!onDeviceModel.isAvailable()) {
      throw FirebaseAIException.from(
        FirebaseAIOnDeviceNotAvailableException("On-device model is not available")
      )
    }

    val request = buildOnDeviceGenerateContentRequest(prompt)

    return try {
      val response = onDeviceModel.generateContent(request)
        // TODO: what about `validate` ?
      GenerateContentResponse.fromOnDeviceResponse(response)
    } catch (e: Throwable) {
      throw FirebaseAIException.from(e)
    }
  }

  internal fun hasFunction(call: FunctionCallPart): Boolean {
    return tools
      ?.flatMap { it.autoFunctionDeclarations?.filterNotNull() ?: emptyList() }
      ?.firstOrNull { it.name == call.name && it.functionReference != null } != null
  }

  @OptIn(InternalSerializationApi::class)
  internal suspend fun executeFunction(call: FunctionCallPart): FunctionResponsePart {
    if (tools == null) {
      throw RuntimeException("No registered tools")
    }
    val tool = tools.flatMap { it.autoFunctionDeclarations?.filterNotNull() ?: emptyList() }
    val declaration =
      tool.firstOrNull() { it.name == call.name }
        ?: throw RuntimeException("No registered function named ${call.name}")
    return executeFunction<Any, Any>(
      call,
      declaration as AutoFunctionDeclaration<Any, Any>,
      JsonObject(call.args).toString()
    )
  }

  @OptIn(InternalSerializationApi::class)
  internal suspend fun <I : Any, O : Any> executeFunction(
    functionCall: FunctionCallPart,
    functionDeclaration: AutoFunctionDeclaration<I, O>,
    parameter: String
  ): FunctionResponsePart {
    val inputDeserializer = functionDeclaration.inputSchema.getSerializer()
    val input = Json.decodeFromString(inputDeserializer, parameter)
    val functionReference =
      functionDeclaration.functionReference
        ?: throw RuntimeException("Function reference for ${functionDeclaration.name} is missing")
    try {
      val output = functionReference.invoke(input)
      val outputSerializer = functionDeclaration.outputSchema?.getSerializer()
      if (outputSerializer != null) {
        return FunctionResponsePart.from(
            Json.encodeToJsonElement(outputSerializer, output).jsonObject
          )
          .normalizeAgainstCall(functionCall)
      }
      return (output as FunctionResponsePart).normalizeAgainstCall(functionCall)
    } catch (e: FirebaseAutoFunctionException) {
      return FunctionResponsePart.from(JsonObject(mapOf("error" to JsonPrimitive(e.message))))
        .normalizeAgainstCall(functionCall)
    }
  }

  internal fun getTurnLimit(): Int = controller.getTurnLimit()

  @OptIn(ExperimentalSerializationApi::class)
  private fun GenerateContentRequest.Companion.fromPrompt(
    overrideConfig: GenerationConfig? = null,
    vararg prompt: Content
  ) =
    GenerateContentRequest(
      modelName,
      prompt.map { it.toInternal() },
      safetySettings
        ?.also { safetySettingList ->
          if (
            generativeBackend.backend == GenerativeBackendEnum.GOOGLE_AI &&
              safetySettingList.any { it.method != null }
          ) {
            throw InvalidStateException(
              "HarmBlockMethod is unsupported by the Google Developer API"
            )
          }
        }
        ?.map { it.toInternal() },
      (overrideConfig ?: generationConfig)?.toInternal(),
      tools?.map { it.toInternal() },
      toolConfig?.toInternal(),
      systemInstruction?.copy(role = "system")?.toInternal(),
    )

  private fun GenerateContentRequest.Companion.fromPrompt(prompt: List<Content>) =
    GenerateContentRequest.fromPrompt(null, *prompt.toTypedArray())

  private fun buildOnDeviceGenerateContentRequest(
    prompt: List<Content>
  ): OnDeviceGenerateContentRequest {
    if (onDeviceModel == null) {
      throw FirebaseAIException.from(
        FirebaseAIOnDeviceNotAvailableException("On-device model is null")
      )
    }

    if (prompt.isEmpty()) {
      throw FirebaseAIException.from(IllegalArgumentException("Prompt is empty"))
    }
    val parts =
      if (prompt.size == 1) {
        prompt.first().parts
      } else {
        Log.w(TAG, "On-device model does not support multiple prompts, concatenating them instead")
        prompt.flatMap { it.parts }
      }
    val textParts =
      parts.filterIsInstance<TextPart>().also {
        if (it.size > 1)
          Log.w(
            TAG,
            "On-device model does not support multiple text parts, concatenating them instead"
          )
      }
    if (textParts.isEmpty()) {
      throw FirebaseAIException.from(
        IllegalArgumentException("On-device model requires text as part of the prompt")
      )
    }
    val text = textParts.joinToString("") { it.text }
    val image =
      parts
        .filterIsInstance<ImagePart>()
        .also {
          if (it.size > 1)
            Log.w(
              TAG,
              "On-device model does not support multiple image parts, using only the first one"
            )
        }
        .firstOrNull()
    return OnDeviceGenerateContentRequest(
      text = com.google.firebase.ai.ondevice.interop.TextPart(text),
      image = image?.let { com.google.firebase.ai.ondevice.interop.ImagePart(it.image) },
      temperature = onDeviceConfig.temperature,
      topK = onDeviceConfig.topK,
      seed = onDeviceConfig.seed,
      candidateCount = 1, // TODO: Add candidate count to config
      maxOutputTokens = onDeviceConfig.maxOutputTokens
    )
  }

  private fun GenerateContentResponse.Companion.fromOnDeviceResponse(
    response: com.google.firebase.ai.ondevice.interop.GenerateContentResponse
  ) =
    GenerateContentResponse(
      response.candidates.map { Candidate.fromInterop(it) },
      InferenceSource.ON_DEVICE,
      null,
      null
    )

  private fun CountTokensRequest.Companion.fromPrompt(vararg prompt: Content) =
    when (generativeBackend.backend) {
      GenerativeBackendEnum.GOOGLE_AI ->
        CountTokensRequest.forGoogleAI(GenerateContentRequest.fromPrompt(null, *prompt))
      GenerativeBackendEnum.VERTEX_AI ->
        CountTokensRequest.forVertexAI(GenerateContentRequest.fromPrompt(null, *prompt))
    }

  private fun GenerateContentResponse.validate() = apply {
    if (candidates.isEmpty() && promptFeedback == null) {
      throw SerializationException("Error deserializing response, found no valid fields")
    }
    promptFeedback?.blockReason?.let { throw PromptBlockedException(this) }
    candidates
      .mapNotNull { it.finishReason }
      .firstOrNull { it != FinishReason.STOP }
      ?.let { throw ResponseStoppedException(this) }
  }

  private companion object {
    private val TAG = GenerativeModel::class.java.simpleName
  }
}
