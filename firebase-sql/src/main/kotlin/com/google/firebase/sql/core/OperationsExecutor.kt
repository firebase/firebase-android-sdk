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

import android.os.Build
import com.google.firebase.appcheck.interop.InteropAppCheckTokenProvider
import com.google.firebase.auth.internal.InternalAuthProvider
import com.google.firebase.inject.Deferred
import com.google.firebase.sql.BuildConfig
import com.google.firebase.sql.FirebaseSqlException
import com.google.firebase.sql.FirebaseSqlExceptionCategory
import com.google.firebase.sql.ReservedHeaderException
import java.io.InterruptedIOException
import java.util.Locale
import java.util.TreeMap
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl

/**
 * Adds the headers that every request needs, and sends the request with the [NetworkClient].
 *
 * HTTP header names are case-insensitive, so a header is never sent twice in different cases. The
 * headers of a request win over the global [headers], which win over the default headers
 * (`Content-Type`, `Accept-Profile`, and `Content-Profile`). `Prefer` is the exception: it holds a
 * list of preferences, so a global `Prefer` is added after the preferences of the request, and
 * PostgREST, which uses the first value of each preference, applies those of the request first.
 *
 * Every request also carries headers that only the SDK sets: `x-goog-api-client` with the versions
 * of Kotlin, Android, and the SDK, `x-client-version` with the platform and the version of the SDK,
 * which the server uses for Cloud Monitoring, and `x-firebase-gmpid` with the Firebase app ID, when
 * there is one. The SDK does not send `x-goog-request-params`, which is for gRPC only, or
 * `x-firebase-sqlconnect-affinity`, because the load balancer reads the same key, the project ID
 * and the service ID, from the path of the URL. Nor does it send the heartbeat header
 * `X-Firebase-Client`, which the server ignores, or `X-Firebase-Client-Log-Type`. An app cannot set
 * any of these headers either: they are among the [RESERVED_HEADERS].
 *
 * The Firebase Authentication token of the signed-in user is sent in the `x-firebase-auth-token`
 * header. Without a signed-in user, the request is sent without a token, so that the server decides
 * what a signed-out user can do. If the token of the signed-in user cannot be retrieved, the
 * request is not sent, because it would run with the permissions of a signed-out user.
 *
 * The Firebase App Check token is sent in the `X-Firebase-AppCheck` header. If App Check fails to
 * provide a valid token, the request carries the placeholder token that App Check returns with its
 * error, so that the server decides whether App Check is enforced.
 *
 * An [AuthTokenManager] and an [AppCheckTokenManager] get the tokens. When the server rejects a
 * token with a 401, the request is sent once more with refreshed tokens, and the requests that the
 * server rejected with the same token cause one refresh.
 *
 * A GET or HEAD request is sent again, up to [maxRetries] times, when it fails with a network error
 * or the server answers 502 or 503, which are transient: for example, PostgREST answers 503 while
 * it loads its schema cache. Before each retry, the SDK waits for [retryDelayMillis], by default
 * about 1, 2, 4 seconds, and so on, with a random part (see [defaultRetryDelayMillis]), and sends
 * the number of the retry in the `X-Retry-Count` header. It ignores the `Retry-After` header, which
 * PostgREST sets to 0 on every 503. The SDK does not retry the other requests after these failures,
 * because a request that changes data may have run even though it failed.
 * [QueryRequest.retryEnabled] turns the retries off for one request. Cancelling the coroutine also
 * cancels the wait.
 *
 * Each attempt that takes longer than [timeoutMillis] is cancelled, and is not sent again.
 *
 * [close] stops the requests in flight at once, whether they are getting their tokens, waiting for
 * the server, or waiting before a retry: their [execute] returns a response with a status of 0 and
 * the error [CLOSED_MESSAGE]. A request that starts after [close] throws an [IllegalStateException]
 * .
 *
 * @param schemaName The schema of the requests. PostgREST reads the schema from the
 * `Content-Profile` header of POST, PUT, PATCH, and DELETE requests, and from the `Accept-Profile`
 * header of the other requests. `null`, the default of [SqlServiceSettings], sends no header, and
 * the server uses its first exposed schema: the schema of the service, which is the only one that
 * Firebase SQL exposes by default. A header that names a schema that the server does not expose
 * fails with a `PGRST106` error, so `public` is not a safe default.
 * @param serviceUrl The URL of the service, to which the path of each request is appended.
 * @param authDeferred Provides the Firebase Authentication token of the requests, or `null` to send
 * none.
 * @param appCheckDeferred Provides the Firebase App Check token of the requests, or `null` to send
 * none.
 * @param networkClient Sends the requests.
 * @param headers Global headers that are sent with every request, unless the request sets the same
 * header.
 * @param appId The Firebase app ID, which is sent in the `x-firebase-gmpid` header, or `null` to
 * send none.
 * @param timeoutMillis The longest time in milliseconds that each attempt can take on the network:
 * [ATTEMPT_TIMEOUT_MILLIS], which tests make shorter.
 * @param maxRetries How many times a GET or HEAD request is sent again after a transient failure:
 * [MAX_RETRIES], which tests change.
 * @param retryDelayMillis Returns how long to wait before the retry after the attempt number
 * `attempt`, from 0: [defaultRetryDelayMillis], which tests replace.
 * @param blockingDispatcher Looks up the token providers, which can read the disk: the blocking
 * executor of Firebase in the app.
 * @param nonBlockingDispatcher Runs the requests and their token requests: the lightweight executor
 * of Firebase in the app.
 */
internal open class OperationsExecutor(
  val schemaName: String?,
  private val serviceUrl: HttpUrl,
  authDeferred: Deferred<InternalAuthProvider>? = null,
  appCheckDeferred: Deferred<InteropAppCheckTokenProvider>? = null,
  private val networkClient: NetworkClient = OkHttpNetworkClient(),
  private val headers: Map<String, String> = emptyMap(),
  private val appId: String? = null,
  private val timeoutMillis: Long = ATTEMPT_TIMEOUT_MILLIS,
  private val maxRetries: Int = MAX_RETRIES,
  private val retryDelayMillis: (attempt: Int) -> Long = ::defaultRetryDelayMillis,
  blockingDispatcher: CoroutineDispatcher = Dispatchers.IO,
  nonBlockingDispatcher: CoroutineDispatcher = Dispatchers.Default
) {

  private val authTokens: AuthTokenManager? =
    authDeferred?.let { AuthTokenManager(it, blockingDispatcher, nonBlockingDispatcher) }

  private val appCheckTokens: AppCheckTokenManager? =
    appCheckDeferred?.let { AppCheckTokenManager(it, blockingDispatcher, nonBlockingDispatcher) }

  /**
   * The parent of the requests in flight, which run on the non-blocking dispatcher. [close] cancels
   * it, which stops all of them at once, including a wait before a retry. Its job is the state of
   * the executor: cancelled by [close], and completed once the requests have ended.
   */
  private val requestScope = CoroutineScope(SupervisorJob() + nonBlockingDispatcher)

  /** `true` after [close]. A closed executor starts no request. */
  val isClosed: Boolean
    get() = !requestScope.isActive

  init {
    authTokens?.initialize()
    appCheckTokens?.initialize()
  }

  /**
   * Closes the executor, and returns without waiting: no request can start, and the requests in
   * flight end with a response whose error is [CLOSED_MESSAGE]. Calling it again has no effect.
   */
  fun close() {
    // Cancelling the scope also makes isClosed true, so a request that it stops always sees a
    // closed executor.
    requestScope.cancel()
    // No request waits for a token anymore, so the token managers can stop.
    authTokens?.close()
    appCheckTokens?.close()
  }

  /**
   * Closes the executor like [close], and returns when the requests that were in flight have ended.
   */
  suspend fun suspendingClose() {
    close()
    // The job of a cancelled scope completes once its coroutines have ended.
    requestScope.coroutineContext.job.join()
  }

  /**
   * Sends the [request] with the default headers and the tokens.
   *
   * Returns a response with a status of 0 and an error, without sending the request, when the
   * Firebase Authentication token of the signed-in user cannot be retrieved.
   *
   * When the server rejects a token with a 401, for example because it expired or was revoked, the
   * request is sent once more with refreshed tokens. The server rejects the tokens before it runs
   * the request, so sending it again is safe. A 401 that PostgREST answers for a database error,
   * for example `42501` for a request without a signed-in user that the database does not allow, is
   * returned as it is, because new tokens cannot change it.
   *
   * When [close] is called before the response arrives, the request stops, and the response has a
   * status of 0 and an [IllegalStateException] with the message [CLOSED_MESSAGE] as its error.
   *
   * @throws CancellationException When the coroutine is cancelled.
   * @throws IllegalStateException When the executor was closed before the call.
   */
  open suspend fun execute(request: QueryRequest): NetworkResponse {
    check(!isClosed) { CLOSED_MESSAGE }
    // The attempts run in the scope that close() cancels, on the non-blocking dispatcher, so that
    // their work never runs on the thread of the caller, which can be the main thread.
    val attempts = requestScope.async { executeWithRetries(request) }
    return try {
      attempts.await()
    } catch (e: CancellationException) {
      attempts.cancel()
      // The cancellation of the caller is passed on, and so is any other cancellation.
      currentCoroutineContext().ensureActive()
      if (!isClosed) throw e
      closedResponse()
    }
  }

  /**
   * Sends the [request], and sends it again after a transient failure. See [OperationsExecutor].
   */
  private suspend fun executeWithRetries(request: QueryRequest): NetworkResponse {
    var attempt = 0
    while (true) {
      val attemptRequest =
        if (attempt == 0) request
        else request.copy(headers = request.headers + (RETRY_COUNT_HEADER to attempt.toString()))
      val response = executeOnce(attemptRequest)
      if (!shouldRetry(request, response, attempt)) return response
      delay(retryDelayMillis(attempt))
      attempt++
    }
  }

  /**
   * Sends the [request], and sends it once more with refreshed tokens when the server rejects a
   * token that it carried.
   */
  private suspend fun executeOnce(request: QueryRequest): NetworkResponse {
    val (response, sentTokens) = send(request)
    if (!rejectsAToken(response) || sentTokens == SentTokens.NONE) return response
    // The response does not say which token the server rejected, so both are refreshed. A token
    // manager ignores a token that is outdated or being refreshed, so it refreshes its token once
    // for all the requests that the server rejected with it. A PostgREST error, `PGRST301` or
    // `PGRST303`, is about the Firebase Authentication token: the service checked the App Check
    // token before.
    sentTokens.authToken?.let { authTokens?.forceRefresh(rejectedToken = it) }
    val postgrestError =
      (response.error as? FirebaseSqlException)?.category == FirebaseSqlExceptionCategory.POSTGREST
    if (!postgrestError) {
      sentTokens.appCheckToken?.let { appCheckTokens?.forceRefresh(rejectedToken = it) }
    }
    return send(request).first
  }

  /**
   * Whether the [response] rejects a token: a 401, unless PostgREST answered it for an error that
   * new tokens cannot change. PostgREST answers 401 for a database error, for example `42501` for a
   * request without a signed-in user that the database does not allow, and for its own errors about
   * the token, `PGRST301` and `PGRST303`, which a refreshed token can fix.
   */
  private fun rejectsAToken(response: NetworkResponse): Boolean {
    if (response.status != HTTP_UNAUTHORIZED) return false
    val error = response.error as? FirebaseSqlException ?: return true
    if (error.category != FirebaseSqlExceptionCategory.POSTGREST) return true
    return error.code in POSTGREST_TOKEN_ERRORS
  }

  /** Whether the [response] to the attempt number [attempt] of [request] is worth a retry. */
  private fun shouldRetry(request: QueryRequest, response: NetworkResponse, attempt: Int): Boolean =
    request.retryEnabled &&
      attempt < maxRetries &&
      request.httpMethod in RETRYABLE_METHODS &&
      (response.isTransportError || response.status in RETRYABLE_STATUSES)

  /**
   * Sends the [request] once, and returns the response together with the tokens that it carried.
   */
  private suspend fun send(request: QueryRequest): Pair<NetworkResponse, SentTokens> {
    val authToken =
      try {
        authTokens?.getToken()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        val error = NetworkResponse(status = 0, statusText = "Auth Error", body = null, error = e)
        return error to SentTokens.NONE
      }
    val appCheckToken = appCheckToken()

    val finalHeaders = requestHeaders(request)
    authToken?.let { finalHeaders["x-firebase-auth-token"] = it }
    appCheckToken?.let { finalHeaders["X-Firebase-AppCheck"] = it }

    // Neither the request nor the global headers replace the versions and the app ID.
    finalHeaders["x-goog-api-client"] = GOOG_API_CLIENT
    finalHeaders["x-client-version"] = CLIENT_VERSION
    if (!appId.isNullOrBlank()) {
      finalHeaders["x-firebase-gmpid"] = appId
    }

    // On timeout, withTimeoutOrNull cancels the call in flight.
    val response =
      withTimeoutOrNull(timeoutMillis) {
        networkClient.sendRequest(serviceUrl, request.copy(headers = finalHeaders))
      }
        ?: timedOut()
    return response to SentTokens(authToken, appCheckToken)
  }

  /**
   * Returns the headers of [request] without the tokens and the versions: the headers of the
   * request, then the global [headers] that the request does not set, and then the default
   * `Content-Type` of a write and the header of the schema, unless one of them sets them. A global
   * `Prefer` is added after the preferences of the request instead.
   */
  private fun requestHeaders(request: QueryRequest): TreeMap<String, String> {
    val finalHeaders = TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER)
    finalHeaders.putAll(request.headers)
    headers.forEach { (name, value) ->
      val requestValue = finalHeaders[name]
      if (requestValue == null) {
        finalHeaders[name] = value
      } else if (name.equals("Prefer", ignoreCase = true)) {
        // PostgREST uses the first value of each preference, so the preferences of the request win.
        finalHeaders[name] = "$requestValue,$value"
      }
    }

    val isWrite = request.httpMethod in WRITE_METHODS
    if (isWrite) {
      finalHeaders.putIfMissing("Content-Type", "application/json")
    }

    if (schemaName != null) {
      finalHeaders.putIfMissing(if (isWrite) "Content-Profile" else "Accept-Profile", schemaName)
    }
    return finalHeaders
  }

  /** The tokens that a request carried. */
  private data class SentTokens(val authToken: String?, val appCheckToken: String?) {
    companion object {
      val NONE = SentTokens(authToken = null, appCheckToken = null)
    }
  }

  /**
   * The response of an attempt that did not complete within [timeoutMillis]. It is not a transport
   * error, so it is not sent again.
   */
  private fun timedOut() =
    NetworkResponse(
      status = 0,
      statusText = "Timeout",
      body = null,
      error = InterruptedIOException("The request did not complete within $timeoutMillis ms.")
    )

  /** The response of a request that [close] stopped. */
  private fun closedResponse() =
    NetworkResponse(
      status = 0,
      statusText = "Closed",
      body = null,
      error = IllegalStateException(CLOSED_MESSAGE)
    )

  /**
   * Returns the Firebase App Check token: the placeholder token when App Check fails with an error,
   * or `null` when the app does not use App Check or no token can be retrieved at all.
   */
  private suspend fun appCheckToken(): String? =
    try {
      appCheckTokens?.getToken()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      null
    }

  private fun MutableMap<String, String>.putIfMissing(name: String, value: String) {
    if (!containsKey(name)) {
      put(name, value)
    }
  }

  private companion object {
    /** The methods that change nothing, so that sending them twice is safe. */
    val RETRYABLE_METHODS = setOf("GET", "HEAD")

    /**
     * The transient statuses, after which a read is worth a retry:
     * - 502, which the Google API layer in front of PostgREST answers when it gets no valid
     * response from PostgREST, for example while a server restarts during a rollout.
     * - 503, which PostgREST answers when it cannot reach the database or while it loads its schema
     * cache, and which the service answers when a backend that it needs is unavailable.
     *
     * Not the other errors. 504 is a timeout, for example when no connection to the database is
     * free (`PGRST003`), so a retry would add load where there is too much already. 429 is a quota,
     * of which a retry spends more. 520 is a status of proxies such as Cloudflare, which the
     * Firebase SQL service does not return.
     */
    val RETRYABLE_STATUSES = setOf(502, 503)

    /** The status of a request whose token the server rejects. */
    const val HTTP_UNAUTHORIZED = 401

    /**
     * The errors of PostgREST about the token, which a refreshed token can fix: `PGRST301` for a
     * token that cannot be decoded, and `PGRST303` for a token whose claims are not valid, for
     * example an expired one.
     */
    val POSTGREST_TOKEN_ERRORS = setOf("PGRST301", "PGRST303")

    /**
     * The methods that PostgREST reads the schema of from `Content-Profile`, and that get a default
     * `Content-Type`.
     */
    val WRITE_METHODS = setOf("POST", "PUT", "PATCH", "DELETE")

    /**
     * The value of the `x-goog-api-client` header: the versions of Kotlin, Android, and the SDK. It
     * has no `grpc/` or `kotlin/gen` part, because the SDK sends REST requests and has no generated
     * code.
     */
    val GOOG_API_CLIENT =
      "gl-kotlin/${KotlinVersion.CURRENT} gl-android/${Build.VERSION.SDK_INT} " +
        "fire/${BuildConfig.VERSION_NAME}"

    /** The value of the `x-client-version` header: the platform and the version of the SDK. */
    const val CLIENT_VERSION = "android/${BuildConfig.VERSION_NAME}"
  }
}

/**
 * How many times a GET or HEAD request is sent again after a transient failure, after waits of
 * about 1, 2, and 4 seconds by default. `noRetry()` turns the retries off for one request.
 */
internal const val MAX_RETRIES = 3

/** The longest wait before a retry. */
internal const val MAX_RETRY_DELAY_MILLIS = 30_000L

/** The largest random part of the wait before a retry, as a fraction of the wait: 25%. */
private const val RETRY_JITTER = 0.25

/**
 * The longest time that each attempt of a request can take: longer than the 90 seconds after which
 * the server cancels a request, so that the SDK does not report a request as failed while the
 * server may still run it and commit it. An app that wants a shorter limit cancels the coroutine
 * that calls `execute()`, for example with `withTimeout`.
 */
internal const val ATTEMPT_TIMEOUT_MILLIS = 100_000L

/** The header that carries the number of the retry, from 1. */
internal const val RETRY_COUNT_HEADER = "X-Retry-Count"

/**
 * Returns the wait before the retry after the attempt number [attempt], from 0: `2^attempt` seconds
 * (1, 2, 4, and so on), up to 25% more or less at random, and at most [MAX_RETRY_DELAY_MILLIS]. The
 * random part spreads out the retries of the clients that failed at the same time, for example
 * while PostgREST loads its schema cache, so that they do not all come back at once.
 *
 * @param random The source of the random part, which tests replace.
 */
internal fun defaultRetryDelayMillis(attempt: Int, random: Random = Random.Default): Long {
  val backoff = (1000L shl attempt.coerceAtMost(5)).coerceAtMost(MAX_RETRY_DELAY_MILLIS)
  val jitter = random.nextDouble(1 - RETRY_JITTER, 1 + RETRY_JITTER)
  return (backoff * jitter).toLong().coerceAtMost(MAX_RETRY_DELAY_MILLIS)
}

/** The message of the error for a call on a terminated `FirebaseSql` instance. */
internal const val CLOSED_MESSAGE =
  "The FirebaseSql instance has been terminated; call Firebase.sql() or FirebaseSql.getInstance() " +
    "to get a new instance."

/**
 * The headers that the SDK reserves, in lowercase: the headers that it sets itself,
 * `Authorization`, which the server also reads as a credential, and the routing and heartbeat
 * headers that Firebase reserves. A developer cannot set them, so that the tokens and the schema of
 * a request always come from the app and the settings, the server records the real versions and app
 * ID, and a request reaches the service of its URL.
 */
internal val RESERVED_HEADERS: Set<String> =
  setOf(
    "x-firebase-auth-token",
    // The server rejects a request that has both this header and the token of a signed-in user,
    // and reads an OAuth token in it as an administrator.
    "authorization",
    "x-firebase-appcheck",
    "x-goog-api-client",
    "x-client-version",
    "x-firebase-gmpid",
    "accept-profile",
    "content-profile",
    // The SDK numbers the retries itself.
    "x-retry-count",
    // The SDK sends none of these headers: the load balancer reads the service from the path of the
    // URL instead of the routing headers, and the heartbeat headers are for the Firebase platform.
    "x-firebase-sqlconnect-affinity",
    "x-goog-request-params",
    "x-firebase-client",
    "x-firebase-client-log-type"
  )

/** The reserved headers that name the schema, which `SqlServiceSettings.schemaName` chooses. */
private val SCHEMA_HEADERS: Set<String> = setOf("accept-profile", "content-profile")

/**
 * Checks that [name] is not one of the [RESERVED_HEADERS], ignoring case.
 *
 * @param source Where the header comes from, for the error message, for example `setHeader()`.
 * @throws ReservedHeaderException If the header is reserved.
 */
internal fun requireNotReservedHeader(name: String, source: String) {
  val lowercaseName = name.lowercase(Locale.ROOT)
  if (lowercaseName !in RESERVED_HEADERS) return
  val schemaHint =
    if (lowercaseName in SCHEMA_HEADERS) " Use SqlServiceSettings.schemaName to choose the schema."
    else ""
  throw ReservedHeaderException(
    name,
    "The header '$name' is reserved by the SDK and cannot be set with $source. The reserved " +
      "headers, compared ignoring case, are ${RESERVED_HEADERS.joinToString()}.$schemaHint"
  )
}
