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

package com.google.firebase.ai.java

import androidx.concurrent.futures.SuspendToFutureAdapter
import com.google.common.util.concurrent.ListenableFuture
import com.google.firebase.ai.ImagenModel
import com.google.firebase.ai.type.Dimensions
import com.google.firebase.ai.type.ImagenEditMode
import com.google.firebase.ai.type.ImagenEditingConfig
import com.google.firebase.ai.type.ImagenGenerationResponse
import com.google.firebase.ai.type.ImagenImagePlacement
import com.google.firebase.ai.type.ImagenInlineImage
import com.google.firebase.ai.type.ImagenMaskReference
import com.google.firebase.ai.type.ImagenReferenceImage
import com.google.firebase.ai.type.PublicPreviewAPI

/**
 * Wrapper class providing Java compatible methods for [ImagenModel].
 *
 * @see [ImagenModel]
 */
@PublicPreviewAPI
public abstract class ImagenModelFutures internal constructor() {
  /**
   * Generates an image, returning the result directly to the caller.
   *
   * @param prompt The main text prompt from which the image is generated.
   */
  public abstract fun generateImages(
    prompt: String,
  ): ListenableFuture<ImagenGenerationResponse<ImagenInlineImage>>

  /**
   * Generates an image from a single or set of base images, returning the result directly to the
   * caller.
   *
   * @param prompt the text input given to the model as a prompt
   * @param referenceImages the image inputs given to the model as a prompt
   * @param config the editing configuration settings
   */
  public abstract fun editImage(
    referenceImages: List<ImagenReferenceImage>,
    prompt: String,
    config: ImagenEditingConfig? = null
  ): ListenableFuture<ImagenGenerationResponse<ImagenInlineImage>>

  /**
   * Generates an image from a single or set of base images, returning the result directly to the
   * caller.
   *
   * @param prompt the text input given to the model as a prompt
   * @param referenceImages the image inputs given to the model as a prompt
   */
  public abstract fun editImage(
    referenceImages: List<ImagenReferenceImage>,
    prompt: String,
  ): ListenableFuture<ImagenGenerationResponse<ImagenInlineImage>>

  /**
   * Generates an image by inpainting a masked off part of a base image.
   *
   * @param image the base image
   * @param prompt the text input given to the model as a prompt
   * @param mask the mask which defines where in the image can be painted by imagen.
   * @param config the editing configuration settings, it should include an [ImagenEditMode]
   */
  public abstract fun inpaintImage(
    image: ImagenInlineImage,
    prompt: String,
    mask: ImagenMaskReference,
    config: ImagenEditingConfig,
  ): ListenableFuture<ImagenGenerationResponse<ImagenInlineImage>>

  /**
   * Generates an image by outpainting the image, extending its borders
   *
   * @param image the base image
   * @param newDimensions the new dimensions for the image, *must* be larger than the original
   * image.
   * @param newPosition the placement of the base image within the new image. This can either be
   * coordinates (0,0 is the top left corner) or an alignment (ex:
   * [ImagenImagePlacement.BOTTOM_CENTER])
   * @param prompt optional, but can be used to specify the background generated if context is
   * insufficient
   * @param config the editing configuration settings
   * @see [ImagenMaskReference.generateMaskAndPadForOutpainting]
   */
  public abstract fun outpaintImage(
    image: ImagenInlineImage,
    newDimensions: Dimensions,
    newPosition: ImagenImagePlacement = ImagenImagePlacement.CENTER,
    prompt: String = "",
    config: ImagenEditingConfig? = null,
  ): ListenableFuture<ImagenGenerationResponse<ImagenInlineImage>>

  /** Returns the [ImagenModel] object wrapped by this object. */
  public abstract fun getImageModel(): ImagenModel

  private class FuturesImpl(private val model: ImagenModel) : ImagenModelFutures() {
    override fun generateImages(
      prompt: String,
    ): ListenableFuture<ImagenGenerationResponse<ImagenInlineImage>> =
      SuspendToFutureAdapter.launchFuture { model.generateImages(prompt) }

    override fun editImage(
      referenceImages: List<ImagenReferenceImage>,
      prompt: String,
      config: ImagenEditingConfig?
    ): ListenableFuture<ImagenGenerationResponse<ImagenInlineImage>> =
      SuspendToFutureAdapter.launchFuture { model.editImage(referenceImages, prompt, config) }

    override fun editImage(
      referenceImages: List<ImagenReferenceImage>,
      prompt: String,
    ): ListenableFuture<ImagenGenerationResponse<ImagenInlineImage>> =
      editImage(referenceImages, prompt, null)

    override fun inpaintImage(
      image: ImagenInlineImage,
      prompt: String,
      mask: ImagenMaskReference,
      config: ImagenEditingConfig
    ): ListenableFuture<ImagenGenerationResponse<ImagenInlineImage>> =
      SuspendToFutureAdapter.launchFuture { model.inpaintImage(image, prompt, mask, config) }

    override fun outpaintImage(
      image: ImagenInlineImage,
      newDimensions: Dimensions,
      newPosition: ImagenImagePlacement,
      prompt: String,
      config: ImagenEditingConfig?
    ): ListenableFuture<ImagenGenerationResponse<ImagenInlineImage>> =
      SuspendToFutureAdapter.launchFuture {
        model.outpaintImage(image, newDimensions, newPosition, prompt, config)
      }

    override fun getImageModel(): ImagenModel = model
  }

  public companion object {

    /** @return a [ImagenModelFutures] created around the provided [ImagenModel] */
    @JvmStatic public fun from(model: ImagenModel): ImagenModelFutures = FuturesImpl(model)
  }
}
