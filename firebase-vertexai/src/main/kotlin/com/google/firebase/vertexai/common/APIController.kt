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

package com.google.firebase.vertexai.common

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.options
import com.google.firebase.vertexai.common.server.FinishReason
import com.google.firebase.vertexai.common.server.GRpcError
import com.google.firebase.vertexai.common.server.GRpcErrorDetails
import com.google.firebase.vertexai.common.util.decodeToFlow
import com.google.firebase.vertexai.common.util.fullModelName
import com.google.firebase.vertexai.internal.GenerateImageRequest
import com.google.firebase.vertexai.internal.GenerateImageResponse
import com.google.firebase.vertexai.type.RequestOptions
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
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
import kotlinx.serialization.json.Json

internal val JSON = Json {
  ignoreUnknownKeys = true
  prettyPrint = false
  isLenient = true
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
internal class APIController
internal constructor(
  private val key: String,
  model: String,
  private val requestOptions: RequestOptions,
  httpEngine: HttpClientEngine,
  private val apiClient: String,
  private val headerProvider: HeaderProvider?,
) {

  constructor(
    key: String,
    model: String,
    requestOptions: RequestOptions,
    apiClient: String,
    headerProvider: HeaderProvider? = null,
  ) : this(key, model, requestOptions, OkHttp.create(), apiClient, headerProvider)

  private val model = fullModelName(model)

  private val client =
    HttpClient(httpEngine) {
      install(HttpTimeout) {
        requestTimeoutMillis = requestOptions.timeout.inWholeMilliseconds
        socketTimeoutMillis =
          max(180.seconds.inWholeMilliseconds, requestOptions.timeout.inWholeMilliseconds)
      }
      install(ContentNegotiation) { json(JSON) }
    }

  suspend fun generateContent(request: GenerateContentRequest): GenerateContentResponse =
    try {
      client
        .post("${requestOptions.endpoint}/${requestOptions.apiVersion}/$model:generateContent") {
          applyCommonConfiguration(request)
          applyHeaderProvider()
        }
        .also { validateResponse(it) }
        .body<GenerateContentResponse>()
        .validate()
    } catch (e: Throwable) {
      throw FirebaseCommonAIException.from(e)
    }

  suspend fun generateImage(request: GenerateImageRequest): GenerateImageResponse =
    try {
      client
        .post("${requestOptions.endpoint}/${requestOptions.apiVersion}/$model:predict") {
          applyCommonConfiguration(request)
          applyHeaderProvider()
        }
        .also { validateResponse(it) }
        .body<GenerateImageResponse>()
        .validate()
    } catch (e: Throwable) {
      throw FirebaseCommonAIException.from(e)
    }

  fun generateContentStream(request: GenerateContentRequest): Flow<GenerateContentResponse> =
    client
      .postStream<GenerateContentResponse>(
        "${requestOptions.endpoint}/${requestOptions.apiVersion}/$model:streamGenerateContent?alt=sse"
      ) {
        applyCommonConfiguration(request)
      }
      .map { it.validate() }
      .catch { throw FirebaseCommonAIException.from(it) }

  suspend fun countTokens(request: CountTokensRequest): CountTokensResponse =
    try {
      client
        .post("${requestOptions.endpoint}/${requestOptions.apiVersion}/$model:countTokens") {
          applyCommonConfiguration(request)
          applyHeaderProvider()
        }
        .also { validateResponse(it) }
        .body()
    } catch (e: Throwable) {
      throw FirebaseCommonAIException.from(e)
    }

  private fun HttpRequestBuilder.applyCommonConfiguration(request: Request) {
    when (request) {
      is GenerateContentRequest -> setBody<GenerateContentRequest>(request)
      is CountTokensRequest -> setBody<CountTokensRequest>(request)
      is GenerateImageRequest -> setBody<GenerateImageRequest>(request)
    }
    contentType(ContentType.Application.Json)
    header("x-goog-api-key", key)
    header("x-goog-api-client", apiClient)
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

  companion object {
    private val TAG = APIController::class.java.simpleName
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
      """URL not found. Please verify the location used to create the `FirebaseVertexAI` object
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
  getServiceDisabledErrorDetailsOrNull(error)?.let {
    val errorMessage =
      if (it.metadata?.get("service") == "firebasevertexai.googleapis.com") {
        """
        The Vertex AI in Firebase SDK requires the Vertex AI in Firebase API
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

private fun getServiceDisabledErrorDetailsOrNull(error: GRpcError): GRpcErrorDetails? {
  return error.details?.firstOrNull {
    it.reason == "SERVICE_DISABLED" && it.domain == "googleapis.com"
  }
}

private fun GenerateContentResponse.validate() = apply {
  if ((candidates?.isEmpty() != false) && promptFeedback == null) {
    throw SerializationException("Error deserializing response, found no valid fields")
  }
  promptFeedback?.blockReason?.let { throw PromptBlockedException(this) }
  candidates
    ?.mapNotNull { it.finishReason }
    ?.firstOrNull { it != FinishReason.STOP }
    ?.let { throw ResponseStoppedException(this) }
}

private fun GenerateImageResponse.validate() = apply {}
