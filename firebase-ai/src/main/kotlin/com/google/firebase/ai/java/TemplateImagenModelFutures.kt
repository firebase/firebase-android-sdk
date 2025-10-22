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
import com.google.firebase.ai.TemplateImagenModel
import com.google.firebase.ai.java.ImagenModelFutures.FuturesImpl
import com.google.firebase.ai.type.ImagenGenerationResponse
import com.google.firebase.ai.type.ImagenInlineImage
import com.google.firebase.ai.type.PublicPreviewAPI

/**
 * Wrapper class providing Java compatible methods for [TemplateImagenModel].
 *
 * @see [TemplateImagenModel]
 */
@OptIn(PublicPreviewAPI::class)
public abstract class TemplateImagenModelFutures internal constructor() {

  /**
   * Generates an image, returning the result directly to the caller.
   *
   * @param templateId The ID of server prompt template.
   * @param inputs the inputs needed to fill in the prompt
   */
  public abstract fun generateImages(
    templateId: String,
    inputs: Map<String, Any>
  ): ListenableFuture<ImagenGenerationResponse<ImagenInlineImage>>

  /** Returns the [TemplateImagenModel] object wrapped by this object. */
  public abstract fun getImageModel(): TemplateImagenModel

  private class FuturesImpl(private val model: TemplateImagenModel) : TemplateImagenModelFutures() {
    override fun generateImages(
      templateId: String,
      inputs: Map<String, Any>
    ): ListenableFuture<ImagenGenerationResponse<ImagenInlineImage>> {
      return SuspendToFutureAdapter.launchFuture { model.generateImages(templateId, inputs) }
    }

    override fun getImageModel(): TemplateImagenModel {
      return model
    }
  }
  public companion object {

    /** @return a [TemplateImagenModelFutures] created around the provided [TemplateImagenModel] */
    @JvmStatic
    public fun from(model: TemplateImagenModel): TemplateImagenModelFutures = FuturesImpl(model)
  }
}
