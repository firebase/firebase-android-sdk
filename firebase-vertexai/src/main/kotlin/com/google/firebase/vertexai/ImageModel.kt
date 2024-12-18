package com.google.firebase.vertexai

import android.util.Log
import com.google.firebase.appcheck.interop.InteropAppCheckTokenProvider
import com.google.firebase.auth.internal.InternalAuthProvider
import com.google.firebase.vertexai.common.APIController
import com.google.firebase.vertexai.common.HeaderProvider
import com.google.firebase.vertexai.internal.GenerateImageRequest
import com.google.firebase.vertexai.internal.ImagenParameters
import com.google.firebase.vertexai.internal.ImagenPromptInstance
import com.google.firebase.vertexai.internal.util.toInternal
import com.google.firebase.vertexai.internal.util.toPublicGCS
import com.google.firebase.vertexai.internal.util.toPublicInline
import com.google.firebase.vertexai.type.FirebaseVertexAIException
import com.google.firebase.vertexai.type.ImageSafetySettings
import com.google.firebase.vertexai.type.ImagenGCSImage
import com.google.firebase.vertexai.type.ImagenGenerationConfig
import com.google.firebase.vertexai.type.ImagenGenerationResponse
import com.google.firebase.vertexai.type.ImagenImageRepresentible
import com.google.firebase.vertexai.type.ImagenInlineImage
import com.google.firebase.vertexai.type.ImagenModelConfig
import com.google.firebase.vertexai.type.PromptBlockedException
import com.google.firebase.vertexai.type.RequestOptions
import kotlinx.coroutines.tasks.await
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

public class ImageModel
internal constructor(
  private val modelName: String,
  private val generationConfig: ImagenModelConfig? = null,
  private val safetySettings: ImageSafetySettings? = null,
  private val controller: APIController,
) {
  @JvmOverloads
  internal constructor(
    modelName: String,
    apiKey: String,
    generationConfig: ImagenModelConfig? = null,
    safetySettings: ImageSafetySettings? = null,
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
      object : HeaderProvider {
        override val timeout: Duration
          get() = 10.seconds

        override suspend fun generateHeaders(): Map<String, String> {
          val headers = mutableMapOf<String, String>()
          if (appCheckTokenProvider == null) {
            Log.w(TAG, "AppCheck not registered, skipping")
          } else {
            val token = appCheckTokenProvider.getToken(false).await()

            if (token.error != null) {
              Log.w(TAG, "Error obtaining AppCheck token", token.error)
            }
            // The Firebase App Check backend can differentiate between apps without App Check, and
            // wrongly configured apps by verifying the value of the token, so it always needs to be
            // included.
            headers["X-Firebase-AppCheck"] = token.token
          }

          if (internalAuthProvider == null) {
            Log.w(TAG, "Auth not registered, skipping")
          } else {
            try {
              val token = internalAuthProvider.getAccessToken(false).await()

              headers["Authorization"] = "Firebase ${token.token!!}"
            } catch (e: Exception) {
              Log.w(TAG, "Error getting Auth token ", e)
            }
          }

          return headers
        }
      },
    ),
  )

  public suspend fun generateImage(
    prompt: String,
    config: ImagenGenerationConfig?,
  ): ImagenGenerationResponse<ImagenInlineImage> =
    try {
      controller.generateImage(constructRequest(prompt, null, config)).toPublicInline().validate()
    } catch (e: Throwable) {
      throw FirebaseVertexAIException.from(e)
    }

  public suspend fun generateImage(
    prompt: String,
    gcsUri: String,
    config: ImagenGenerationConfig?,
  ): ImagenGenerationResponse<ImagenGCSImage> =
    try {
      controller.generateImage(constructRequest(prompt, gcsUri, config)).toPublicGCS().validate()
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
    internal const val DEFAULT_FILTERED_ERROR = "Unable to show generated images. All images were filtered out because they violated Vertex AI's usage guidelines. You will not be charged for blocked images. Try rephrasing the prompt. If you think this was an error, send feedback."
  }
}

private fun <T : ImagenImageRepresentible> ImagenGenerationResponse<T>.validate():
  ImagenGenerationResponse<T> {
  if (images.isEmpty()) {
    throw PromptBlockedException(message = filteredReason ?: ImageModel.DEFAULT_FILTERED_ERROR)
  }
  return this
}
