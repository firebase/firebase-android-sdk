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
import com.google.firebase.vertexai.ImagenModel
import com.google.firebase.vertexai.type.ImagenGenerationResponse
import com.google.firebase.vertexai.type.ImagenInlineImage
import com.google.firebase.vertexai.type.PublicPreviewAPI

/**
 * Wrapper class providing Java compatible methods for [ImagenModel].
 *
 * @see [ImagenModel]
 */
@PublicPreviewAPI
@Deprecated(
  """The Vertex AI in Firebase SDK (firebase-vertexai) has been replaced with the FirebaseAI SDK (firebase-ai) to accommodate the evolving set of supported features and services.
For migration details, see the migration guide: https://firebase.google.com/docs/vertex-ai/migrate-to-latest-sdk"""
)
public abstract class ImagenModelFutures internal constructor() {
  /**
   * Generates an image, returning the result directly to the caller.
   *
   * @param prompt The main text prompt from which the image is generated.
   */
  public abstract fun generateImages(
    prompt: String,
  ): ListenableFuture<ImagenGenerationResponse<ImagenInlineImage>>

  /** Returns the [ImagenModel] object wrapped by this object. */
  public abstract fun getImageModel(): ImagenModel

  private class FuturesImpl(private val model: ImagenModel) : ImagenModelFutures() {
    override fun generateImages(
      prompt: String,
    ): ListenableFuture<ImagenGenerationResponse<ImagenInlineImage>> =
      SuspendToFutureAdapter.launchFuture { model.generateImages(prompt) }

    override fun getImageModel(): ImagenModel = model
  }

  public companion object {

    /** @return a [ImagenModelFutures] created around the provided [ImagenModel] */
    @JvmStatic public fun from(model: ImagenModel): ImagenModelFutures = FuturesImpl(model)
  }
}
