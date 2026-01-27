/*
 * Copyright 2024 Google LLC
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

package com.google.firebase.ai.common

import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.common.util.decodeToFlow
import com.google.firebase.ai.common.util.fullModelName
import com.google.firebase.ai.type.APINotConfiguredException
import com.google.firebase.ai.type.CountTokensResponse
import com.google.firebase.ai.type.FinishReason
import com.google.firebase.ai.type.FirebaseAIException
import com.google.firebase.ai.type.GRpcErrorResponse
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.GenerativeBackendEnum
import com.google.firebase.ai.type.ImagenGenerationResponse
import com.google.firebase.ai.type.InvalidAPIKeyException
import com.google.firebase.ai.type.PromptBlockedException
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.RequestOptions
import com.google.firebase.ai.type.Response
import com.google.firebase.ai.type.ResponseStoppedException
import com.google.firebase.ai.type.SerializationException
import com.google.firebase.ai.type.ServerException
import com.google.firebase.ai.type.ServiceDisabledException
import com.google.firebase.ai.type.UnsupportedUserLocationException
import com.google.firebase.options
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.withCharset
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.charsets.Charset
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import kotlin.math.max
import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.ClassDiscriminatorMode
import kotlinx.serialization.json.Json

@OptIn(ExperimentalSerializationApi::class)
internal val JSON = Json {
  ignoreUnknownKeys = true
  prettyPrint = false
  isLenient = true
  explicitNulls = false
  classDiscriminatorMode = ClassDiscriminatorMode.NONE
}

/**
 * Backend class for interfacing with the Gemini API.
 *
 * This class handles making HTTP requests to the API and streaming the responses back.
 *
 * @param httpEngine The HTTP client engine to be used for making requests. Defaults to CIO engine.
 * Exposed primarily for DI in tests.
 * @property key The API key used for authentication.
 * @property model The model to use for generation.
 * @property apiClient The value to pass in the `x-goog-api-client` header.
 * @property headerProvider A provider that generates extra headers to include in all HTTP requests.
 */
@OptIn(PublicPreviewAPI::class)
internal class APIController
internal constructor(
  private val key: String,
  model: String,
  private val requestOptions: RequestOptions,
  httpEngine: HttpClientEngine,
  private val apiClient: String,
  private val firebaseApp: FirebaseApp,
  private val appVersion: Int = 0,
  private val googleAppId: String,
  private val headerProvider: HeaderProvider?,
  private val backend: GenerativeBackend? = null
) {

  constructor(
    key: String,
    model: String,
    requestOptions: RequestOptions,
    apiClient: String,
    firebaseApp: FirebaseApp,
    headerProvider: HeaderProvider? = null,
    backend: GenerativeBackend? = null,
  ) : this(
    key,
    model,
    requestOptions,
    OkHttp.create(),
    apiClient,
    firebaseApp,
    getVersionNumber(firebaseApp),
    firebaseApp.options.applicationId,
    headerProvider,
    backend
  )

  private val model = fullModelName(model)

  private val client =
    HttpClient(httpEngine) {
      install(HttpTimeout) {
        requestTimeoutMillis = requestOptions.timeout.inWholeMilliseconds
        socketTimeoutMillis =
          max(180.seconds.inWholeMilliseconds, requestOptions.timeout.inWholeMilliseconds)
      }
      install(WebSockets)
      install(ContentNegotiation) { json(JSON) }
    }

  suspend fun generateContent(request: GenerateContentRequest): GenerateContentResponse.Internal =
    try {
      client
        .post("${requestOptions.endpoint}/${requestOptions.apiVersion}/$model:generateContent") {
          applyCommonConfiguration(request)
          applyHeaderProvider()
        }
        .also { validateResponse(it) }
        .body<GenerateContentResponse.Internal>()
        .validate()
    } catch (e: Throwable) {
      throw FirebaseAIException.from(e)
    }

  suspend fun templateGenerateContent(
    templateId: String,
    request: TemplateGenerateContentRequest
  ): GenerateContentResponse.Internal =
    try {
      client
        .post(
          "${requestOptions.endpoint}/${requestOptions.apiVersion}/$templateId:templateGenerateContent"
        ) {
          applyCommonConfiguration(request)
          applyHeaderProvider()
        }
        .also { validateResponse(it) }
        .body<GenerateContentResponse.Internal>()
        .validate()
    } catch (e: Throwable) {
      throw FirebaseAIException.from(e)
    }

  fun templateGenerateContentStream(
    templateId: String,
    request: TemplateGenerateContentRequest
  ): Flow<GenerateContentResponse.Internal> =
    client
      .postStream<GenerateContentResponse.Internal>(
        "${requestOptions.endpoint}/${requestOptions.apiVersion}/$templateId:templateStreamGenerateContent?alt=sse"
      ) {
        applyCommonConfiguration(request)
      }
      .map { it.validate() }
      .catch { throw FirebaseAIException.from(it) }

  suspend fun generateImage(request: GenerateImageRequest): ImagenGenerationResponse.Internal =
    try {
      client
        .post("${requestOptions.endpoint}/${requestOptions.apiVersion}/$model:predict") {
          applyCommonConfiguration(request)
          applyHeaderProvider()
        }
        .also { validateResponse(it) }
        .body<ImagenGenerationResponse.Internal>()
    } catch (e: Throwable) {
      throw FirebaseAIException.from(e)
    }

  suspend fun templateGenerateImage(
    templateId: String,
    request: TemplateGenerateImageRequest
  ): ImagenGenerationResponse.Internal =
    try {
      client
        .post(
          "${requestOptions.endpoint}/${requestOptions.apiVersion}/$templateId:templatePredict"
        ) {
          applyCommonConfiguration(request)
          applyHeaderProvider()
        }
        .also { validateResponse(it) }
        .body<ImagenGenerationResponse.Internal>()
    } catch (e: Throwable) {
      throw FirebaseAIException.from(e)
    }

  private fun getBidiEndpoint(location: String): String =
    when (backend?.backend) {
      GenerativeBackendEnum.VERTEX_AI,
      null ->
        "wss://firebasevertexai.googleapis.com/ws/google.firebase.vertexai.v1beta.LlmBidiService/BidiGenerateContent/locations/$location?key=$key"
      GenerativeBackendEnum.GOOGLE_AI ->
        "wss://firebasevertexai.googleapis.com/ws/google.firebase.vertexai.v1beta.GenerativeService/BidiGenerateContent?key=$key"
    }

  suspend fun getWebSocketSession(location: String): DefaultClientWebSocketSession =
    client.webSocketSession(getBidiEndpoint(location)) { applyCommonHeaders() }

  fun generateContentStream(
    request: GenerateContentRequest
  ): Flow<GenerateContentResponse.Internal> =
    client
      .postStream<GenerateContentResponse.Internal>(
        "${requestOptions.endpoint}/${requestOptions.apiVersion}/$model:streamGenerateContent?alt=sse"
      ) {
        applyCommonConfiguration(request)
      }
      .map { it.validate() }
      .catch { throw FirebaseAIException.from(it) }

  suspend fun countTokens(request: CountTokensRequest): CountTokensResponse.Internal =
    try {
      client
        .post("${requestOptions.endpoint}/${requestOptions.apiVersion}/$model:countTokens") {
          applyCommonConfiguration(request)
          applyHeaderProvider()
        }
        .also { validateResponse(it) }
        .body()
    } catch (e: Throwable) {
      throw FirebaseAIException.from(e)
    }

  private fun HttpRequestBuilder.applyCommonHeaders() {
    contentType(ContentType.Application.Json)
    header("x-goog-api-key", key)
    header("x-goog-api-client", apiClient)
    header("X-Android-Package", firebaseApp.applicationContext.packageName)
    header("X-Android-Cert", getSigningCertFingerprint() ?: "")
    if (firebaseApp.isDataCollectionDefaultEnabled) {
      header("X-Firebase-AppId", googleAppId)
      header("X-Firebase-AppVersion", appVersion)
    }
  }
  private fun HttpRequestBuilder.applyCommonConfiguration(request: Request) {
    when (request) {
      is GenerateContentRequest -> setBody<GenerateContentRequest>(request)
      is CountTokensRequest -> setBody<CountTokensRequest>(request)
      is GenerateImageRequest -> setBody<GenerateImageRequest>(request)
      is TemplateGenerateContentRequest -> setBody<TemplateGenerateContentRequest>(request)
      is TemplateGenerateImageRequest -> setBody<TemplateGenerateImageRequest>(request)
    }
    applyCommonHeaders()
  }

  private suspend fun HttpRequestBuilder.applyHeaderProvider() {
    if (headerProvider != null) {
      try {
        withTimeout(headerProvider.timeout) {
          for ((tag, value) in headerProvider.generateHeaders()) {
            header(tag, value)
          }
        }
      } catch (e: TimeoutCancellationException) {
        Log.w(TAG, "HeaderProvided timed out without generating headers, ignoring")
      }
    }
  }

  /**
   * Makes a POST request to the specified [url] and returns a [Flow] of deserialized response
   * objects of type [R]. The response is expected to be a stream of JSON objects that are parsed in
   * real-time as they are received from the server.
   *
   * This function is intended for internal use within the client that handles streaming responses.
   *
   * Example usage:
   * ```
   * val client: HttpClient = HttpClient(CIO)
   * val request: Request = GenerateContentRequest(...)
   * val url: String = "http://example.com/stream"
   *
   * val responses: GenerateContentResponse = client.postStream(url) {
   *   setBody(request)
   *   contentType(ContentType.Application.Json)
   * }
   * responses.collect {
   *   println("Got a response: $it")
   * }
   * ```
   *
   * @param R The type of the response object.
   * @param url The URL to which the POST request will be made.
   * @param config An optional [HttpRequestBuilder] callback for request configuration.
   * @return A [Flow] of response objects of type [R].
   */
  private inline fun <reified R : Response> HttpClient.postStream(
    url: String,
    crossinline config: HttpRequestBuilder.() -> Unit = {},
  ): Flow<R> = channelFlow {
    launch(CoroutineName("postStream")) {
      preparePost(url) {
          applyHeaderProvider()
          config()
        }
        .execute {
          validateResponse(it)

          val channel = it.bodyAsChannel()
          val flow = JSON.decodeToFlow<R>(channel)

          flow.collect { send(it) }
        }
    }
  }

  @OptIn(ExperimentalStdlibApi::class)
  private fun getSigningCertFingerprint(): String? {
    val signature = getCurrentSignature() ?: return null
    try {
      val messageDigest = MessageDigest.getInstance("SHA-1")
      val digest = messageDigest.digest(signature.toByteArray())
      return digest.toHexString(HexFormat.UpperCase)
    } catch (e: NoSuchAlgorithmException) {
      Log.w(TAG, "No support for SHA-1 algorithm found.", e)
      return null
    }
  }

  @Suppress("DEPRECATION")
  private fun getCurrentSignature(): Signature? {
    val packageName = firebaseApp.applicationContext.packageName
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
      val packageInfo =
        firebaseApp.applicationContext.packageManager.getPackageInfo(
          packageName,
          PackageManager.GET_SIGNATURES
        )
      val signatures = packageInfo?.signatures ?: return null
      if (signatures.size > 1) {
        Log.d(
          TAG,
          "Multiple certificates found. On Android < P, certificate order is non-deterministic; an rotated/old cert may be used."
        )
      }
      return signatures.firstOrNull()
    }
    val packageInfo =
      firebaseApp.applicationContext.packageManager.getPackageInfo(
        packageName,
        PackageManager.GET_SIGNING_CERTIFICATES
      )
    val signingInfo = packageInfo?.signingInfo ?: return null
    if (signingInfo.hasMultipleSigners()) {
      Log.d(TAG, "App has been signed with multiple certificates. Defaulting to the first one")
      return signingInfo.apkContentsSigners.first()
    } else {
      // The `signingCertificateHistory` contains a sorted list of certificates used to sign this
      // artifact, with the original one first, and once it's rotated, the current one is added at
      // the end of the list. See the method's refdocs for more info.
      return signingInfo.signingCertificateHistory.lastOrNull()
    }
  }

  companion object {
    private val TAG = APIController::class.java.simpleName

    private fun getVersionNumber(app: FirebaseApp): Int {
      try {
        val context = app.applicationContext
        return context.packageManager.getPackageInfo(context.packageName, 0).versionCode
      } catch (e: Exception) {
        Log.d(TAG, "Error while getting app version: ${e.message}")
        return 0
      }
    }
  }
}

internal interface HeaderProvider {
  val timeout: Duration

  suspend fun generateHeaders(): Map<String, String>
}

private suspend fun validateResponse(response: HttpResponse) {
  if (response.status == HttpStatusCode.OK) return

  val htmlContentType = ContentType.Text.Html.withCharset(Charset.forName("utf-8"))
  if (response.status == HttpStatusCode.NotFound && response.contentType() == htmlContentType)
    throw ServerException(
      """URL not found. Please verify the location used to create the `FirebaseAI` object
          | See https://cloud.google.com/vertex-ai/generative-ai/docs/learn/locations#available-regions
          | for the list of available locations. Raw response: ${response.bodyAsText()}"""
        .trimMargin()
    )
  val text = response.bodyAsText()
  val error =
    try {
      JSON.decodeFromString<GRpcErrorResponse>(text).error
    } catch (e: Throwable) {
      throw ServerException("Unexpected Response:\n$text $e")
    }
  val message = error.message
  if (message.contains("API key not valid")) {
    throw InvalidAPIKeyException(message)
  }
  // TODO (b/325117891): Use a better method than string matching.
  if (message == "User location is not supported for the API use.") {
    throw UnsupportedUserLocationException()
  }
  if (message.contains("quota")) {
    throw QuotaExceededException(message)
  }
  if (message.contains("The prompt could not be submitted")) {
    throw PromptBlockedException(message)
  }
  if (message.contains("genai config not found")) {
    throw APINotConfiguredException(
      "The Gemini Developer API is not enabled, to enable and configure, see https://firebase.google.com/docs/ai-logic/faq-and-troubleshooting?api=dev#error-genai-config-not-found"
    )
  }
  getServiceDisabledErrorDetailsOrNull(error)?.let {
    val errorMessage =
      if (it.metadata?.get("service") == "firebasevertexai.googleapis.com") {
        """
        The Firebase AI SDK requires the Vertex AI in Firebase API
        (`firebasevertexai.googleapis.com`) to be enabled in your Firebase project. Enable this API
        by visiting the Firebase Console at
        https://console.firebase.google.com/project/${Firebase.options.projectId}/genai
        and clicking "Get started". If you enabled this API recently, wait a few minutes for the
        action to propagate to our systems and then retry.
      """
          .trimIndent()
      } else {
        error.message
      }

    throw ServiceDisabledException(errorMessage)
  }
  throw ServerException(message)
}

private fun getServiceDisabledErrorDetailsOrNull(
  error: GRpcErrorResponse.GRpcError
): GRpcErrorResponse.GRpcError.GRpcErrorDetails? {
  return error.details?.firstOrNull {
    it.reason == "SERVICE_DISABLED" && it.domain == "googleapis.com"
  }
}

private fun GenerateContentResponse.Internal.validate() = apply {
  if ((candidates?.isEmpty() != false) && promptFeedback == null) {
    throw SerializationException("Error deserializing response, found no valid fields")
  }
  promptFeedback?.blockReason?.let { throw PromptBlockedException(this.toPublic(), null, null) }
  candidates
    ?.mapNotNull { it.finishReason }
    ?.firstOrNull { it != FinishReason.Internal.STOP }
    ?.let { throw ResponseStoppedException(this.toPublic()) }
}
