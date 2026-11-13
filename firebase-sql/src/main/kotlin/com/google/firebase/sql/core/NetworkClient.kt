/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.sql.core

import com.google.firebase.sql.FirebaseSqlException
import com.google.firebase.sql.FirebaseSqlExceptionCategory
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink

/**
 * The HTTP response to a request, or the error of a request that got no HTTP response.
 *
 * @property status The HTTP status code, for example 200 or 404. It is 0 when the request got no
 * HTTP response.
 * @property statusText The HTTP status text, for example "OK" or "Not Found". It is empty for an
 * HTTP/2 response. When the request got no HTTP response, it names the failure, for example
 * "Timeout".
 * @property body The raw response body of a successful response, or `null` for an error.
 * @property count The total number of rows, read from the `Content-Range` header. It is `null` when
 * the server did not count the rows.
 * @property range The positions of the returned rows, read from the `Content-Range` header, for
 * example `0..19` for `0-19/250`. It is `null` when the header has no positions, for example `*`.
 * @property error The error to throw for a request that failed: a [FirebaseSqlException] with the
 * HTTP status for an error response or for a response that could not be read, or the error of a
 * request that got no HTTP response, as it is: the [IOException] of the network, an
 * `InterruptedIOException` for a timeout, the error of Firebase Authentication, or an
 * [IllegalStateException] for a terminated instance.
 * @property preferenceApplied The `Preference-Applied` response header, or `null` when the server
 * did not send one.
 * @property isTransportError `true` when the request got no HTTP response because of a network
 * error, and `false` for an HTTP response, a timeout, a Firebase Authentication token error, or a
 * terminated instance.
 */
internal data class NetworkResponse(
  val status: Int,
  val statusText: String,
  val body: String?,
  val count: Long? = null,
  val range: LongRange? = null,
  val error: Exception? = null,
  val preferenceApplied: String? = null,
  val isTransportError: Boolean = false
)

/** Abstract interface for the HTTP transport layer. */
internal interface NetworkClient {
  /**
   * Sends the [request] to the service at [serviceUrl].
   *
   * @param serviceUrl The URL of the service, to which the path segments of [request] are appended.
   * @param request The request, including the headers to send.
   * @return The HTTP response, or a response with a status of 0 and an error when the request got
   * no HTTP response.
   */
  suspend fun sendRequest(serviceUrl: HttpUrl, request: QueryRequest): NetworkResponse
}

/**
 * A [NetworkClient] that sends requests with OkHttp.
 *
 * Each of the [QueryRequest.pathSegments] is appended to the service URL as one percent-encoded
 * path segment. `GET` and `HEAD` requests carry no body, and the other requests without a body,
 * such as a `DELETE`, carry an empty one. A [String] or [ByteArray] body is sent as it is, a
 * [JsonElement] is sent as its JSON text, and any other value is encoded to JSON first. The body is
 * labeled with the `Content-Type` header of the request, so that PostgREST reads a `text/csv` body
 * as CSV, or with `application/json; charset=utf-8` when the request has no such header.
 *
 * Every body is one-shot. OkHttp sends a request again by itself after some failures, for example
 * after a 408 or a network error, but never a request with a one-shot body once it has sent it,
 * because it could change the data twice. The client of [newOkHttpClient] does not send a request
 * again after a 503: the retries of [OperationsExecutor] handle it.
 *
 * The total row count is read from the `Content-Range` header, which PostgREST sends as
 * `<first>-<last>/<total>`. Every error response becomes a [FirebaseSqlException] with the HTTP
 * status. A PostgREST error gives its `message`, `code`, `details`, and `hint` fields. An error of
 * the Google API layer in front of PostgREST, `{"error": {...}}`, gives its `message`, its `status`
 * as the code, and its `details`. When an error has no message, or a blank one, the message is
 * `HTTP <status>: <body>`. A request that gets no response, for example because the server is
 * unreachable, returns a status of 0 and the [IOException] as its error. A response that cannot be
 * read because of another exception gives a [FirebaseSqlException] of the category
 * [FirebaseSqlExceptionCategory.CLIENT], with the HTTP status and that exception as its cause.
 *
 * Cancelling the coroutine of a request cancels its HTTP call.
 *
 * @param okHttpClient Sends the requests. The instances of one `FirebaseApp` share one client,
 * which is shut down when the app is deleted.
 */
internal class OkHttpNetworkClient(private val okHttpClient: OkHttpClient) : NetworkClient {

  /** Creates a client with its own [OkHttpClient], which [newOkHttpClient] makes. */
  constructor() : this(newOkHttpClient())

  override suspend fun sendRequest(serviceUrl: HttpUrl, request: QueryRequest): NetworkResponse {
    // addPathSegment() replaces the empty last segment of a URL that ends with `/`.
    val urlBuilder = serviceUrl.newBuilder()
    request.pathSegments.forEach { urlBuilder.addPathSegment(it) }
    request.queryParams.forEach { (key, value) -> urlBuilder.addQueryParameter(key, value) }

    val requestBuilder = Request.Builder().url(urlBuilder.build())
    request.headers.forEach { (key, value) -> requestBuilder.addHeader(key, value) }
    requestBuilder.method(request.httpMethod, requestBody(request))
    val call = okHttpClient.newCall(requestBuilder.build())
    return suspendCancellableCoroutine { continuation ->
      continuation.invokeOnCancellation { call.cancel() }
      call.enqueue(
        object : Callback {
          override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resume(transportError(e))
          }

          override fun onResponse(call: Call, response: Response) {
            // OkHttp only logs an IOException thrown here, so it has to be reported explicitly. It
            // rethrows any other exception on its own thread, which crashes the app, without ending
            // the call, so that exception is reported too.
            val result =
              try {
                response.use { toNetworkResponse(it) }
              } catch (e: IOException) {
                transportError(e)
              } catch (e: Exception) {
                unreadableResponse(response, e)
              }
            if (continuation.isActive) continuation.resume(result)
          }
        }
      )
    }
  }

  private companion object {
    val METHODS_WITHOUT_BODY = setOf("GET", "HEAD")

    /** The error code in a `Proxy-Status: PostgREST; error=<code>` header. */
    val PROXY_STATUS_ERROR = Regex("""(?i)\bPostgREST\b.*?;\s*error=([^;,\s]+)""")
    val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    fun requestBody(request: QueryRequest): RequestBody? {
      if (request.httpMethod in METHODS_WITHOUT_BODY) return null
      val bytes =
        when (val body = request.body) {
          null -> byteArrayOf()
          is String -> body.toByteArray(Charsets.UTF_8)
          is ByteArray -> body
          is JsonElement -> body.toString().toByteArray(Charsets.UTF_8)
          else -> JsonValueEncoder.encode(body).toString().toByteArray(Charsets.UTF_8)
        }
      val contentType =
        request.headers.entries.firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
      // OkHttp replaces the Content-Type header with the media type of the body. A media type that
      // OkHttp cannot parse is left out, so that the header of the request is sent as it is.
      val mediaType =
        if (contentType == null) JSON_MEDIA_TYPE else contentType.value.toMediaTypeOrNull()
      return OneShotRequestBody(bytes, mediaType)
    }

    /**
     * Returns the positions in a `Content-Range` header such as `0-19/250`, or `null` when the
     * header is missing or has no positions, for example when it starts with `*`.
     */
    fun rangeOf(contentRange: String?): LongRange? {
      val positions = contentRange?.substringBefore('/')?.split('-') ?: return null
      if (positions.size != 2) return null
      val first = positions[0].trim().toLongOrNull() ?: return null
      val last = positions[1].trim().toLongOrNull() ?: return null
      return if (first <= last) first..last else null
    }

    fun toNetworkResponse(response: Response): NetworkResponse {
      val body = response.body?.string().orEmpty()
      return if (response.isSuccessful) {
        NetworkResponse(
          status = response.code,
          statusText = response.message,
          body = body,
          count = response.header("Content-Range")?.substringAfterLast('/', "")?.toLongOrNull(),
          range = rangeOf(response.header("Content-Range")),
          preferenceApplied = response.header("Preference-Applied")
        )
      } else {
        NetworkResponse(
          status = response.code,
          statusText = response.message,
          body = null,
          error = parseError(response.code, body, response.header("Proxy-Status")),
          preferenceApplied = response.header("Preference-Applied")
        )
      }
    }

    /**
     * Returns the error of an HTTP error response with the given [status] and [body].
     *
     * @param proxyStatus The `Proxy-Status` header. The server strips the body of the response to a
     * HEAD request, but still names the PostgREST error code in this header.
     */
    fun parseError(status: Int, body: String, proxyStatus: String? = null): FirebaseSqlException {
      val fallbackMessage = "HTTP $status: $body"
      val proxyStatusCode = proxyStatus?.let { PROXY_STATUS_ERROR.find(it) }?.groupValues?.get(1)
      if (body.isBlank() && proxyStatusCode != null) {
        return FirebaseSqlException(
          message = fallbackMessage,
          status = status,
          code = proxyStatusCode,
          category = FirebaseSqlExceptionCategory.POSTGREST
        )
      }
      // A body that is not a JSON object, for example the error page of a proxy, does not come
      // from PostgREST, which always answers an error with a JSON object.
      val json =
        runCatching { Json.parseToJsonElement(body) }.getOrNull() as? JsonObject
          ?: return FirebaseSqlException(
            message = fallbackMessage,
            status = status,
            category = FirebaseSqlExceptionCategory.PLATFORM
          )
      // The Google API layer in front of PostgREST, which checks the tokens and the quotas for
      // example, answers with {"error": {"code", "message", "status", "details"}}.
      val googleError = json["error"] as? JsonObject
      if (googleError != null) {
        return FirebaseSqlException(
          message = googleError.message() ?: fallbackMessage,
          status = status,
          code = googleError.text("status"),
          details = googleError.text("details"),
          category = FirebaseSqlExceptionCategory.PLATFORM
        )
      }
      val isPostgrestError = json.containsKey("message") || json.containsKey("code")
      return FirebaseSqlException(
        message = json.message() ?: fallbackMessage,
        status = status,
        code = json.text("code"),
        details = json.text("details"),
        hint = json.text("hint"),
        category =
          if (isPostgrestError) FirebaseSqlExceptionCategory.POSTGREST
          else FirebaseSqlExceptionCategory.PLATFORM
      )
    }

    fun JsonObject.text(key: String): String? =
      when (val value = get(key)) {
        null,
        JsonNull -> null
        is JsonPrimitive -> value.content
        else -> value.toString()
      }

    /**
     * The `message` field of an error body, or `null` when it is missing or blank. An exception
     * cannot have an empty message: `FirebaseException` rejects it with an
     * [IllegalArgumentException]. PostgREST sends an empty message, for example, when a function
     * raises an exception whose message is empty.
     */
    fun JsonObject.message(): String? = text("message")?.takeIf { it.isNotBlank() }

    /**
     * Returns the error of a [response] that could not be read because of [e], an unexpected
     * exception that is not an [IOException], so that the request ends with that error. The error
     * has the HTTP status of the response, because the server answered.
     */
    fun unreadableResponse(response: Response, e: Exception) =
      NetworkResponse(
        status = response.code,
        statusText = response.message,
        body = null,
        error =
          FirebaseSqlException(
            message = "The response could not be read: $e",
            status = response.code,
            category = FirebaseSqlExceptionCategory.CLIENT,
            cause = e
          )
      )

    fun transportError(e: IOException) =
      NetworkResponse(
        status = 0,
        statusText = "Transport Error",
        body = null,
        error = e,
        isTransportError = true
      )
  }
}

/**
 * A body of [bytes] that OkHttp sends only once: it never sends a request with a one-shot body
 * again by itself, for example after a 408, or after a network error once the request was sent.
 */
private class OneShotRequestBody(private val bytes: ByteArray, private val mediaType: MediaType?) :
  RequestBody() {
  override fun contentType(): MediaType? = mediaType

  override fun contentLength(): Long = bytes.size.toLong()

  override fun isOneShot(): Boolean = true

  override fun writeTo(sink: BufferedSink) {
    sink.write(bytes)
  }
}

/**
 * Returns a new [OkHttpClient] for the requests of the SDK. Its read timeout is longer than the
 * deadline of the server, so that OkHttp does not fail a request that the server still runs, and
 * may commit. The timeout of each attempt is set by [OperationsExecutor].
 *
 * The client removes the `Retry-After` header of every 503 response, which PostgREST sets to 0.
 * After a 503 with `Retry-After: 0`, OkHttp would send a GET or HEAD request again at once by
 * itself, without the wait and the `X-Retry-Count` header of the retries of [OperationsExecutor]:
 * each attempt would reach the server twice. A network interceptor sees the response before OkHttp
 * decides whether to send the request again.
 */
internal fun newOkHttpClient(): OkHttpClient =
  OkHttpClient.Builder()
    .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    .addNetworkInterceptor { chain ->
      val response = chain.proceed(chain.request())
      if (response.code != HTTP_UNAVAILABLE) response
      else response.newBuilder().removeHeader("Retry-After").build()
    }
    .build()

/** Longer than the 90 seconds after which the server cancels a request. */
private const val READ_TIMEOUT_SECONDS = 100L

/** The status after which OkHttp would send a request again at once, given `Retry-After: 0`. */
private const val HTTP_UNAVAILABLE = 503
