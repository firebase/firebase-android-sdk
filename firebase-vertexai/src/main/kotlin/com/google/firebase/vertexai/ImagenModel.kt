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

package com.google.firebase.vertexai

import com.google.firebase.appcheck.interop.InteropAppCheckTokenProvider
import com.google.firebase.auth.internal.InternalAuthProvider
import com.google.firebase.vertexai.common.APIController
import com.google.firebase.vertexai.common.ContentBlockedException
import com.google.firebase.vertexai.internal.GenerateImageRequest
import com.google.firebase.vertexai.internal.GenerateImageResponse
import com.google.firebase.vertexai.internal.ImagenParameters
import com.google.firebase.vertexai.internal.ImagenPromptInstance
import com.google.firebase.vertexai.internal.util.AppCheckHeaderProvider
import com.google.firebase.vertexai.internal.util.toInternal
import com.google.firebase.vertexai.internal.util.toPublicGCS
import com.google.firebase.vertexai.internal.util.toPublicInline
import com.google.firebase.vertexai.type.FirebaseVertexAIException
import com.google.firebase.vertexai.type.ImagenGCSImage
import com.google.firebase.vertexai.type.ImagenGenerationConfig
import com.google.firebase.vertexai.type.ImagenGenerationResponse
import com.google.firebase.vertexai.type.ImagenInlineImage
import com.google.firebase.vertexai.type.ImagenSafetySettings
import com.google.firebase.vertexai.type.RequestOptions

/**
 * Represents a generative model (like Imagen), capable of generating images based on various input
 * types.
 */
public class ImagenModel
internal constructor(
  private val modelName: String,
  private val generationConfig: ImagenGenerationConfig? = null,
  private val safetySettings: ImagenSafetySettings? = null,
  private val controller: APIController,
) {
  @JvmOverloads
  internal constructor(
    modelName: String,
    apiKey: String,
    generationConfig: ImagenGenerationConfig? = null,
    safetySettings: ImagenSafetySettings? = null,
    requestOptions: RequestOptions = RequestOptions(),
    appCheckTokenProvider: InteropAppCheckTokenProvider? = null,
    internalAuthProvider: InternalAuthProvider? = null,
  ) : this(
    modelName,
    generationConfig,
    safetySettings,
    APIController(
      apiKey,
      modelName,
      requestOptions,
      "gl-kotlin/${KotlinVersion.CURRENT} fire/${BuildConfig.VERSION_NAME}",
      AppCheckHeaderProvider(TAG, appCheckTokenProvider, internalAuthProvider),
    ),
  )

  /**
   * Generates an image, returning the result directly to the caller.
   *
   * @param prompt The input(s) given to the model as a prompt.
   */
  public suspend fun generateImages(prompt: String): ImagenGenerationResponse<ImagenInlineImage> =
    try {
      controller.generateImage(constructRequest(prompt, null, generationConfig)).toPublicInline()
      controller
        .generateImage(constructRequest(prompt, null, generationConfig))
        .validate()
        .toPublicInline()
    } catch (e: Throwable) {
      throw FirebaseVertexAIException.from(e)
    }

  /**
   * Generates an image, storing the result in Google Cloud Storage and returning a URL
   *
   * @param prompt The input(s) given to the model as a prompt.
   * @param gcsUri Specifies the Google Cloud Storage bucket in which to store the image.
   */
  public suspend fun generateImages(
    prompt: String,
    gcsUri: String,
  ): ImagenGenerationResponse<ImagenGCSImage> =
    try {
      controller
        .generateImage(constructRequest(prompt, gcsUri, generationConfig))
        .validate()
        .toPublicGCS()
    } catch (e: Throwable) {
      throw FirebaseVertexAIException.from(e)
    }

  private fun constructRequest(
    prompt: String,
    gcsUri: String?,
    config: ImagenGenerationConfig?,
  ): GenerateImageRequest {
    return GenerateImageRequest(
      listOf(ImagenPromptInstance(prompt)),
      ImagenParameters(
        sampleCount = config?.numberOfImages ?: 1,
        includeRaiReason = true,
        addWatermark = generationConfig?.addWatermark,
        personGeneration = safetySettings?.personFilterLevel?.internalVal,
        negativePrompt = config?.negativePrompt,
        safetySetting = safetySettings?.safetyFilterLevel?.internalVal,
        storageUri = gcsUri,
        aspectRatio = config?.aspectRatio?.internalVal,
        imageOutputOptions = generationConfig?.imageFormat?.toInternal(),
      ),
    )
  }

  internal companion object {
    private val TAG = ImagenModel::class.java.simpleName
    internal const val DEFAULT_FILTERED_ERROR =
      "Unable to show generated images. All images were filtered out because they violated Vertex AI's usage guidelines. You will not be charged for blocked images. Try rephrasing the prompt. If you think this was an error, send feedback."
  }
}

private fun GenerateImageResponse.validate(): GenerateImageResponse {
  if (predictions.none { it.mimeType != null }) {
    throw ContentBlockedException(
      message = predictions.first { it.raiFilteredReason != null }.raiFilteredReason
          ?: ImagenModel.DEFAULT_FILTERED_ERROR
    )
  }
  return this
}
