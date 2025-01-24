package com.google.firebase.vertexai

import com.google.firebase.appcheck.interop.InteropAppCheckTokenProvider
import com.google.firebase.auth.internal.InternalAuthProvider
import com.google.firebase.vertexai.common.APIController
import com.google.firebase.vertexai.common.ContentBlockedException
import com.google.firebase.vertexai.common.PromptBlockedException
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

public class ImageModel
internal constructor(
  private val modelName: String,
  private val generationConfig: ImagenGenerationConfig? = null,
  private val safetySettings: ImagenSafetySettings? = null,
  private val controller: APIController,
) {
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

  public suspend fun generateImage(prompt: String): ImagenGenerationResponse<ImagenInlineImage> =
    try {
      controller
        .generateImage(constructRequest(prompt, null, generationConfig))
        .validate()
        .toPublicInline()
    } catch (e: Throwable) {
      throw FirebaseVertexAIException.from(e)
    }

  public suspend fun generateImage(
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
    private val TAG = ImageModel::class.java.simpleName
    internal const val DEFAULT_FILTERED_ERROR =
      "Unable to show generated images. All images were filtered out because they violated Vertex AI's usage guidelines. You will not be charged for blocked images. Try rephrasing the prompt. If you think this was an error, send feedback."
  }
}

private fun GenerateImageResponse.validate(): GenerateImageResponse {
  if (predictions.none { it.mimeType != null }) {
    throw ContentBlockedException(
      message = predictions.first { it.raiFilteredReason != null }.raiFilteredReason
          ?: ImageModel.DEFAULT_FILTERED_ERROR
    )
  }
  return this
}
