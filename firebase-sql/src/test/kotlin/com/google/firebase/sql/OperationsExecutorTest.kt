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

package com.google.firebase.sql

import android.os.Build
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.google.firebase.FirebaseException
import com.google.firebase.appcheck.AppCheckTokenResult
import com.google.firebase.appcheck.interop.AppCheckTokenListener
import com.google.firebase.appcheck.interop.InteropAppCheckTokenProvider
import com.google.firebase.auth.GetTokenResult
import com.google.firebase.auth.internal.IdTokenListener
import com.google.firebase.auth.internal.InternalAuthProvider
import com.google.firebase.inject.Deferred
import com.google.firebase.internal.api.FirebaseNoSignedInUserException
import com.google.firebase.sql.builders.OperationBuilderImpl
import com.google.firebase.sql.core.MAX_RETRIES
import com.google.firebase.sql.core.MAX_RETRY_DELAY_MILLIS
import com.google.firebase.sql.core.NetworkClient
import com.google.firebase.sql.core.NetworkResponse
import com.google.firebase.sql.core.OperationsExecutor
import com.google.firebase.sql.core.QueryRequest
import com.google.firebase.sql.core.defaultRetryDelayMillis
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests the headers that [OperationsExecutor] adds to every request, and how it handles the tokens
 * of Firebase Authentication and App Check.
 *
 * Robolectric is needed because the constructor of [FirebaseException] checks its message with
 * `android.text.TextUtils`.
 */
@RunWith(RobolectricTestRunner::class)
class OperationsExecutorTest {

  private class MockNetworkClient(
    var responseToReturn: NetworkResponse = NetworkResponse(200, "OK", "")
  ) : NetworkClient {
    var lastRequest: QueryRequest? = null

    override suspend fun sendRequest(serviceUrl: HttpUrl, request: QueryRequest): NetworkResponse {
      lastRequest = request
      return responseToReturn
    }
  }

  @Test
  fun aRequestWithoutTokensCarriesOnlyTheSchemaAndTheVersions() =
    runBlocking<Unit> {
      val client = MockNetworkClient()

      executor(client = client).execute(request("GET"))

      // The names keep the case in which the SDK sets them.
      assertThat(client.lastRequest!!.headers.keys)
        .containsExactly("Accept-Profile", "x-goog-api-client", "x-client-version")
    }

  @Test
  fun theVersionsAndTheAppIdAreSentLikeInTheOtherFirebaseSdks() =
    runBlocking<Unit> {
      val client = MockNetworkClient()

      executor(client = client, appId = "1:123:android:abc").execute(request("GET"))

      val headers = client.lastRequest!!.headers
      // The version comes from gradle.properties. Without it, VERSION_NAME is "unspecified".
      assertThat(BuildConfig.VERSION_NAME).matches("""\d+\.\d+\.\d+(-[a-z]+\d+)?""")
      // Robolectric reports a real API level, so the header does not say 0.
      assertThat(Build.VERSION.SDK_INT).isGreaterThan(0)
      assertThat(headers.valuesOf("x-goog-api-client"))
        .containsExactly(
          "gl-kotlin/${KotlinVersion.CURRENT} gl-android/${Build.VERSION.SDK_INT} " +
            "fire/${BuildConfig.VERSION_NAME}"
        )
      assertThat(headers.valuesOf("x-client-version"))
        .containsExactly("android/${BuildConfig.VERSION_NAME}")
      assertThat(headers.valuesOf("x-firebase-gmpid")).containsExactly("1:123:android:abc")
    }

  @Test
  fun noAppIdIsSentWithoutAnAppId() =
    runBlocking<Unit> {
      val client = MockNetworkClient()

      executor(client = client).execute(request("GET"))
      val withoutAppId = client.lastRequest!!.headers
      executor(client = client, appId = " ").execute(request("GET"))
      val withBlankAppId = client.lastRequest!!.headers

      assertThat(withoutAppId.valuesOf("x-firebase-gmpid")).isEmpty()
      assertThat(withBlankAppId.valuesOf("x-firebase-gmpid")).isEmpty()
      // The versions do not need an app ID.
      assertThat(withoutAppId.valuesOf("x-client-version")).hasSize(1)
      assertThat(withoutAppId.valuesOf("x-goog-api-client")).hasSize(1)
    }

  @Test
  fun theVersionsAndTheAppIdAreNotReplacedByOtherHeaders() =
    runBlocking<Unit> {
      // Apps cannot set these headers, and the SDK sets them over the headers that it is given.
      val client = MockNetworkClient()
      val executor =
        executor(
          client = client,
          appId = "1:123:android:abc",
          headers = mapOf("X-Goog-Api-Client" to "global", "X-Firebase-GMPID" to "global")
        )

      executor.execute(request("GET", headers = mapOf("X-CLIENT-VERSION" to "request")))

      val headers = client.lastRequest!!.headers
      assertThat(headers.valuesOf("x-goog-api-client").single()).startsWith("gl-kotlin/")
      assertThat(headers.valuesOf("x-client-version"))
        .containsExactly("android/${BuildConfig.VERSION_NAME}")
      assertThat(headers.valuesOf("x-firebase-gmpid")).containsExactly("1:123:android:abc")
    }

  @Test
  fun authAndAppCheckTokensAreSentInTheirHeaders() =
    runBlocking<Unit> {
      val client = MockNetworkClient()
      val auth = authProvider(Tasks.forResult(GetTokenResult("auth-token", mutableMapOf())))
      val appCheck = appCheckProvider(Tasks.forResult(appCheckResult("app-check-token", null)))

      executor(auth = auth, appCheck = appCheck, client = client).execute(request("GET"))

      val headers = client.lastRequest!!.headers
      assertThat(headers.valuesOf("x-firebase-auth-token")).containsExactly("auth-token")
      assertThat(headers.valuesOf("X-Firebase-AppCheck")).containsExactly("app-check-token")
    }

  @Test
  fun requestIsSentWithoutAuthTokenWhenNoUserIsSignedIn() =
    runBlocking<Unit> {
      // Firebase Authentication fails getAccessToken() with FirebaseNoSignedInUserException when no
      // user is signed in. The request is sent without a token, so that the server can decide what
      // a
      // signed-out user can read.
      val client = MockNetworkClient()
      val signedOut =
        authProvider(Tasks.forException(FirebaseNoSignedInUserException("No user is signed in.")))

      val response = executor(auth = signedOut, client = client).execute(request("GET"))

      assertThat(response.status).isEqualTo(200)
      assertThat(client.lastRequest!!.headers.valuesOf("x-firebase-auth-token")).isEmpty()
    }

  @Test
  fun authTokenFailureIsAnErrorWithoutSendingTheRequest() =
    runBlocking<Unit> {
      // Without the token, the request would run as a signed-out user, so it is not sent at all.
      val client = MockNetworkClient()
      val cause = FirebaseException("The token could not be refreshed.")
      val executor = executor(auth = authProvider(Tasks.forException(cause)), client = client)

      val response = executor.execute(request("GET"))

      assertThat(response.status).isEqualTo(0)
      assertThat(response.body).isNull()
      // The error of Firebase Authentication, as it is.
      assertThat(response.error).isSameInstanceAs(cause)

      // A builder throws the error.
      val query = OperationBuilderImpl("products", executor).select()
      val thrown = assertThrows(FirebaseException::class.java) { runBlocking { query.execute() } }
      assertThat(thrown).isSameInstanceAs(cause)
      assertThat(client.lastRequest).isNull()
    }

  @Test
  fun cancelledAuthTokenTaskIsNotTurnedIntoAFailure() {
    // The CancellationException must reach the caller, so that structured concurrency still works.
    val client = MockNetworkClient()
    val executor = executor(auth = authProvider(Tasks.forCanceled()), client = client)

    assertThrows(CancellationException::class.java) {
      runBlocking { executor.execute(request("GET")) }
    }
    assertThat(client.lastRequest).isNull()
  }

  @Test
  fun appCheckErrorSendsThePlaceholderToken() =
    runBlocking<Unit> {
      // App Check returns a placeholder token together with the error. The SDK sends it, and the
      // server decides whether to accept the request.
      val client = MockNetworkClient()
      val failed = appCheckResult("placeholder", FirebaseException("Attestation failed."))

      val response =
        executor(appCheck = appCheckProvider(Tasks.forResult(failed)), client = client)
          .execute(request("GET"))

      assertThat(response.status).isEqualTo(200)
      assertThat(client.lastRequest!!.headers.valuesOf("X-Firebase-AppCheck"))
        .containsExactly("placeholder")
    }

  @Test
  fun appCheckTaskFailureSendsTheRequestWithoutAppCheckToken() =
    runBlocking<Unit> {
      val client = MockNetworkClient()
      val executor =
        executor(
          auth = authProvider(Tasks.forResult(GetTokenResult("auth-token", mutableMapOf()))),
          appCheck = appCheckProvider(Tasks.forException(FirebaseException("App Check failed."))),
          client = client
        )

      val response = executor.execute(request("GET"))

      assertThat(response.status).isEqualTo(200)
      val headers = client.lastRequest!!.headers
      assertThat(headers.valuesOf("x-firebase-auth-token")).containsExactly("auth-token")
      assertThat(headers.valuesOf("X-Firebase-AppCheck")).isEmpty()
    }

  @Test
  fun anEmptyAppCheckTokenIsNotSent() =
    runBlocking<Unit> {
      // An empty token is no token, so the request is sent without the header, as without App
      // Check, whether or not App Check reports an error with it.
      for (error in listOf(null, FirebaseException("Attestation failed."))) {
        val client = MockNetworkClient()
        val appCheck = appCheckProvider(Tasks.forResult(appCheckResult("", error)))

        val response = executor(appCheck = appCheck, client = client).execute(request("GET"))

        assertWithMessage("error $error").that(response.status).isEqualTo(200)
        assertWithMessage("error $error")
          .that(client.lastRequest!!.headers.valuesOf("X-Firebase-AppCheck"))
          .isEmpty()
      }
    }

  @Test
  fun theFirstRequestWaitsForTheLookupOfTheProviders() =
    runBlocking<Unit> {
      // The providers are looked up on the blocking dispatcher, which is busy here. The first
      // request waits for the lookup, instead of going out without the tokens of linked providers.
      val blocking = Executors.newSingleThreadExecutor()
      val lookupMayRun = CountDownLatch(1)
      blocking.execute { lookupMayRun.await() }
      try {
        val client = MockNetworkClient()
        val auth = authProvider(Tasks.forResult(GetTokenResult("auth-token", mutableMapOf())))
        val appCheck = appCheckProvider(Tasks.forResult(appCheckResult("app-check-token", null)))
        val executor =
          OperationsExecutor(
            "public",
            SERVICE_URL,
            authDeferred = deferred(auth),
            appCheckDeferred = deferred(appCheck),
            networkClient = client,
            blockingDispatcher = blocking.asCoroutineDispatcher()
          )

        val response = async(Dispatchers.Default) { executor.execute(request("GET")) }
        delay(100)
        assertThat(response.isActive).isTrue()
        lookupMayRun.countDown()

        assertThat(response.await().status).isEqualTo(200)
        val headers = client.lastRequest!!.headers
        assertThat(headers.valuesOf("x-firebase-auth-token")).containsExactly("auth-token")
        assertThat(headers.valuesOf("X-Firebase-AppCheck")).containsExactly("app-check-token")
      } finally {
        blocking.shutdownNow()
      }
    }

  @Test
  fun providersThatBecomeAvailableLaterAreUsedByTheNextRequests() =
    runBlocking<Unit> {
      // A provider becomes available after the executor is created when, for example, its SDK is
      // in a dynamic feature module that is installed later. It arrives on another thread.
      // Each token manager looks up its provider on its own thread, so the two handlers can be
      // added at the same time.
      val deliveries = ConcurrentLinkedQueue<() -> Unit>()
      val auth = authProvider(Tasks.forResult(GetTokenResult("auth-token", mutableMapOf())))
      val appCheck = appCheckProvider(Tasks.forResult(appCheckResult("app-check-token", null)))
      val client = MockNetworkClient()
      val executor =
        OperationsExecutor(
          "public",
          SERVICE_URL,
          authDeferred = Deferred { handler -> deliveries.add { handler.handle { auth } } },
          appCheckDeferred = Deferred { handler -> deliveries.add { handler.handle { appCheck } } },
          networkClient = client
        )

      executor.execute(request("GET"))
      val before = client.lastRequest!!.headers
      assertThat(before.valuesOf("x-firebase-auth-token")).isEmpty()
      assertThat(before.valuesOf("X-Firebase-AppCheck")).isEmpty()

      val delivery = Thread { deliveries.forEach { it() } }
      delivery.start()
      delivery.join()
      executor.execute(request("GET"))

      val after = client.lastRequest!!.headers
      assertThat(after.valuesOf("x-firebase-auth-token")).containsExactly("auth-token")
      assertThat(after.valuesOf("X-Firebase-AppCheck")).containsExactly("app-check-token")
    }

  @Test
  fun contentTypeIsAddedToPostPutPatchAndDelete() =
    runBlocking<Unit> {
      val client = MockNetworkClient()
      val executor = executor(client = client)

      for (method in listOf("POST", "PUT", "PATCH", "DELETE")) {
        executor.execute(request(method))

        assertWithMessage(method)
          .that(client.lastRequest!!.headers.valuesOf("Content-Type"))
          .containsExactly("application/json")
      }
      executor.execute(request("GET"))
      assertThat(client.lastRequest!!.headers.valuesOf("Content-Type")).isEmpty()
    }

  @Test
  fun contentTypeOfTheRequestIsNotOverwritten() =
    runBlocking<Unit> {
      val client = MockNetworkClient()

      executor(client = client)
        .execute(request("POST", headers = mapOf("Content-Type" to "text/csv")))

      assertThat(client.lastRequest!!.headers.valuesOf("Content-Type")).containsExactly("text/csv")
    }

  @Test
  fun schemaIsSentInTheProfileHeaderOfTheMethod() =
    runBlocking<Unit> {
      // PostgREST reads the schema of GET and HEAD requests from Accept-Profile, and the schema of
      // POST, PUT, PATCH, and DELETE requests from Content-Profile.
      val headerByMethod =
        mapOf(
          "GET" to "Accept-Profile",
          "HEAD" to "Accept-Profile",
          "POST" to "Content-Profile",
          "PUT" to "Content-Profile",
          "PATCH" to "Content-Profile",
          "DELETE" to "Content-Profile"
        )
      // An explicit `public` is sent like any other schema. It is not the default: without a
      // schema, no header is sent and the server uses the schema of the service.
      for (schema in listOf("custom_schema", "public")) {
        val client = MockNetworkClient()
        val executor = executor(schema = schema, client = client)

        for ((method, header) in headerByMethod) {
          executor.execute(request(method))

          val headers = client.lastRequest!!.headers
          val otherHeader = if (header == "Accept-Profile") "Content-Profile" else "Accept-Profile"
          assertWithMessage("$schema $method")
            .that(headers.valuesOf(header))
            .containsExactly(schema)
          assertWithMessage("$schema $method").that(headers.valuesOf(otherHeader)).isEmpty()
        }
      }
    }

  @Test
  fun noSchemaSendsNoSchemaHeader() =
    runBlocking<Unit> {
      // Without a schema header, PostgREST uses its default schema, the schema of the service.
      val client = MockNetworkClient()
      val executor = executor(schema = null, client = client)

      for (method in listOf("GET", "POST")) {
        executor.execute(request(method))

        val headers = client.lastRequest!!.headers
        assertWithMessage(method).that(headers.valuesOf("Accept-Profile")).isEmpty()
        assertWithMessage(method).that(headers.valuesOf("Content-Profile")).isEmpty()
      }
    }

  @Test
  fun globalHeadersAreAdded() =
    runBlocking<Unit> {
      val client = MockNetworkClient()

      executor(client = client, headers = mapOf("X-Global" to "global-value"))
        .execute(request("GET"))

      assertThat(client.lastRequest!!.headers.valuesOf("X-Global")).containsExactly("global-value")
    }

  @Test
  fun requestHeadersWinOverGlobalHeadersInAnyCase() =
    runBlocking<Unit> {
      val client = MockNetworkClient()

      executor(client = client, headers = mapOf("x-custom" to "global-value"))
        .execute(request("GET", headers = mapOf("X-Custom" to "request-value")))

      assertThat(client.lastRequest!!.headers.valuesOf("X-Custom")).containsExactly("request-value")
    }

  @Test
  fun aGlobalPreferIsAddedAfterThePreferencesOfTheRequest() =
    runBlocking<Unit> {
      // Prefer holds a list of preferences, so the global ones are kept. PostgREST uses the first
      // value of each preference, so those of the request win: count=exact here.
      val client = MockNetworkClient()
      val executor =
        executor(client = client, headers = mapOf("prefer" to "timezone=UTC, count=planned"))

      executor.execute(request("GET", headers = mapOf("Prefer" to "count=exact")))
      val merged = client.lastRequest!!.headers
      executor.execute(request("GET"))
      val globalOnly = client.lastRequest!!.headers

      assertThat(merged.valuesOf("Prefer"))
        .containsExactly("count=exact,timezone=UTC, count=planned")
      assertThat(globalOnly.valuesOf("Prefer")).containsExactly("timezone=UTC, count=planned")
    }

  @Test
  fun globalHeadersWinOverDefaultHeaders() =
    runBlocking<Unit> {
      val client = MockNetworkClient()
      val executor =
        executor(
          schema = "custom_schema",
          client = client,
          headers = mapOf("accept-profile" to "inventory", "content-type" to "text/csv")
        )

      executor.execute(request("GET"))
      val getHeaders = client.lastRequest!!.headers
      executor.execute(request("POST"))
      val postHeaders = client.lastRequest!!.headers

      assertThat(getHeaders.valuesOf("Accept-Profile")).containsExactly("inventory")
      assertThat(postHeaders.valuesOf("Content-Type")).containsExactly("text/csv")
    }

  @Test
  fun defaultHeadersAreNotAddedWhenTheRequestHasThemInAnotherCase() =
    runBlocking<Unit> {
      val client = MockNetworkClient()
      val executor = executor(schema = "custom_schema", client = client)

      executor.execute(request("GET", headers = mapOf("accept-profile" to "other")))
      val getHeaders = client.lastRequest!!.headers
      executor.execute(
        request("POST", headers = mapOf("content-type" to "text/csv", "content-profile" to "other"))
      )
      val postHeaders = client.lastRequest!!.headers

      assertThat(getHeaders.valuesOf("Accept-Profile")).containsExactly("other")
      assertThat(postHeaders.valuesOf("Content-Type")).containsExactly("text/csv")
      assertThat(postHeaders.valuesOf("Content-Profile")).containsExactly("other")
    }

  // --- close() ---

  @Test
  fun executeAfterCloseThrowsWithoutSending() {
    val client = MockNetworkClient()
    val executor = executor(client = client)

    executor.close()

    val error =
      assertThrows(IllegalStateException::class.java) {
        runBlocking { executor.execute(request("GET")) }
      }
    assertThat(error).hasMessageThat().contains("terminated")
    assertThat(executor.isClosed).isTrue()
    assertThat(client.lastRequest).isNull()
  }

  @Test
  fun closeCanBeCalledAgain() =
    runBlocking<Unit> {
      val executor = executor(client = MockNetworkClient())
      assertThat(executor.isClosed).isFalse()

      executor.close()
      executor.close()
      executor.suspendingClose()
      executor.suspendingClose()

      assertThat(executor.isClosed).isTrue()
    }

  @Test
  fun closeDuringTheTokenFetchStopsTheRequest() =
    runBlocking<Unit> {
      // The App Check provider closes the executor while it returns its token, so the request is
      // stopped before it is sent.
      val client = MockNetworkClient()
      lateinit var executor: OperationsExecutor
      val closingAppCheck =
        object :
          InteropAppCheckTokenProvider by appCheckProvider(
            Tasks.forResult(appCheckResult("app-check-token", null))
          ) {
          override fun getToken(forceRefresh: Boolean): Task<AppCheckTokenResult> {
            executor.close()
            return Tasks.forResult(appCheckResult("app-check-token", null))
          }
        }
      executor = this@OperationsExecutorTest.executor(appCheck = closingAppCheck, client = client)

      val response = executor.execute(request("GET"))

      assertClosed(response)
      assertThat(client.lastRequest).isNull()
    }

  @Test
  fun closeStopsTheRequestsThatWaitForAToken() =
    runBlocking<Unit> {
      // The token never arrives, so the requests wait for it until the executor is closed.
      val pendingToken = TaskCompletionSource<GetTokenResult>()
      val client = MockNetworkClient()
      val executor = executor(auth = authProvider(pendingToken.task), client = client)
      val requests =
        List(3) { async(Dispatchers.Default) { runCatching { executor.execute(request("GET")) } } }
      delay(100)

      executor.close()

      for (result in requests.awaitAll()) {
        assertClosed(result.getOrThrow())
      }
      assertThat(client.lastRequest).isNull()
    }

  @Test
  fun suspendingCloseWaitsForTheRequestsInFlightToEnd() =
    runBlocking<Unit> {
      // The call takes a moment to stop after its cancellation.
      val sent = CompletableDeferred<Unit>()
      val stopped = AtomicBoolean(false)
      val client =
        object : NetworkClient {
          override suspend fun sendRequest(
            serviceUrl: HttpUrl,
            request: QueryRequest
          ): NetworkResponse {
            sent.complete(Unit)
            try {
              awaitCancellation()
            } finally {
              withContext(NonCancellable) { delay(200) }
              stopped.set(true)
            }
          }
        }
      val executor = executor(client = client)
      val response = async(Dispatchers.Default) { executor.execute(request("GET")) }
      sent.await()

      executor.suspendingClose()

      assertThat(stopped.get()).isTrue()
      assertClosed(response.await())
    }

  @Test
  fun theAttemptsRunOnTheNonBlockingDispatcher() =
    runBlocking<Unit> {
      // execute() is often called on the main thread, which must not build, send, or retry the
      // request.
      val nonBlocking = Executors.newSingleThreadExecutor { Thread(it, "non-blocking") }
      try {
        val threadNames = ConcurrentLinkedQueue<String>()
        val client =
          object : NetworkClient {
            override suspend fun sendRequest(
              serviceUrl: HttpUrl,
              request: QueryRequest
            ): NetworkResponse {
              threadNames.add(Thread.currentThread().name)
              return NetworkResponse(200, "OK", "[]")
            }
          }
        val executor =
          OperationsExecutor(
            "public",
            SERVICE_URL,
            networkClient = client,
            nonBlockingDispatcher = nonBlocking.asCoroutineDispatcher()
          )

        executor.execute(request("GET"))

        // In debug mode, kotlinx.coroutines appends the name of the coroutine to the thread name.
        assertThat(threadNames.single()).startsWith("non-blocking")
      } finally {
        nonBlocking.shutdownNow()
      }
    }

  /** Checks that [response] is the response of a request that `close()` stopped. */
  private fun assertClosed(response: NetworkResponse) {
    assertThat(response.status).isEqualTo(0)
    assertThat(response.statusText).isEqualTo("Closed")
    // The same error as a call on a closed instance.
    assertThat(response.error).isInstanceOf(IllegalStateException::class.java)
    assertThat(response.error).hasMessageThat().contains("terminated")
  }

  // --- 401 and token refresh ---

  /** Answers with [statuses] in order, and records the requests. */
  private class SequenceNetworkClient(vararg statuses: Int) : NetworkClient {
    private val remaining = statuses.toMutableList()
    val requests = mutableListOf<QueryRequest>()

    override suspend fun sendRequest(serviceUrl: HttpUrl, request: QueryRequest): NetworkResponse {
      requests.add(request)
      val status = remaining.removeAt(0)
      return NetworkResponse(status, "", if (status == 200) "[]" else null)
    }
  }

  /** Returns `cached-token`, or `fresh-token` when asked to refresh, and counts the refreshes. */
  private class RefreshingAuthProvider : InternalAuthProvider {
    var refreshes = 0

    override fun getAccessToken(forceRefresh: Boolean): Task<GetTokenResult> {
      if (forceRefresh) refreshes++
      val token = if (forceRefresh) "fresh-token" else "cached-token"
      return Tasks.forResult(GetTokenResult(token, mutableMapOf()))
    }

    override fun getUid(): String? = null
    override fun addIdTokenListener(listener: IdTokenListener) {}
    override fun removeIdTokenListener(listener: IdTokenListener) {}
  }

  @Test
  fun a401IsRetriedOnceWithARefreshedToken() =
    runBlocking<Unit> {
      val client = SequenceNetworkClient(401, 200)
      val auth = RefreshingAuthProvider()

      val response = executor(schema = null, auth = auth, client = client).execute(request("POST"))

      assertThat(response.status).isEqualTo(200)
      assertThat(auth.refreshes).isEqualTo(1)
      assertThat(client.requests.map { it.headers.valuesOf("x-firebase-auth-token") })
        .containsExactly(listOf("cached-token"), listOf("fresh-token"))
        .inOrder()
    }

  @Test
  fun a401WithARefreshedTokenIsReturned() =
    runBlocking<Unit> {
      // The request is retried only once, so a token that the server still rejects ends there.
      val client = SequenceNetworkClient(401, 401)

      val response =
        executor(schema = null, auth = RefreshingAuthProvider(), client = client)
          .execute(request("GET"))

      assertThat(response.status).isEqualTo(401)
      assertThat(client.requests).hasSize(2)
    }

  @Test
  fun a401WithoutATokenIsNotRetried() =
    runBlocking<Unit> {
      // A request without tokens has no token that a refresh can change.
      val client = SequenceNetworkClient(401)

      val response = executor(schema = null, client = client).execute(request("GET"))

      assertThat(response.status).isEqualTo(401)
      assertThat(client.requests).hasSize(1)
    }

  @Test
  fun aRefreshThatReturnsTheSameTokenSendsTheRequestOnlyOnceMore() =
    runBlocking<Unit> {
      // The request is sent once more even when the refresh returns the token that the server
      // rejected, and the second 401 is the response, so such a provider cannot cause a loop.
      val refreshes = AtomicInteger(0)
      val auth =
        object : InternalAuthProvider {
          override fun getAccessToken(forceRefresh: Boolean): Task<GetTokenResult> {
            if (forceRefresh) refreshes.incrementAndGet()
            return Tasks.forResult(GetTokenResult("same-token", mutableMapOf()))
          }

          override fun getUid(): String? = null
          override fun addIdTokenListener(listener: IdTokenListener) {}
          override fun removeIdTokenListener(listener: IdTokenListener) {}
        }
      val client = SequenceNetworkClient(401, 401)

      val response = executor(schema = null, auth = auth, client = client).execute(request("GET"))

      assertThat(response.status).isEqualTo(401)
      assertThat(refreshes.get()).isEqualTo(1)
      assertThat(client.requests.map { it.headers.valuesOf("x-firebase-auth-token") })
        .containsExactly(listOf("same-token"), listOf("same-token"))
    }

  /**
   * Like Firebase Authentication, a refresh replaces the cached token, `token-1`, with a new one:
   * `token-2`, then `token-3`, and so on. Counts the refreshes.
   */
  private class RotatingAuthProvider : InternalAuthProvider {
    private val generation = AtomicInteger(1)
    val refreshes = AtomicInteger(0)

    override fun getAccessToken(forceRefresh: Boolean): Task<GetTokenResult> {
      val current =
        if (forceRefresh) {
          refreshes.incrementAndGet()
          generation.incrementAndGet()
        } else {
          generation.get()
        }
      return Tasks.forResult(GetTokenResult("token-$current", mutableMapOf()))
    }

    override fun getUid(): String? = null
    override fun addIdTokenListener(listener: IdTokenListener) {}
    override fun removeIdTokenListener(listener: IdTokenListener) {}
  }

  /** Like [RotatingAuthProvider], for App Check: `app-check-1`, then `app-check-2`, and so on. */
  private class RotatingAppCheckProvider : InteropAppCheckTokenProvider {
    private val generation = AtomicInteger(1)
    val refreshes = AtomicInteger(0)

    override fun getToken(forceRefresh: Boolean): Task<AppCheckTokenResult> {
      val current =
        if (forceRefresh) {
          refreshes.incrementAndGet()
          generation.incrementAndGet()
        } else {
          generation.get()
        }
      return Tasks.forResult(appCheckResult("app-check-$current", null))
    }

    override fun getLimitedUseToken(): Task<AppCheckTokenResult> = getToken(false)
    override fun addAppCheckTokenListener(listener: AppCheckTokenListener) {}
    override fun removeAppCheckTokenListener(listener: AppCheckTokenListener) {}
  }

  /**
   * Answers 401 with [error] to the requests that carry one of the [rejected] tokens, by header
   * name, and 200 to the others. The first answer waits until [firstAnswerAfter] requests have
   * arrived, and each answer takes up to [maxDelayMillis], so that concurrent requests overlap.
   */
  private class RejectingNetworkClient(
    private val rejected: Map<String, String>,
    private val error: FirebaseSqlException? = null,
    private val maxDelayMillis: Long = 20,
    private val firstAnswerAfter: Int = 1
  ) : NetworkClient {
    constructor(
      header: String,
      rejectedToken: String,
      error: FirebaseSqlException? = null
    ) : this(mapOf(header to rejectedToken), error)

    val requests = ConcurrentLinkedQueue<QueryRequest>()
    private val arrivals = AtomicInteger(0)
    private val allArrived = CompletableDeferred<Unit>()

    override suspend fun sendRequest(serviceUrl: HttpUrl, request: QueryRequest): NetworkResponse {
      requests.add(request)
      if (arrivals.incrementAndGet() >= firstAnswerAfter) allArrived.complete(Unit)
      allArrived.await()
      delay((0L..maxDelayMillis).random())
      val isRejected =
        rejected.any { (header, token) -> request.headers.valuesOf(header) == listOf(token) }
      return if (isRejected) {
        NetworkResponse(401, "Unauthorized", null, error = error)
      } else {
        NetworkResponse(200, "OK", "[]")
      }
    }
  }

  @Test
  fun the401sOfRequestsThatSentTheSameTokensCauseOneRefreshOfEach() =
    runBlocking<Unit> {
      // The requests overlap in random orders, so the scenario runs several times.
      repeat(20) { iteration ->
        val auth = RotatingAuthProvider()
        val appCheck = RotatingAppCheckProvider()
        // The requests are all sent before the first answer, so they all carry the first tokens.
        val client =
          RejectingNetworkClient(
            mapOf("x-firebase-auth-token" to "token-1", "X-Firebase-AppCheck" to "app-check-1"),
            firstAnswerAfter = 20
          )
        val executor = executor(schema = null, auth = auth, appCheck = appCheck, client = client)

        val responses =
          List(20) { async(Dispatchers.Default) { executor.execute(request("GET")) } }.awaitAll()

        assertWithMessage("statuses, iteration $iteration")
          .that(responses.map { it.status }.toSet())
          .containsExactly(200)
        assertWithMessage("refreshes of the Firebase Authentication token, iteration $iteration")
          .that(auth.refreshes.get())
          .isEqualTo(1)
        assertWithMessage("refreshes of the App Check token, iteration $iteration")
          .that(appCheck.refreshes.get())
          .isEqualTo(1)
      }
    }

  @Test
  fun a401RefreshesTheAppCheckTokenToo() =
    runBlocking<Unit> {
      // The response does not say which token the server rejected, so both are refreshed.
      val auth = RotatingAuthProvider()
      val appCheck = RotatingAppCheckProvider()
      val client = RejectingNetworkClient("X-Firebase-AppCheck", "app-check-1")
      val executor = executor(schema = null, auth = auth, appCheck = appCheck, client = client)

      val response = executor.execute(request("POST"))

      assertThat(response.status).isEqualTo(200)
      assertThat(auth.refreshes.get()).isEqualTo(1)
      assertThat(appCheck.refreshes.get()).isEqualTo(1)
      val resent = client.requests.last().headers
      assertThat(resent.valuesOf("x-firebase-auth-token")).containsExactly("token-2")
      assertThat(resent.valuesOf("X-Firebase-AppCheck")).containsExactly("app-check-2")
    }

  @Test
  fun a401RefreshesATokenEvenWhenTheOtherTokenIsOutdated() =
    runBlocking<Unit> {
      // While the first request waits for its answer, a second request gets a new Firebase
      // Authentication token, because the provider rotated it, for example shortly before it
      // expired. The server rejects the App Check token of the first request, which is still the
      // newest one, so it is refreshed even though the other token of the request is outdated.
      val authCalls = AtomicInteger(0)
      val auth =
        object : InternalAuthProvider {
          override fun getAccessToken(forceRefresh: Boolean): Task<GetTokenResult> =
            Tasks.forResult(GetTokenResult("token-${authCalls.incrementAndGet()}", mutableMapOf()))

          override fun getUid(): String? = null
          override fun addIdTokenListener(listener: IdTokenListener) {}
          override fun removeIdTokenListener(listener: IdTokenListener) {}
        }
      val appCheck = RotatingAppCheckProvider()
      val firstSent = CompletableDeferred<Unit>()
      val secondSent = CompletableDeferred<Unit>()
      val firstResent = CompletableDeferred<Unit>()
      val requests = ConcurrentLinkedQueue<QueryRequest>()
      val client =
        object : NetworkClient {
          override suspend fun sendRequest(
            serviceUrl: HttpUrl,
            request: QueryRequest
          ): NetworkResponse {
            requests.add(request)
            // The first request is answered once the second one is sent, and the second one once
            // the first one is sent again.
            when (requests.size) {
              1 -> {
                firstSent.complete(Unit)
                secondSent.await()
              }
              2 -> {
                secondSent.complete(Unit)
                firstResent.await()
              }
              3 -> firstResent.complete(Unit)
            }
            val rejected = request.headers.valuesOf("X-Firebase-AppCheck") == listOf("app-check-1")
            return if (rejected) NetworkResponse(401, "Unauthorized", null)
            else NetworkResponse(200, "OK", "[]")
          }
        }
      val executor = executor(schema = null, auth = auth, appCheck = appCheck, client = client)

      val first = async(Dispatchers.Default) { executor.execute(request("GET")) }
      firstSent.await()
      val second = async(Dispatchers.Default) { executor.execute(request("GET")) }

      assertThat(first.await().status).isEqualTo(200)
      assertThat(second.await().status).isEqualTo(200)
      assertThat(appCheck.refreshes.get()).isEqualTo(1)
    }

  @Test
  fun aFailedRefreshIsForcedAgainByTheNextRequest() =
    runBlocking<Unit> {
      // The refresh after the 401 fails, for example without network, so the request fails. The
      // next request refreshes the token again, instead of sending the token that the server
      // rejected.
      val refreshes = AtomicInteger(0)
      val auth =
        object : InternalAuthProvider {
          override fun getAccessToken(forceRefresh: Boolean): Task<GetTokenResult> =
            when {
              !forceRefresh -> Tasks.forResult(GetTokenResult("cached-token", mutableMapOf()))
              refreshes.incrementAndGet() == 1 ->
                Tasks.forException(FirebaseException("No network"))
              else -> Tasks.forResult(GetTokenResult("fresh-token", mutableMapOf()))
            }

          override fun getUid(): String? = null
          override fun addIdTokenListener(listener: IdTokenListener) {}
          override fun removeIdTokenListener(listener: IdTokenListener) {}
        }
      val client = RejectingNetworkClient("x-firebase-auth-token", "cached-token")
      val executor = executor(schema = null, auth = auth, client = client)

      val failed = executor.execute(request("GET"))
      val next = executor.execute(request("GET"))

      assertThat(failed.statusText).isEqualTo("Auth Error")
      assertThat(next.status).isEqualTo(200)
      assertThat(client.requests.map { it.headers.valuesOf("x-firebase-auth-token") })
        .containsExactly(listOf("cached-token"), listOf("fresh-token"))
        .inOrder()
    }

  @Test
  fun aRequestWithOnlyAnAppCheckTokenIsSentAgainWithARefreshedOne() =
    runBlocking<Unit> {
      // Without a signed-in user, the App Check token is the only token that the server can reject.
      val appCheck = RotatingAppCheckProvider()
      val unauthenticated =
        FirebaseSqlException(
          "The App Check token is not valid.",
          FirebaseSqlExceptionCategory.PLATFORM,
          code = "UNAUTHENTICATED",
          status = 401
        )
      val client = RejectingNetworkClient("X-Firebase-AppCheck", "app-check-1", unauthenticated)

      val response =
        executor(schema = null, appCheck = appCheck, client = client).execute(request("GET"))

      assertThat(response.status).isEqualTo(200)
      assertThat(appCheck.refreshes.get()).isEqualTo(1)
      assertThat(client.requests.map { it.headers.valuesOf("X-Firebase-AppCheck") })
        .containsExactly(listOf("app-check-1"), listOf("app-check-2"))
        .inOrder()
    }

  @Test
  fun a401ForADatabaseErrorIsNotSentAgain() =
    runBlocking<Unit> {
      // PostgREST answers 401 to a request without a signed-in user that the database does not
      // allow. New tokens cannot change that answer.
      val appCheck = RotatingAppCheckProvider()
      val denied =
        FirebaseSqlException(
          "permission denied for table products",
          FirebaseSqlExceptionCategory.POSTGREST,
          code = "42501",
          status = 401
        )
      val client = RejectingNetworkClient("X-Firebase-AppCheck", "app-check-1", denied)

      val response =
        executor(schema = null, appCheck = appCheck, client = client).execute(request("GET"))

      assertThat(response.status).isEqualTo(401)
      assertThat(response.error).isSameInstanceAs(denied)
      assertThat(client.requests).hasSize(1)
      assertThat(appCheck.refreshes.get()).isEqualTo(0)
    }

  @Test
  fun a401ForAPostgrestTokenErrorIsSentAgainWithARefreshedToken() =
    runBlocking<Unit> {
      val auth = RotatingAuthProvider()
      val expired =
        FirebaseSqlException(
          "JWT expired",
          FirebaseSqlExceptionCategory.POSTGREST,
          code = "PGRST303",
          status = 401
        )
      val client = RejectingNetworkClient("x-firebase-auth-token", "token-1", expired)
      val appCheck = RotatingAppCheckProvider()

      val response =
        executor(schema = null, auth = auth, appCheck = appCheck, client = client)
          .execute(request("GET"))

      assertThat(response.status).isEqualTo(200)
      assertThat(auth.refreshes.get()).isEqualTo(1)
      // PostgREST reads only the Firebase Authentication token, so the App Check token is kept.
      assertThat(appCheck.refreshes.get()).isEqualTo(0)
    }

  @Test
  fun aLater401RefreshesTheTokenAgain() =
    runBlocking<Unit> {
      // The server first rejects token-1, then, for example once it has expired, token-2, which
      // the first refresh returned. Each rejection causes its own refresh.
      val auth = RotatingAuthProvider()
      val rejected = ConcurrentLinkedQueue(listOf("token-1"))
      val requests = ConcurrentLinkedQueue<QueryRequest>()
      val client =
        object : NetworkClient {
          override suspend fun sendRequest(
            serviceUrl: HttpUrl,
            request: QueryRequest
          ): NetworkResponse {
            requests.add(request)
            val token = request.headers.valuesOf("x-firebase-auth-token").single()
            return if (token in rejected) NetworkResponse(401, "Unauthorized", null)
            else NetworkResponse(200, "OK", "[]")
          }
        }
      val executor = executor(schema = null, auth = auth, client = client)

      val first = executor.execute(request("GET"))
      rejected.add("token-2")
      val second = executor.execute(request("GET"))

      assertThat(first.status).isEqualTo(200)
      assertThat(second.status).isEqualTo(200)
      assertThat(auth.refreshes.get()).isEqualTo(2)
      assertThat(requests.map { it.headers.valuesOf("x-firebase-auth-token") })
        .containsExactly(listOf("token-1"), listOf("token-2"), listOf("token-2"), listOf("token-3"))
        .inOrder()
    }

  @Test
  fun cancellingTheCallDuringTheRefreshAfterA401SendsNothingMore() =
    runBlocking<Unit> {
      // The refresh never ends, so the call is still waiting for it when it is cancelled.
      val refreshStarted = CompletableDeferred<Unit>()
      val auth =
        object : InternalAuthProvider {
          override fun getAccessToken(forceRefresh: Boolean): Task<GetTokenResult> {
            if (!forceRefresh)
              return Tasks.forResult(GetTokenResult("cached-token", mutableMapOf()))
            refreshStarted.complete(Unit)
            return TaskCompletionSource<GetTokenResult>().task
          }

          override fun getUid(): String? = null
          override fun addIdTokenListener(listener: IdTokenListener) {}
          override fun removeIdTokenListener(listener: IdTokenListener) {}
        }
      val client = SequenceNetworkClient(401)
      val executor = executor(schema = null, auth = auth, client = client)

      val call = async(Dispatchers.Default) { executor.execute(request("GET")) }
      withTimeout(5_000) { refreshStarted.await() }
      call.cancelAndJoin()

      assertThat(call.isCancelled).isTrue()
      assertThat(client.requests).hasSize(1)
      // The cancellation of a caller does not close the executor.
      assertThat(executor.isClosed).isFalse()
      executor.close()
    }

  /** Returns an executor for [SERVICE_URL] that sends its requests to [client]. */
  // --- Retries ---

  /** Answers with [responses] in order, and records the requests. */
  private class ResponsesNetworkClient(vararg responses: NetworkResponse) : NetworkClient {
    private val remaining = responses.toMutableList()
    val requests = mutableListOf<QueryRequest>()

    override suspend fun sendRequest(serviceUrl: HttpUrl, request: QueryRequest): NetworkResponse {
      requests.add(request)
      return remaining.removeAt(0)
    }
  }

  private fun status(code: Int) = NetworkResponse(code, "", if (code == 200) "[]" else null)

  private val networkError =
    NetworkResponse(
      0,
      "Transport Error",
      null,
      error = IOException("reset"),
      isTransportError = true
    )

  @Test
  fun readsAreRetriedAfterTransientStatusesAndNetworkErrors() =
    runBlocking<Unit> {
      for (method in listOf("GET", "HEAD")) {
        val client = ResponsesNetworkClient(status(503), networkError, status(502), status(200))

        val response = executor(client = client).execute(request(method))

        assertWithMessage(method).that(response.status).isEqualTo(200)
        // Each retry carries its number.
        assertWithMessage(method)
          .that(client.requests.map { it.headers.valuesOf("X-Retry-Count") })
          .containsExactly(emptyList<String>(), listOf("1"), listOf("2"), listOf("3"))
          .inOrder()
      }
    }

  @Test
  fun writesAreNeverRetried() =
    runBlocking<Unit> {
      // A write that failed may still have run, so sending it twice could apply it twice.
      for (method in listOf("POST", "PUT", "PATCH", "DELETE")) {
        for (failure in listOf(status(502), status(503), networkError)) {
          val client = ResponsesNetworkClient(failure)

          val response = executor(client = client).execute(request(method))

          assertWithMessage(method).that(response).isSameInstanceAs(failure)
          assertWithMessage(method).that(client.requests).hasSize(1)
        }
      }
    }

  @Test
  fun otherErrorsAreNotRetried() =
    runBlocking<Unit> {
      // 504 is a timeout, for example when no connection to the database is free, so a retry would
      // add load. 429 is a quota, of which a retry spends more. 520 is a status of proxies such as
      // Cloudflare, which the Firebase SQL service does not return.
      for (code in listOf(400, 404, 406, 408, 429, 500, 504, 520)) {
        val client = ResponsesNetworkClient(status(code))

        val response = executor(client = client).execute(request("GET"))

        assertWithMessage("$code").that(response.status).isEqualTo(code)
        assertWithMessage("$code").that(client.requests).hasSize(1)
      }
    }

  @Test
  fun retriesStopAfterThreeRetries() =
    runBlocking<Unit> {
      val client = ResponsesNetworkClient(status(503), status(503), status(503), status(503))

      val response = executor(client = client).execute(request("GET"))

      assertThat(response.status).isEqualTo(503)
      assertThat(client.requests).hasSize(4)
    }

  @Test
  fun retriesThatRunOutOnNetworkErrorsGiveTheLastError() =
    runBlocking<Unit> {
      val errors =
        List(4) {
          NetworkResponse(
            0,
            "Transport Error",
            null,
            error = IOException("reset ${it + 1}"),
            isTransportError = true
          )
        }
      val client = ResponsesNetworkClient(*errors.toTypedArray())

      val response = executor(client = client).execute(request("GET"))

      assertThat(client.requests).hasSize(4)
      assertThat(response.isTransportError).isTrue()
      assertThat(response.error).isSameInstanceAs(errors.last().error)

      // A builder throws the IOException as it is.
      val query =
        OperationBuilderImpl(
            "products",
            executor(client = ResponsesNetworkClient(*errors.toTypedArray()))
          )
          .select()
      val thrown = assertThrows(IOException::class.java) { runBlocking { query.execute() } }
      assertThat(thrown).isSameInstanceAs(errors.last().error)
    }

  @Test
  fun aRequestSentAgainAfterA401KeepsTheNumberOfItsRetry() =
    runBlocking<Unit> {
      // The first retry gets a 401, so it is sent once more with a refreshed token. That is still
      // the first retry, not a new one.
      val client = ResponsesNetworkClient(status(503), status(401), status(200))
      val auth = RefreshingAuthProvider()

      val response = executor(auth = auth, client = client).execute(request("GET"))

      assertThat(response.status).isEqualTo(200)
      assertThat(auth.refreshes).isEqualTo(1)
      assertThat(client.requests.map { it.headers.valuesOf("X-Retry-Count") })
        .containsExactly(emptyList<String>(), listOf("1"), listOf("1"))
        .inOrder()
      assertThat(client.requests.map { it.headers.valuesOf("x-firebase-auth-token") })
        .containsExactly(listOf("cached-token"), listOf("cached-token"), listOf("fresh-token"))
        .inOrder()
    }

  @Test
  fun maxRetriesSetsTheNumberOfRetries() =
    runBlocking<Unit> {
      for (maxRetries in 0..2) {
        val client = ResponsesNetworkClient(status(503), status(503), status(503))

        val response = executor(client = client, maxRetries = maxRetries).execute(request("GET"))

        assertWithMessage("$maxRetries").that(response.status).isEqualTo(503)
        assertWithMessage("$maxRetries").that(client.requests).hasSize(maxRetries + 1)
      }
    }

  /** Waits [delays] in order before each answer of [responses]. */
  private class SlowNetworkClient(
    private val delays: List<Long>,
    private val responses: List<NetworkResponse>
  ) : NetworkClient {
    val requests = mutableListOf<QueryRequest>()

    override suspend fun sendRequest(serviceUrl: HttpUrl, request: QueryRequest): NetworkResponse {
      requests.add(request)
      delay(delays[requests.size - 1])
      return responses[requests.size - 1]
    }
  }

  @Test
  fun anAttemptThatTakesTooLongFailsWithAnInterruptedIOExceptionAndIsNotRetried() =
    runBlocking<Unit> {
      val client = SlowNetworkClient(listOf(60_000L), listOf(status(200)))

      val started = System.nanoTime()
      val response = executor(client = client, timeoutMillis = 50).execute(request("GET"))

      assertThat((System.nanoTime() - started) / 1_000_000).isLessThan(10_000L)
      assertThat(response.status).isEqualTo(0)
      assertThat(response.statusText).isEqualTo("Timeout")
      assertThat(response.isTransportError).isFalse()
      assertThat(response.error).isInstanceOf(InterruptedIOException::class.java)
      assertThat(response.error).hasMessageThat().contains("50 ms")
      // A timed-out attempt is not sent again, because the server may still run it.
      assertThat(client.requests).hasSize(1)
    }

  @Test
  fun eachAttemptGetsTheFullTimeout() =
    runBlocking<Unit> {
      // Together the attempts take longer than the timeout, but each one takes less.
      val client = SlowNetworkClient(listOf(300L, 300L), listOf(status(503), status(200)))

      val response = executor(client = client, timeoutMillis = 500).execute(request("GET"))

      assertThat(response.status).isEqualTo(200)
      assertThat(client.requests).hasSize(2)
    }

  @Test
  fun aRequestWithRetriesDisabledIsSentOnce() =
    runBlocking<Unit> {
      val client = ResponsesNetworkClient(status(503))

      val response = executor(client = client).execute(request("GET", retryEnabled = false))

      assertThat(response.status).isEqualTo(503)
      assertThat(client.requests).hasSize(1)
    }

  @Test
  fun eachWaitGetsTheNumberOfTheAttemptBeforeIt() =
    runBlocking<Unit> {
      val attempts = mutableListOf<Int>()
      val client = ResponsesNetworkClient(status(503), status(502), status(200))
      val executor =
        executor(
          client = client,
          retryDelayMillis = { attempt ->
            attempts.add(attempt)
            0L
          }
        )

      executor.execute(request("GET"))

      assertThat(attempts).containsExactly(0, 1).inOrder()
    }

  @Test
  fun theDefaultWaitBacksOffWithARandomPart() {
    // 1, 2, then 4 seconds, each up to 25% more or less, whatever the random numbers.
    val waits =
      (0 until 100).map { seed ->
        val random = Random(seed)
        (0..2).map { attempt -> defaultRetryDelayMillis(attempt, random) }
      }
    for ((seed, wait) in waits.withIndex()) {
      for ((attempt, millis) in wait.withIndex()) {
        val base = 1000L shl attempt
        assertWithMessage("seed $seed, attempt $attempt").that(millis).isAtLeast(base * 3 / 4)
        assertWithMessage("seed $seed, attempt $attempt").that(millis).isAtMost(base * 5 / 4)
      }
    }
    // The apps that failed at the same time do not all retry at the same time.
    assertThat(waits.map { it[0] }.toSet().size).isGreaterThan(50)
    // The wait never exceeds the limit.
    for (seed in 0 until 100) {
      assertThat(defaultRetryDelayMillis(40, Random(seed))).isAtMost(MAX_RETRY_DELAY_MILLIS)
    }
  }

  @Test
  fun closingTheExecutorDuringTheWaitStopsTheRetries() =
    runBlocking<Unit> {
      val client = ResponsesNetworkClient(status(503), status(200))
      val waiting = CompletableDeferred<Unit>()
      val retrying =
        executor(
          client = client,
          retryDelayMillis = {
            waiting.complete(Unit)
            60_000L
          }
        )
      val response = async { retrying.execute(request("GET")) }
      waiting.await()

      retrying.close()

      // The wait of 60 seconds stops at once.
      assertClosed(withTimeout(5_000) { response.await() })
      assertThat(client.requests).hasSize(1)
    }

  @Test
  fun cancellingTheCoroutineCancelsTheWait() =
    runBlocking<Unit> {
      val client = ResponsesNetworkClient(status(503), status(200))
      val slow = executor(client = client, retryDelayMillis = { 60_000L })

      val started = System.nanoTime()
      val response = withTimeoutOrNull(100) { slow.execute(request("GET")) }

      assertThat(response).isNull()
      assertThat((System.nanoTime() - started) / 1_000_000).isLessThan(10_000L)
      assertThat(client.requests).hasSize(1)
      // The cancellation of a caller does not close the executor.
      assertThat(slow.isClosed).isFalse()
    }

  private fun executor(
    schema: String? = "public",
    auth: InternalAuthProvider? = null,
    appCheck: InteropAppCheckTokenProvider? = null,
    client: NetworkClient = MockNetworkClient(),
    headers: Map<String, String> = emptyMap(),
    appId: String? = null,
    timeoutMillis: Long = 30_000L,
    maxRetries: Int = MAX_RETRIES,
    // No wait between the retries, so that the tests stay fast.
    retryDelayMillis: (Int) -> Long = { 0L }
  ) =
    OperationsExecutor(
      schema,
      SERVICE_URL,
      authDeferred = auth?.let { deferred(it) },
      appCheckDeferred = appCheck?.let { deferred(it) },
      networkClient = client,
      headers = headers,
      appId = appId,
      timeoutMillis = timeoutMillis,
      maxRetries = maxRetries,
      retryDelayMillis = retryDelayMillis
    )

  private fun request(
    method: String,
    headers: Map<String, String> = emptyMap(),
    retryEnabled: Boolean = true
  ) =
    QueryRequest(
      httpMethod = method,
      pathSegments = listOf("table"),
      queryParams = emptyList(),
      headers = headers,
      retryEnabled = retryEnabled
    )

  private companion object {
    val SERVICE_URL = "http://host".toHttpUrl()
  }
}
