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

package com.google.firebase.vertexai

import android.graphics.Bitmap
import android.util.Log
import com.google.ai.client.generativeai.common.APIController
import com.google.ai.client.generativeai.common.CountTokensRequest
import com.google.ai.client.generativeai.common.GenerateContentRequest
import com.google.ai.client.generativeai.common.HeaderProvider
import com.google.firebase.appcheck.interop.InteropAppCheckTokenProvider
import com.google.firebase.auth.internal.InternalAuthProvider
import com.google.firebase.vertexai.internal.util.toInternal
import com.google.firebase.vertexai.internal.util.toPublic
import com.google.firebase.vertexai.type.Content
import com.google.firebase.vertexai.type.CountTokensResponse
import com.google.firebase.vertexai.type.FinishReason
import com.google.firebase.vertexai.type.FirebaseVertexAIException
import com.google.firebase.vertexai.type.GenerateContentResponse
import com.google.firebase.vertexai.type.GenerationConfig
import com.google.firebase.vertexai.type.PromptBlockedException
import com.google.firebase.vertexai.type.RequestOptions
import com.google.firebase.vertexai.type.ResponseStoppedException
import com.google.firebase.vertexai.type.SafetySetting
import com.google.firebase.vertexai.type.SerializationException
import com.google.firebase.vertexai.type.Tool
import com.google.firebase.vertexai.type.ToolConfig
import com.google.firebase.vertexai.type.content
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * A controller for communicating with the API of a given multimodal model (for example, Gemini).
 */
class GenerativeModel
internal constructor(
  val modelName: String,
  val generationConfig: GenerationConfig? = null,
  val safetySettings: List<SafetySetting>? = null,
  val tools: List<Tool>? = null,
  val toolConfig: ToolConfig? = null,
  val systemInstruction: Content? = null,
  private val controller: APIController
) {

  @JvmOverloads
  internal constructor(
    modelName: String,
    apiKey: String,
    generationConfig: GenerationConfig? = null,
    safetySettings: List<SafetySetting>? = null,
    tools: List<Tool>? = null,
    toolConfig: ToolConfig? = null,
    systemInstruction: Content? = null,
    requestOptions: RequestOptions = RequestOptions(),
    appCheckTokenProvider: InteropAppCheckTokenProvider? = null,
    internalAuthProvider: InternalAuthProvider? = null,
  ) : this(
    modelName,
    generationConfig,
    safetySettings,
    tools,
    toolConfig,
    systemInstruction,
    APIController(
      apiKey,
      modelName,
      requestOptions.toInternal(),
      "gl-kotlin/${KotlinVersion.CURRENT} fire/${BuildConfig.VERSION_NAME}",
      object : HeaderProvider {
        override val timeout: Duration
          get() = 10.seconds

        override suspend fun generateHeaders(): Map<String, String> {
          val headers = mutableMapOf<String, String>()
          if (appCheckTokenProvider == null) {
            Log.w(TAG, "AppCheck not registered, skipping")
          } else {
            val token = appCheckTokenProvider.getToken(false).await()

            if (token.error != null) {
              Log.w(TAG, "Error obtaining AppCheck token", token.error)
            } else {
              headers["X-Firebase-AppCheck"] = token.token
            }
          }

          if (internalAuthProvider == null) {
            Log.w(TAG, "Auth not registered, skipping")
          } else {
            try {
              val token = internalAuthProvider.getAccessToken(false).await()

              headers["Authorization"] = token.token!!
            } catch (e: Exception) {
              Log.w(TAG, "Error getting Auth token ", e)
            }
          }

          return headers
        }
      }
    )
  )

  /**
   * Generates a [GenerateContentResponse] from the backend with the provided [Content].
   *
   * @param prompt [Content] to send to the model.
   * @return A [GenerateContentResponse]. Function should be called within a suspend context to
   * properly manage concurrency.
   */
  suspend fun generateContent(vararg prompt: Content): GenerateContentResponse =
    try {
      controller.generateContent(constructRequest(*prompt)).toPublic().validate()
    } catch (e: Throwable) {
      throw FirebaseVertexAIException.from(e)
    }

  /**
   * Generates a streaming response from the backend with the provided [Content].
   *
   * @param prompt [Content] to send to the model.
   * @return A [Flow] which will emit responses as they are returned from the model.
   */
  fun generateContentStream(vararg prompt: Content): Flow<GenerateContentResponse> =
    controller
      .generateContentStream(constructRequest(*prompt))
      .catch { throw FirebaseVertexAIException.from(it) }
      .map { it.toPublic().validate() }

  /**
   * Generates a [GenerateContentResponse] from the backend with the provided text prompt.
   *
   * @param prompt The text to be converted into a single piece of [Content] to send to the model.
   * @return A [GenerateContentResponse] after some delay. Function should be called within a
   * suspend context to properly manage concurrency.
   */
  suspend fun generateContent(prompt: String): GenerateContentResponse =
    generateContent(content { text(prompt) })

  /**
   * Generates a streaming response from the backend with the provided text prompt.
   *
   * @param prompt The text to be converted into a single piece of [Content] to send to the model.
   * @return A [Flow] which will emit responses as they are returned from the model.
   */
  fun generateContentStream(prompt: String): Flow<GenerateContentResponse> =
    generateContentStream(content { text(prompt) })

  /**
   * Generates a [GenerateContentResponse] from the backend with the provided image prompt.
   *
   * @param prompt The image to be converted into a single piece of [Content] to send to the model.
   * @return A [GenerateContentResponse] after some delay. Function should be called within a
   * suspend context to properly manage concurrency.
   */
  suspend fun generateContent(prompt: Bitmap): GenerateContentResponse =
    generateContent(content { image(prompt) })

  /**
   * Generates a streaming response from the backend with the provided image prompt.
   *
   * @param prompt The image to be converted into a single piece of [Content] to send to the model.
   * @return A [Flow] which will emit responses as they are returned from the model.
   */
  fun generateContentStream(prompt: Bitmap): Flow<GenerateContentResponse> =
    generateContentStream(content { image(prompt) })

  /** Creates a [Chat] instance which internally tracks the ongoing conversation with the model */
  fun startChat(history: List<Content> = emptyList()): Chat = Chat(this, history.toMutableList())

  /**
   * Counts the amount of tokens in a prompt.
   *
   * @param prompt A group of [Content] to count tokens of.
   * @return A [CountTokensResponse] containing the amount of tokens in the prompt.
   */
  suspend fun countTokens(vararg prompt: Content): CountTokensResponse {
    return controller.countTokens(constructCountTokensRequest(*prompt)).toPublic()
  }

  /**
   * Counts the amount of tokens in the text prompt.
   *
   * @param prompt The text to be converted to a single piece of [Content] to count the tokens of.
   * @return A [CountTokensResponse] containing the amount of tokens in the prompt.
   */
  suspend fun countTokens(prompt: String): CountTokensResponse {
    return countTokens(content { text(prompt) })
  }

  /**
   * Counts the amount of tokens in the image prompt.
   *
   * @param prompt The image to be converted to a single piece of [Content] to count the tokens of.
   * @return A [CountTokensResponse] containing the amount of tokens in the prompt.
   */
  suspend fun countTokens(prompt: Bitmap): CountTokensResponse {
    return countTokens(content { image(prompt) })
  }

  private fun constructRequest(vararg prompt: Content) =
    GenerateContentRequest(
      modelName,
      prompt.map { it.toInternal() },
      safetySettings?.map { it.toInternal() },
      generationConfig?.toInternal(),
      tools?.map { it.toInternal() },
      toolConfig?.toInternal(),
      systemInstruction?.toInternal()
    )

  private fun constructCountTokensRequest(vararg prompt: Content) =
    CountTokensRequest.forVertexAI(constructRequest(*prompt))

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

  companion object {
    private val TAG = GenerativeModel::class.java.simpleName
  }
}
