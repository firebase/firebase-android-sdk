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

import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.app
import com.google.firebase.appcheck.interop.InteropAppCheckTokenProvider
import com.google.firebase.auth.internal.InternalAuthProvider
import com.google.firebase.inject.Provider
import com.google.firebase.vertexai.type.Content
import com.google.firebase.vertexai.type.GenerationConfig
import com.google.firebase.vertexai.type.GenerativeBackend
import com.google.firebase.vertexai.type.ImagenGenerationConfig
import com.google.firebase.vertexai.type.ImagenSafetySettings
import com.google.firebase.vertexai.type.InvalidLocationException
import com.google.firebase.vertexai.type.PublicPreviewAPI
import com.google.firebase.vertexai.type.RequestOptions
import com.google.firebase.vertexai.type.SafetySetting
import com.google.firebase.vertexai.type.Tool
import com.google.firebase.vertexai.type.ToolConfig

/** Entry point for all _Vertex AI for Firebase_ functionality. */
public class FirebaseVertexAI
internal constructor(
  private val firebaseApp: FirebaseApp,
  private val backend: GenerativeBackend,
  private val location: String,
  private val appCheckProvider: Provider<InteropAppCheckTokenProvider>,
  private val internalAuthProvider: Provider<InternalAuthProvider>,
) {

  /**
   * Instantiates a new [GenerativeModel] given the provided parameters.
   *
   * @param modelName The name of the model to use, for example `"gemini-1.5-pro"`.
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
    if (location.trim().isEmpty() || location.contains("/")) {
      throw InvalidLocationException(location)
    }
    val modelUri =
      when (backend) {
        GenerativeBackend.VERTEX_AI ->
          "projects/${firebaseApp.options.projectId}/locations/${location}/publishers/google/models/${modelName}"
        GenerativeBackend.GOOGLE_AI ->
          "projects/${firebaseApp.options.projectId}/models/${modelName}"
      }
    return GenerativeModel(
      modelUri,
      firebaseApp.options.apiKey,
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
    if (location.trim().isEmpty() || location.contains("/")) {
      throw InvalidLocationException(location)
    }
    return ImagenModel(
      "projects/${firebaseApp.options.projectId}/locations/${location}/publishers/google/models/${modelName}",
      firebaseApp.options.apiKey,
      generationConfig,
      safetySettings,
      requestOptions,
      appCheckProvider.get(),
      internalAuthProvider.get(),
    )
  }

  public companion object {
    /** The [FirebaseVertexAI] instance for the default [FirebaseApp] */
    @JvmStatic
    public val instance: FirebaseVertexAI
      get() = getInstance(location = "us-central1")

    /**
     * Returns the [FirebaseVertexAI] instance for the provided [FirebaseApp] and [location].
     *
     * @param location location identifier, defaults to `us-central1`; see available
     * [Vertex AI regions](https://firebase.google.com/docs/vertex-ai/locations?platform=android#available-locations)
     * .
     */
    @JvmStatic
    @JvmOverloads
    public fun getInstance(app: FirebaseApp = Firebase.app, location: String): FirebaseVertexAI {
      val multiResourceComponent = app[FirebaseVertexAIMultiResourceComponent::class.java]
      return multiResourceComponent.get(GenerativeBackend.VERTEX_AI, location)
    }
  }
}

/** Returns the [FirebaseVertexAI] instance of the default [FirebaseApp]. */
public val Firebase.vertexAI: FirebaseVertexAI
  get() = FirebaseVertexAI.instance

/** Returns the [FirebaseVertexAI] instance of a given [FirebaseApp]. */
public fun Firebase.vertexAI(
  app: FirebaseApp = Firebase.app,
  location: String = "us-central1",
): FirebaseVertexAI = FirebaseVertexAI.getInstance(app, location)
