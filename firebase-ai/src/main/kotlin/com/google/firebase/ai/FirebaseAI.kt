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

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.GenerationConfig
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.GenerativeBackendEnum
import com.google.firebase.ai.type.ImagenGenerationConfig
import com.google.firebase.ai.type.ImagenSafetySettings
import com.google.firebase.ai.type.InvalidStateException
import com.google.firebase.ai.type.LiveGenerationConfig
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.RequestOptions
import com.google.firebase.ai.type.SafetySetting
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.ToolConfig
import com.google.firebase.annotations.concurrent.Blocking
import com.google.firebase.app
import com.google.firebase.appcheck.interop.InteropAppCheckTokenProvider
import com.google.firebase.auth.internal.InternalAuthProvider
import com.google.firebase.inject.Provider
import kotlin.coroutines.CoroutineContext

/** Entry point for all _Firebase AI_ functionality. */
public class FirebaseAI
internal constructor(
  private val firebaseApp: FirebaseApp,
  private val backend: GenerativeBackend,
  @Blocking private val blockingDispatcher: CoroutineContext,
  private val appCheckProvider: Provider<InteropAppCheckTokenProvider>,
  private val internalAuthProvider: Provider<InternalAuthProvider>,
) {

  /**
   * Instantiates a new [GenerativeModel] given the provided parameters.
   *
   * @param modelName The name of the model to use, for example `"gemini-2.0-flash-exp"`.
   * @param generationConfig The configuration parameters to use for content generation.
   * @param safetySettings The safety bounds the model will abide to during content generation.
   * @param tools A list of [Tool]s the model may use to generate content.
   * @param toolConfig The [ToolConfig] that defines how the model handles the tools provided.
   * @param systemInstruction [Content] instructions that direct the model to behave a certain way.
   * Currently only text content is supported.
   * @param requestOptions Configuration options for sending requests to the backend.
   * @return The initialized [GenerativeModel] instance.
   */
  @JvmOverloads
  public fun generativeModel(
    modelName: String,
    generationConfig: GenerationConfig? = null,
    safetySettings: List<SafetySetting>? = null,
    tools: List<Tool>? = null,
    toolConfig: ToolConfig? = null,
    systemInstruction: Content? = null,
    requestOptions: RequestOptions = RequestOptions(),
  ): GenerativeModel {
    val modelUri =
      when (backend.backend) {
        GenerativeBackendEnum.VERTEX_AI ->
          "projects/${firebaseApp.options.projectId}/locations/${backend.location}/publishers/google/models/${modelName}"
        GenerativeBackendEnum.GOOGLE_AI ->
          "projects/${firebaseApp.options.projectId}/models/${modelName}"
      }
    if (!modelName.startsWith(GEMINI_MODEL_NAME_PREFIX)) {
      Log.w(
        TAG,
        """Unsupported Gemini model "${modelName}"; see
      https://firebase.google.com/docs/vertex-ai/models for a list supported Gemini model names.
      """
          .trimIndent(),
      )
    }
    return GenerativeModel(
      modelUri,
      firebaseApp.options.apiKey,
      firebaseApp,
      generationConfig,
      safetySettings,
      tools,
      toolConfig,
      systemInstruction,
      requestOptions,
      backend,
      appCheckProvider.get(),
      internalAuthProvider.get(),
    )
  }

  /**
   * Instantiates a new [LiveGenerationConfig] given the provided parameters.
   *
   * @param modelName The name of the model to use, for example `"gemini-2.0-flash-exp"`.
   * @param generationConfig The configuration parameters to use for content generation.
   * @param tools A list of [Tool]s the model may use to generate content.
   * @param systemInstruction [Content] instructions that direct the model to behave a certain way.
   * Currently only text content is supported.
   * @param requestOptions Configuration options for sending requests to the backend.
   * @return The initialized [LiveGenerativeModel] instance.
   */
  @JvmOverloads
  @PublicPreviewAPI
  public fun liveModel(
    modelName: String,
    generationConfig: LiveGenerationConfig? = null,
    tools: List<Tool>? = null,
    systemInstruction: Content? = null,
    requestOptions: RequestOptions = RequestOptions(),
  ): LiveGenerativeModel {
    if (!modelName.startsWith(GEMINI_MODEL_NAME_PREFIX)) {
      Log.w(
        TAG,
        """Unsupported Gemini model "$modelName"; see
      https://firebase.google.com/docs/vertex-ai/models for a list supported Gemini model names.
      """
          .trimIndent(),
      )
    }
    return LiveGenerativeModel(
      when (backend.backend) {
        GenerativeBackendEnum.VERTEX_AI ->
          "projects/${firebaseApp.options.projectId}/locations/${backend.location}/publishers/google/models/${modelName}"
        GenerativeBackendEnum.GOOGLE_AI ->
          throw InvalidStateException("Live Model is not yet available on the Google AI backend")
      },
      firebaseApp.options.apiKey,
      firebaseApp,
      blockingDispatcher,
      generationConfig,
      tools,
      systemInstruction,
      backend.location,
      requestOptions,
      appCheckProvider.get(),
      internalAuthProvider.get(),
    )
  }

  /**
   * Instantiates a new [ImagenModel] given the provided parameters.
   *
   * @param modelName The name of the model to use, for example `"imagen-3.0-generate-001"`.
   * @param generationConfig The configuration parameters to use for image generation.
   * @param safetySettings The safety bounds the model will abide by during image generation.
   * @param requestOptions Configuration options for sending requests to the backend.
   * @return The initialized [ImagenModel] instance.
   */
  @JvmOverloads
  @PublicPreviewAPI
  public fun imagenModel(
    modelName: String,
    generationConfig: ImagenGenerationConfig? = null,
    safetySettings: ImagenSafetySettings? = null,
    requestOptions: RequestOptions = RequestOptions(),
  ): ImagenModel {
    val modelUri =
      when (backend.backend) {
        GenerativeBackendEnum.VERTEX_AI ->
          "projects/${firebaseApp.options.projectId}/locations/${backend.location}/publishers/google/models/${modelName}"
        GenerativeBackendEnum.GOOGLE_AI ->
          "projects/${firebaseApp.options.projectId}/models/${modelName}"
      }
    if (!modelName.startsWith(IMAGEN_MODEL_NAME_PREFIX)) {
      Log.w(
        TAG,
        """Unsupported Imagen model "${modelName}"; see
      https://firebase.google.com/docs/vertex-ai/models for a list supported Imagen model names.
      """
          .trimIndent(),
      )
    }
    return ImagenModel(
      modelUri,
      firebaseApp.options.apiKey,
      firebaseApp,
      generationConfig,
      safetySettings,
      requestOptions,
      appCheckProvider.get(),
      internalAuthProvider.get(),
    )
  }

  public companion object {
    /** The [FirebaseAI] instance for the default [FirebaseApp] using the Google AI Backend. */
    @JvmStatic
    public val instance: FirebaseAI
      get() = getInstance(backend = GenerativeBackend.googleAI())

    /**
     * Returns the [FirebaseAI] instance for the provided [FirebaseApp] and [backend].
     *
     * @param backend the backend reference to make generative AI requests to.
     */
    @JvmStatic
    @JvmOverloads
    public fun getInstance(
      app: FirebaseApp = Firebase.app,
      backend: GenerativeBackend
    ): FirebaseAI {
      val multiResourceComponent = app[FirebaseAIMultiResourceComponent::class.java]
      return multiResourceComponent.get(backend)
    }

    /** The [FirebaseAI] instance for the provided [FirebaseApp] using the Google AI Backend. */
    @JvmStatic public fun getInstance(app: FirebaseApp): FirebaseAI = getInstance(app)

    private const val GEMINI_MODEL_NAME_PREFIX = "gemini-"

    private const val IMAGEN_MODEL_NAME_PREFIX = "imagen-"

    private val TAG = FirebaseAI::class.java.simpleName
  }
}

/** The [FirebaseAI] instance for the default [FirebaseApp] using the Google AI Backend. */
public val Firebase.ai: FirebaseAI
  get() = FirebaseAI.instance

/**
 * Returns the [FirebaseAI] instance for the provided [FirebaseApp] and [backend].
 *
 * @param backend the backend reference to make generative AI requests to.
 */
public fun Firebase.ai(
  app: FirebaseApp = Firebase.app,
  backend: GenerativeBackend = GenerativeBackend.googleAI()
): FirebaseAI = FirebaseAI.getInstance(app, backend)
