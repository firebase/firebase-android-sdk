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
import com.google.firebase.ai.ondevice.interop.FirebaseAIOnDeviceGenerativeModelFactory
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.GenerationConfig
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.GenerativeBackendEnum
import com.google.firebase.ai.type.ImagenGenerationConfig
import com.google.firebase.ai.type.ImagenSafetySettings
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
  private val onDeviceFactoryProvider: Provider<FirebaseAIOnDeviceGenerativeModelFactory>,
  private val useLimitedUseAppCheckTokens: Boolean
) {

  /**
   * Instantiates a new [GenerativeModel] given the provided parameters.
   *
   * @param modelName The name of the model to use. See the documentation for a list of
   * [supported models](https://firebase.google.com/docs/ai-logic/models).
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
  ): GenerativeModel =
    generativeModel(
      modelName,
      generationConfig,
      safetySettings,
      tools,
      toolConfig,
      systemInstruction,
      requestOptions,
      OnDeviceConfig.IN_CLOUD
    )

  /**
   * Instantiates a new [GenerativeModel] given the provided parameters.
   *
   * @param modelName The name of the model to use. See the documentation for a list of
   * [supported models](https://firebase.google.com/docs/ai-logic/models).
   * @param generationConfig The configuration parameters to use for content generation.
   * @param safetySettings The safety bounds the model will abide to during content generation.
   * @param tools A list of [Tool]s the model may use to generate content.
   * @param toolConfig The [ToolConfig] that defines how the model handles the tools provided.
   * @param systemInstruction [Content] instructions that direct the model to behave a certain way.
   * Currently only text content is supported.
   * @param requestOptions Configuration options for sending requests to the backend.
   * @param onDeviceConfig Configuration for on-device inference.
   * @return The initialized [GenerativeModel] instance.
   */
  public fun generativeModel(
    modelName: String,
    generationConfig: GenerationConfig? = null,
    safetySettings: List<SafetySetting>? = null,
    tools: List<Tool>? = null,
    toolConfig: ToolConfig? = null,
    systemInstruction: Content? = null,
    requestOptions: RequestOptions = RequestOptions(),
    onDeviceConfig: OnDeviceConfig = OnDeviceConfig.IN_CLOUD
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
      modelName = modelUri,
      apiKey = firebaseApp.options.apiKey,
      firebaseApp = firebaseApp,
      useLimitedUseAppCheckTokens = useLimitedUseAppCheckTokens,
      generationConfig = generationConfig,
      safetySettings = safetySettings,
      tools = tools,
      toolConfig = toolConfig,
      systemInstruction = systemInstruction,
      requestOptions = requestOptions,
      onDeviceConfig = onDeviceConfig,
      generativeBackend = backend,
      appCheckTokenProvider = appCheckProvider.get(),
      internalAuthProvider = internalAuthProvider.get(),
      onDeviceFactoryProvider = onDeviceFactoryProvider.get()
    )
  }

  /**
   * Instantiates a new [TemplateGenerativeModel] given the provided parameters.
   *
   * @param requestOptions Configuration options for sending requests to the backend.
   * @return The initialized [TemplateGenerativeModel] instance.
   */
  @JvmOverloads
  @PublicPreviewAPI
  public fun templateGenerativeModel(
    requestOptions: RequestOptions = RequestOptions(),
  ): TemplateGenerativeModel {
    val templateUri = getTemplateUri(backend)
    return TemplateGenerativeModel(
      templateUri,
      firebaseApp.options.apiKey,
      firebaseApp,
      useLimitedUseAppCheckTokens,
      requestOptions,
      appCheckProvider.get(),
      internalAuthProvider.get(),
    )
  }

  /**
   * Instantiates a new [LiveGenerationConfig] given the provided parameters.
   *
   * @param modelName The name of the model to use. See the documentation for a list of
   * [supported models](https://firebase.google.com/docs/ai-logic/models).
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
          "projects/${firebaseApp.options.projectId}/models/${modelName}"
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
      backend,
      useLimitedUseAppCheckTokens,
    )
  }

  /**
   * Instantiates a new [ImagenModel] given the provided parameters.
   *
   * @param modelName The name of the model to use. See the documentation for a list of
   * [supported models](https://firebase.google.com/docs/ai-logic/models).
   * @param generationConfig The configuration parameters to use for image generation.
   * @param safetySettings The safety bounds the model will abide by during image generation.
   * @param requestOptions Configuration options for sending requests to the backend.
   * @return The initialized [ImagenModel] instance.
   */
  @JvmOverloads
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
      useLimitedUseAppCheckTokens,
      generationConfig,
      safetySettings,
      requestOptions,
      appCheckProvider.get(),
      internalAuthProvider.get(),
    )
  }

  /**
   * Instantiates a new [TemplateImagenModel] given the provided parameters.
   *
   * @param requestOptions Configuration options for sending requests to the backend.
   * @return The initialized [TemplateImagenModel] instance.
   */
  @JvmOverloads
  @PublicPreviewAPI
  public fun templateImagenModel(
    requestOptions: RequestOptions = RequestOptions(),
  ): TemplateImagenModel {
    val templateUri = getTemplateUri(backend)
    return TemplateImagenModel(
      templateUri,
      firebaseApp.options.apiKey,
      firebaseApp,
      useLimitedUseAppCheckTokens,
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
      return getInstance(app, backend, false)
    }

    /**
     * Returns the [FirebaseAI] instance for the provided [FirebaseApp] and [backend].
     *
     * @param backend the backend reference to make generative AI requests to.
     * @param useLimitedUseAppCheckTokens when sending tokens to the backend, this option enables
     * the usage of App Check's limited-use tokens instead of the standard cached tokens. Learn more
     * about [limited-use tokens](https://firebase.google.com/docs/ai-logic/app-check), including
     * their nuances, when to use them, and best practices for integrating them into your app.
     *
     * _This flag is set to `false` by default._
     */
    @JvmStatic
    @JvmOverloads
    public fun getInstance(
      app: FirebaseApp = Firebase.app,
      backend: GenerativeBackend,
      useLimitedUseAppCheckTokens: Boolean,
    ): FirebaseAI {
      val multiResourceComponent = app[FirebaseAIMultiResourceComponent::class.java]
      return multiResourceComponent.get(InstanceKey(backend, useLimitedUseAppCheckTokens))
    }

    /** The [FirebaseAI] instance for the provided [FirebaseApp] using the Google AI Backend. */
    @JvmStatic
    public fun getInstance(app: FirebaseApp): FirebaseAI =
      getInstance(app, GenerativeBackend.googleAI())

    private const val GEMINI_MODEL_NAME_PREFIX = "gemini-"

    private const val IMAGEN_MODEL_NAME_PREFIX = "imagen-"

    private val TAG = FirebaseAI::class.java.simpleName
  }

  private fun getTemplateUri(backend: GenerativeBackend): String =
    when (backend.backend) {
      GenerativeBackendEnum.VERTEX_AI ->
        "projects/${firebaseApp.options.projectId}/locations/${backend.location}/templates/"
      GenerativeBackendEnum.GOOGLE_AI -> "projects/${firebaseApp.options.projectId}/templates/"
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

/**
 * Returns the [FirebaseAI] instance for the provided [FirebaseApp] and [backend].
 *
 * @param backend the backend reference to make generative AI requests to.
 * @param useLimitedUseAppCheckTokens use App Check's limited-use tokens when sending requests to
 * the backend. Learn more about
 * [limited-use tokens](https://firebase.google.com/docs/ai-logic/app-check), including their
 * nuances, when to use them, and best practices for integrating them into your app.
 */
// TODO(b/440356335): Update docs above when web page goes live in M170
public fun Firebase.ai(
  app: FirebaseApp = Firebase.app,
  backend: GenerativeBackend = GenerativeBackend.googleAI(),
  useLimitedUseAppCheckTokens: Boolean
): FirebaseAI = FirebaseAI.getInstance(app, backend, useLimitedUseAppCheckTokens)
