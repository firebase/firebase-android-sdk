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
import com.google.firebase.ai.type.Dimensions
import com.google.firebase.ai.type.FirebaseAIException
import com.google.firebase.ai.type.ImagenEditMode
import com.google.firebase.ai.type.ImagenEditingConfig
import com.google.firebase.ai.type.ImagenGenerationConfig
import com.google.firebase.ai.type.ImagenGenerationResponse
import com.google.firebase.ai.type.ImagenImagePlacement
import com.google.firebase.ai.type.ImagenInlineImage
import com.google.firebase.ai.type.ImagenMaskReference
import com.google.firebase.ai.type.ImagenRawImage
import com.google.firebase.ai.type.ImagenReferenceImage
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
        .generateImage(constructGenerateImageRequest(prompt, generationConfig))
        .validate()
        .toPublicInline()
    } catch (e: Throwable) {
      throw FirebaseAIException.from(e)
    }

  /**
   * Generates an image from a single or set of base images, returning the result directly to the
   * caller.
   *
   * @param prompt the text input given to the model as a prompt
   * @param referenceImages the image inputs given to the model as a prompt
   * @param config the editing configuration settings
   */
  public suspend fun editImage(
    referenceImages: List<ImagenReferenceImage>,
    prompt: String,
    config: ImagenEditingConfig? = null,
  ): ImagenGenerationResponse<ImagenInlineImage> =
    try {
      controller
        .generateImage(constructEditRequest(referenceImages, prompt, config))
        .validate()
        .toPublicInline()
    } catch (e: Throwable) {
      throw FirebaseAIException.from(e)
    }

  /**
   * Generates an image by inpainting a masked off part of a base image.
   *
   * @param image the base image
   * @param prompt the text input given to the model as a prompt
   * @param mask the mask which defines where in the image can be painted by imagen.
   * @param config the editing configuration settings, its important to include an [ImagenEditMode]
   */
  public suspend fun inpaintImage(
    image: ImagenInlineImage,
    prompt: String,
    mask: ImagenMaskReference,
    config: ImagenEditingConfig,
  ): ImagenGenerationResponse<ImagenInlineImage> {
    return editImage(listOf(ImagenRawImage(image), mask), prompt, config)
  }

  /**
   * Generates an image by outpainting the image, extending its borders
   *
   * @param image the base image
   * @param newDimensions the new dimensions for the image, *must* be larger than the original
   * image.
   * @param newPosition the placement of the base image within the new image. This can either be
   * coordinates (0,0 is the top left corner) or an alignment (ex: [ImagenImagePlacement.BOTTOM])
   * @param prompt optional, but can be used to specify the background generated if context is
   * insufficient
   * @param config the editing configuration settings
   * @see [ImagenMaskReference.generateMaskAndPadForOutpainting]
   */
  public suspend fun outpaintImage(
    image: ImagenInlineImage,
    newDimensions: Dimensions,
    newPosition: ImagenImagePlacement = ImagenImagePlacement.CENTER,
    prompt: String = "",
    config: ImagenEditingConfig? = null,
  ): ImagenGenerationResponse<ImagenInlineImage> {
    return editImage(
      ImagenMaskReference.generateMaskAndPadForOutpainting(image, newDimensions, newPosition),
      prompt,
      ImagenEditingConfig(ImagenEditMode.OUTPAINT, config?.editSteps)
    )
  }

  private fun constructGenerateImageRequest(
    prompt: String,
    generationConfig: ImagenGenerationConfig? = null,
  ): GenerateImageRequest {
    return GenerateImageRequest(
      listOf(GenerateImageRequest.ImagenPrompt(prompt)),
      GenerateImageRequest.ImagenParameters(
        sampleCount = generationConfig?.numberOfImages ?: 1,
        includeRaiReason = true,
        addWatermark = generationConfig?.addWatermark,
        personGeneration = safetySettings?.personFilterLevel?.internalVal,
        negativePrompt = generationConfig?.negativePrompt,
        safetySetting = safetySettings?.safetyFilterLevel?.internalVal,
        storageUri = null,
        aspectRatio = generationConfig?.aspectRatio?.internalVal,
        imageOutputOptions = generationConfig?.imageFormat?.toInternal(),
        editMode = null,
        editConfig = null,
      ),
    )
  }

  private fun constructEditRequest(
    referenceImages: List<ImagenReferenceImage>,
    prompt: String,
    editConfig: ImagenEditingConfig?,
  ): GenerateImageRequest {
    var maxRefId = referenceImages.mapNotNull { it.referenceId }.maxOrNull() ?: 1
    return GenerateImageRequest(
      listOf(
        GenerateImageRequest.ImagenPrompt(
          prompt = prompt,
          referenceImages = referenceImages.map { it.toInternal(++maxRefId) },
        )
      ),
      GenerateImageRequest.ImagenParameters(
        sampleCount = generationConfig?.numberOfImages ?: 1,
        includeRaiReason = true,
        addWatermark = generationConfig?.addWatermark,
        personGeneration = safetySettings?.personFilterLevel?.internalVal,
        negativePrompt = generationConfig?.negativePrompt,
        safetySetting = safetySettings?.safetyFilterLevel?.internalVal,
        storageUri = null,
        aspectRatio = generationConfig?.aspectRatio?.internalVal,
        imageOutputOptions = generationConfig?.imageFormat?.toInternal(),
        editMode = editConfig?.editMode?.value,
        editConfig = editConfig?.toInternal(),
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
