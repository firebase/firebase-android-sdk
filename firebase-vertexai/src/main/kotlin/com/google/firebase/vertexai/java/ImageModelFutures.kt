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

package com.google.firebase.vertexai.java

import androidx.concurrent.futures.SuspendToFutureAdapter
import com.google.common.util.concurrent.ListenableFuture
import com.google.firebase.vertexai.ImageModel
import com.google.firebase.vertexai.type.ImagenGCSImage
import com.google.firebase.vertexai.type.ImagenGenerationConfig
import com.google.firebase.vertexai.type.ImagenGenerationResponse
import com.google.firebase.vertexai.type.ImagenInlineImage

/**
 * Wrapper class providing Java compatible methods for [ImageModel].
 *
 * @see [ImageModel]
 */
public abstract class ImageModelFutures internal constructor() {
  /**
   * Generates an image, returning the result directly to the caller.
   *
   * @param prompt The main text prompt from which the image is generated.
   * @param config contains secondary image generation parameters.
   */
  public abstract fun generateImages(
    prompt: String,
    config: ImagenGenerationConfig?,
  ): ListenableFuture<ImagenGenerationResponse<ImagenInlineImage>>

  /**
   * Generates an image, storing the result in Google Cloud Storage and returning a URL
   *
   * @param prompt The main text prompt from which the image is generated.
   * @param gcsUri Specifies the GCS bucket in which to store the image.
   * @param config contains secondary image generation parameters.
   */
  public abstract fun generateImages(
    prompt: String,
    gcsUri: String,
    config: ImagenGenerationConfig?,
  ): ListenableFuture<ImagenGenerationResponse<ImagenGCSImage>>

  /** Returns the [ImageModel] object wrapped by this object. */
  public abstract fun getImageModel(): ImageModel

  private class FuturesImpl(private val model: ImageModel) : ImageModelFutures() {
    override fun generateImages(
      prompt: String,
      config: ImagenGenerationConfig?,
    ): ListenableFuture<ImagenGenerationResponse<ImagenInlineImage>> =
      SuspendToFutureAdapter.launchFuture { model.generateImage(prompt, config) }

    override fun generateImages(
      prompt: String,
      gcsUri: String,
      config: ImagenGenerationConfig?,
    ): ListenableFuture<ImagenGenerationResponse<ImagenGCSImage>> =
      SuspendToFutureAdapter.launchFuture { model.generateImage(prompt, gcsUri, config) }

    override fun getImageModel(): ImageModel = model
  }

  public companion object {

    /** @return a [ImageModelFutures] created around the provided [ImageModel] */
    @JvmStatic public fun from(model: ImageModel): ImageModelFutures = FuturesImpl(model)
  }
}
