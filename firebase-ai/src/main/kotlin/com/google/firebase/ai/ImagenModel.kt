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

package com.google.firebase.ai

import com.google.firebase.FirebaseApp
import com.google.firebase.ai.common.APIController
import com.google.firebase.ai.common.AppCheckHeaderProvider
import com.google.firebase.ai.common.ContentBlockedException
import com.google.firebase.ai.common.GenerateImageRequest
import com.google.firebase.ai.type.FirebaseAIException
import com.google.firebase.ai.type.ImagenEditingConfig
import com.google.firebase.ai.type.ImagenGenerationConfig
import com.google.firebase.ai.type.ImagenGenerationResponse
import com.google.firebase.ai.type.ImagenInlineImage
import com.google.firebase.ai.type.ImagenSafetySettings
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.RequestOptions
import com.google.firebase.appcheck.interop.InteropAppCheckTokenProvider
import com.google.firebase.auth.internal.InternalAuthProvider

/**
 * Represents a generative model (like Imagen), capable of generating images based on various input
 * types.
 */
@PublicPreviewAPI
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
    firebaseApp: FirebaseApp,
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
      "gl-kotlin/${KotlinVersion.CURRENT}-ai fire/${BuildConfig.VERSION_NAME}",
      firebaseApp,
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
      controller
        .generateImage(constructGenerationRequest(prompt, null, generationConfig))
        .validate()
        .toPublicInline()
    } catch (e: Throwable) {
      throw FirebaseAIException.from(e)
    }

  /**
   * Generates an image, based on both a prompt, and input image, returning the result directly to
   * the caller.
   *
   * @param prompt The input(s) given to the model as a prompt.
   * @param config The editing config given to the model.
   */
  public suspend fun editImage(
    prompt: String,
    config: ImagenEditingConfig
  ): ImagenGenerationResponse<ImagenInlineImage> =
    try {
      controller
        .generateImage(constructEditRequest(prompt, null, config))
        .validate()
        .toPublicInline()
    } catch (e: Throwable) {
      throw FirebaseAIException.from(e)
    }

  private fun constructGenerationRequest(
    prompt: String,
    gcsUri: String? = null,
    generationConfig: ImagenGenerationConfig? = null,
  ): GenerateImageRequest {
    return GenerateImageRequest(
      listOf(GenerateImageRequest.ImagenPrompt(prompt)),
      GenerateImageRequest.ImagenParameters(
        sampleCount = generationConfig?.numberOfImages ?: 1,
        includeRaiReason = true,
        addWatermark = this.generationConfig?.addWatermark,
        personGeneration = safetySettings?.personFilterLevel?.internalVal,
        negativePrompt = generationConfig?.negativePrompt,
        safetySetting = safetySettings?.safetyFilterLevel?.internalVal,
        storageUri = gcsUri,
        aspectRatio = generationConfig?.aspectRatio?.internalVal,
        imageOutputOptions = this.generationConfig?.imageFormat?.toInternal(),
        editMode = null,
        editConfig = null
      ),
    )
  }

  private fun constructEditRequest(
    prompt: String,
    gcsUri: String? = null,
    editConfig: ImagenEditingConfig,
  ): GenerateImageRequest {
    return GenerateImageRequest(
      listOf(
        GenerateImageRequest.ImagenPrompt(
          prompt = prompt,
          referenceImages =
            buildList {
              add(
                GenerateImageRequest.ReferenceImage(
                  referenceType = GenerateImageRequest.ReferenceType.RAW,
                  referenceId = 1,
                  referenceImage = editConfig.image.toInternal(),
                  maskImageConfig = null
                )
              )
              if (editConfig.mask != null) {
                add(
                  GenerateImageRequest.ReferenceImage(
                    referenceType = GenerateImageRequest.ReferenceType.MASK,
                    referenceId = 2,
                    referenceImage = editConfig.mask.toInternal(),
                    maskImageConfig =
                      GenerateImageRequest.MaskImageConfig(
                        maskMode = GenerateImageRequest.MaskMode.USER_PROVIDED,
                        dilation = editConfig.maskDilation
                      )
                  )
                )
              }
            }
        )
      ),
      GenerateImageRequest.ImagenParameters(
        sampleCount = generationConfig?.numberOfImages ?: 1,
        includeRaiReason = true,
        addWatermark = this.generationConfig?.addWatermark,
        personGeneration = safetySettings?.personFilterLevel?.internalVal,
        negativePrompt = generationConfig?.negativePrompt,
        safetySetting = safetySettings?.safetyFilterLevel?.internalVal,
        storageUri = gcsUri,
        aspectRatio = generationConfig?.aspectRatio?.internalVal,
        imageOutputOptions = this.generationConfig?.imageFormat?.toInternal(),
        editMode = editConfig.editMode.value,
        editConfig = editConfig.toInternal()
      ),
    )
  }

  internal companion object {
    private val TAG = ImagenModel::class.java.simpleName
    internal const val DEFAULT_FILTERED_ERROR =
      "Unable to show generated images. All images were filtered out because they violated Vertex AI's usage guidelines. You will not be charged for blocked images. Try rephrasing the prompt. If you think this was an error, send feedback."
  }
}

@OptIn(PublicPreviewAPI::class)
private fun ImagenGenerationResponse.Internal.validate(): ImagenGenerationResponse.Internal {
  if (predictions.none { it.mimeType != null }) {
    throw ContentBlockedException(
      message = predictions.first { it.raiFilteredReason != null }.raiFilteredReason
          ?: ImagenModel.DEFAULT_FILTERED_ERROR
    )
  }
  return this
}
