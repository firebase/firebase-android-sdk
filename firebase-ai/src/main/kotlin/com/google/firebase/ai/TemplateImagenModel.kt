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
import com.google.firebase.ai.common.TemplateGenerateImageRequest
import com.google.firebase.ai.type.FirebaseAIException
import com.google.firebase.ai.type.ImagenGenerationResponse
import com.google.firebase.ai.type.ImagenInlineImage
import com.google.firebase.ai.type.RequestOptions
import com.google.firebase.appcheck.interop.InteropAppCheckTokenProvider
import com.google.firebase.auth.internal.InternalAuthProvider

/**
 * Represents a generative model (like Imagen), capable of generating images based a template.
 *
 * See the documentation for a list of
 * [supported models](https://firebase.google.com/docs/ai-logic/models).
 */
public class TemplateImagenModel
internal constructor(
  private val templateUri: String,
  private val controller: APIController,
) {

  @JvmOverloads
  internal constructor(
    templateUri: String,
    apiKey: String,
    firebaseApp: FirebaseApp,
    useLimitedUseAppCheckTokens: Boolean,
    requestOptions: RequestOptions = RequestOptions(),
    appCheckTokenProvider: InteropAppCheckTokenProvider? = null,
    internalAuthProvider: InternalAuthProvider? = null,
  ) : this(
    templateUri,
    APIController(
      apiKey,
      "",
      requestOptions,
      "gl-kotlin/${KotlinVersion.CURRENT}-ai fire/${BuildConfig.VERSION_NAME}",
      firebaseApp,
      AppCheckHeaderProvider(
        TAG,
        useLimitedUseAppCheckTokens,
        appCheckTokenProvider,
        internalAuthProvider
      ),
    ),
  )

  /**
   * Generates an image, returning the result directly to the caller.
   *
   * @param templateId The ID of server prompt template.
   * @param inputs the inputs needed to fill in the prompt
   */
  public suspend fun generateImages(
    templateId: String,
    inputs: Map<String, Any>
  ): ImagenGenerationResponse<ImagenInlineImage> =
    try {
      controller
        .templateGenerateImage(
          "$templateUri$templateId",
          constructTemplateGenerateImageRequest(inputs)
        )
        .validate()
        .toPublicInline()
    } catch (e: Throwable) {
      throw FirebaseAIException.from(e)
    }

  private fun constructTemplateGenerateImageRequest(
    inputs: Map<String, Any>
  ): TemplateGenerateImageRequest {
    return TemplateGenerateImageRequest(inputs)
  }

  internal companion object {
    private val TAG = TemplateImagenModel::class.java.simpleName
    internal const val DEFAULT_FILTERED_ERROR =
      "Unable to show generated images. All images were filtered out because they violated Vertex AI's usage guidelines. You will not be charged for blocked images. Try rephrasing the prompt. If you think this was an error, send feedback."
  }
}
