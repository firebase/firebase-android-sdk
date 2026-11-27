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

import com.google.firebase.appcheck.interop.InteropAppCheckTokenProvider
import com.google.firebase.auth.internal.InternalAuthProvider
import com.google.firebase.inject.Deferred.DeferredHandler
import com.google.firebase.inject.Provider
import com.google.firebase.internal.api.FirebaseNoSignedInUserException
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Gets the tokens of one provider, Firebase Authentication or Firebase App Check, for the requests
 * of an [OperationsExecutor].
 *
 * The whole state is one immutable [State] in a [MutableStateFlow], and every change is one atomic
 * update, so the provider, a pending refresh, and the token request in flight always agree,
 * whatever the threads that call this class.
 * - [initialize] looks up the provider on the blocking dispatcher, because getting a provider can
 * read the disk. [getToken] waits for that lookup, so that a provider that the app links is used
 * from the first request. A provider that becomes available later, for example from a dynamic
 * feature module, is used from then on.
 * - [getToken] returns a token that was requested after the call started, so it never returns a
 * token from before a sign-in or a sign-out.
 * - [forceRefresh] makes the next [getToken] refresh the token, after the server rejected it. The
 * requests that the server rejected with the same token cause one refresh.
 *
 * @param T The type of the provider.
 * @param deferredProvider Provides the provider, now or later.
 * @param blockingDispatcher Runs the lookup of the provider.
 * @param nonBlockingDispatcher Runs the token requests.
 */
internal sealed class CredentialsTokenManager<T : Any>(
  private val deferredProvider: com.google.firebase.inject.Deferred<T>,
  private val blockingDispatcher: CoroutineDispatcher,
  nonBlockingDispatcher: CoroutineDispatcher
) {
  private val coroutineScope = CoroutineScope(SupervisorJob() + nonBlockingDispatcher)

  @Suppress("LeakingThis") private val weakThis = WeakReference(this)

  private sealed interface State<out T> {
    /** [initialize] was not called yet. */
    object New : State<Nothing>

    /** [initialize] was called, and the provider is not available, or not yet. */
    data class Initialized(override val forceTokenRefresh: Boolean) :
      StateWithForceTokenRefresh<Nothing>

    /** [close] was called. */
    object Closed : State<Nothing>

    sealed interface StateWithForceTokenRefresh<out T> : State<T> {
      /** Whether the next token request refreshes the token. */
      val forceTokenRefresh: Boolean
    }

    sealed interface StateWithProvider<out T> : State<T> {
      /** The provider of the tokens. */
      val provider: T
    }

    /** The provider is available, and no token request is in flight. */
    data class Idle<T>(override val provider: T, override val forceTokenRefresh: Boolean) :
      StateWithProvider<T>, StateWithForceTokenRefresh<T>

    /**
     * A token request is in flight, or completed and not replaced yet.
     *
     * @property forceRefresh Whether the request refreshes the token.
     * @property job The coroutine that requests the token. It starts when a caller awaits it.
     */
    data class Active<out T>(
      override val provider: T,
      val forceRefresh: Boolean,
      val job: Deferred<SequencedReference<Result<String?>>>
    ) : StateWithProvider<T>
  }

  /** The current state of this object. */
  private val state = MutableStateFlow<State<T>>(State.New)

  /**
   * The newest token that a token request returned. A token request sets it before it completes, so
   * that [forceRefresh] knows the token of every completed request.
   */
  private val latestToken = MutableStateFlow(SequencedReference<String?>(0, null))

  /** Completes when the lookup of the provider returned, or when this object is closed. */
  private val providerLookup = CompletableDeferred<Unit>()

  /** Requests a token from [provider], and refreshes it when [forceRefresh] is `true`. */
  protected abstract suspend fun fetchToken(provider: T, forceRefresh: Boolean): String?

  /**
   * Starts the lookup of the provider. It must be called once, before the other methods, except
   * [close].
   */
  fun initialize() {
    state.update { currentState ->
      when (currentState) {
        is State.New -> State.Initialized(forceTokenRefresh = false)
        is State.Closed ->
          throw IllegalStateException("initialize() cannot be called after close()")
        else -> throw IllegalStateException("initialize() has already been called")
      }
    }

    // Getting a provider can read SharedPreferences, which StrictMode reports on the main thread.
    coroutineScope.launch(blockingDispatcher) {
      try {
        // whenAvailable() runs the handler before it returns when the provider is available.
        deferredProvider.whenAvailable(DeferredProviderHandler(weakThis))
        providerLookup.complete(Unit)
      } catch (e: Exception) {
        // getToken() throws the error, so that a request without its token is not sent.
        providerLookup.completeExceptionally(e)
      }
    }
  }

  /**
   * Closes this object: [getToken] throws an [IllegalStateException] from then on, the token
   * requests in flight are cancelled, and a provider that becomes available later is ignored.
   * Calling it again has no effect.
   */
  fun close() {
    weakThis.clear()
    coroutineScope.cancel()
    state.value = State.Closed
    providerLookup.complete(Unit)
  }

  /**
   * Makes the next [getToken] refresh the token, because the server rejected [rejectedToken]. When
   * the refresh fails, the [getToken] after it refreshes the token again.
   *
   * Nothing changes when a refresh is in flight, or when a token request returned another token
   * since [rejectedToken], because [getToken] returns that newer token. That way, the requests that
   * the server rejected with the same token cause one refresh.
   */
  fun forceRefresh(rejectedToken: String) {
    var replaced: State.Active<T>? = null
    state.update { currentState ->
      replaced = null
      when (currentState) {
        is State.New ->
          throw IllegalStateException("initialize() must be called before forceRefresh()")
        is State.Closed -> currentState
        is State.Initialized -> currentState.copy(forceTokenRefresh = true)
        is State.Idle ->
          if (latestToken.value.ref == rejectedToken) currentState.copy(forceTokenRefresh = true)
          else currentState
        is State.Active ->
          when {
            // Check the job before latestToken, which a token request sets before it completes.
            currentState.forceRefresh && !currentState.job.isCompleted -> currentState
            latestToken.value.ref != rejectedToken -> currentState
            else -> {
              replaced = currentState
              State.Idle(currentState.provider, forceTokenRefresh = true)
            }
          }
      }
    }
    replaced?.job?.cancel("the token needs a refresh", ForceRefresh("the token needs a refresh"))
  }

  /**
   * Returns a token that was requested after this call started, or `null` when the provider is not
   * available. The token is refreshed when [forceRefresh] asked for it. The callers that arrive
   * before a token request starts share it.
   *
   * @throws IllegalStateException When this object is closed, before or during the call.
   * @throws Exception The error of the provider, or of the lookup of the provider.
   */
  suspend fun getToken(): String? {
    providerLookup.await()
    while (true) {
      val attemptSequenceNumber = nextSequenceNumber()
      val oldState = state.value

      val newState: State.Active<T> =
        when (oldState) {
          is State.New ->
            throw IllegalStateException("initialize() must be called before getToken()")
          is State.Closed -> throw IllegalStateException(CLOSED_MESSAGE)
          is State.Initialized -> return null
          is State.Idle -> newActiveState(oldState.provider, oldState.forceTokenRefresh)
          is State.Active ->
            if (
              oldState.job.isCompleted &&
                !oldState.job.isCancelled &&
                oldState.job.await().sequenceNumber < attemptSequenceNumber
            ) {
              // A forced refresh that failed is forced again, so that the provider does not
              // return the token that the server rejected.
              val refreshFailed = oldState.forceRefresh && oldState.job.await().ref.isFailure
              newActiveState(oldState.provider, forceRefresh = refreshFailed)
            } else {
              oldState
            }
        }

      if (oldState !== newState && !state.compareAndSet(oldState, newState)) {
        // Another caller changed the state first, so the new job is never started.
        newState.job.cancel()
        continue
      }

      val jobResult = newState.job.runCatching { await() }
      // Throws when the caller is cancelled, so that an exception below comes from the job.
      currentCoroutineContext().ensureActive()

      val exception = jobResult.exceptionOrNull()
      if (exception != null) {
        // forceRefresh() or a new provider replaced the job.
        if (exception.isRetryIndicator()) continue
        // close() cancelled the job.
        throw IllegalStateException(CLOSED_MESSAGE, exception)
      }

      val result = jobResult.getOrThrow()
      // The token was requested before this call started.
      if (result.sequenceNumber < attemptSequenceNumber) continue
      return result.ref.getOrThrow()
    }
  }

  private fun newActiveState(provider: T, forceRefresh: Boolean): State.Active<T> {
    val job =
      coroutineScope.async(start = CoroutineStart.LAZY) {
        val sequenceNumber = nextSequenceNumber()
        val result = runCatching { fetchToken(provider, forceRefresh) }
        result.onSuccess { token ->
          latestToken.update { latest ->
            if (latest.sequenceNumber > sequenceNumber) latest
            else SequencedReference(sequenceNumber, token)
          }
        }
        SequencedReference(sequenceNumber, result)
      }
    return State.Active(provider, forceRefresh, job)
  }

  private fun onProviderAvailable(newProvider: T) {
    var replaced: State.Active<T>? = null
    state.update { currentState ->
      replaced = null
      when (currentState) {
        is State.New ->
          throw IllegalStateException("initialize() must be called before onProviderAvailable()")
        is State.Closed -> currentState
        is State.Initialized -> State.Idle(newProvider, currentState.forceTokenRefresh)
        is State.Idle -> State.Idle(newProvider, currentState.forceTokenRefresh)
        is State.Active -> {
          replaced = currentState
          State.Idle(newProvider, forceTokenRefresh = false)
        }
      }
    }
    replaced?.job?.cancel("a new provider is available", NewProvider("a new provider is available"))
  }

  /**
   * Gives the provider to the object. It holds the object weakly, so that a provider that never
   * becomes available does not keep a closed object in memory. A `null` provider counts as not
   * available, so the requests are sent without its token.
   */
  private class DeferredProviderHandler<T : Any>(
    private val tokenManager: WeakReference<CredentialsTokenManager<T>>
  ) : DeferredHandler<T> {
    override fun handle(provider: Provider<T>) {
      val value: T = provider.get() ?: return
      tokenManager.get()?.onProviderAvailable(value)
    }
  }

  /** Why a token request was cancelled to be replaced. Its callers request a token again. */
  private sealed class GetTokenRetry(message: String) : Exception(message)

  private class ForceRefresh(message: String) : GetTokenRetry(message)

  private class NewProvider(message: String) : GetTokenRetry(message)

  private companion object {
    fun Throwable.isRetryIndicator(): Boolean {
      var cause: Throwable? = this
      while (cause != null) {
        if (cause is GetTokenRetry) return true
        cause = cause.cause
      }
      return false
    }
  }
}

/**
 * Gets the Firebase Authentication ID token of the signed-in user, or `null` without a signed-in
 * user.
 */
internal class AuthTokenManager(
  deferredProvider: com.google.firebase.inject.Deferred<InternalAuthProvider>,
  blockingDispatcher: CoroutineDispatcher,
  nonBlockingDispatcher: CoroutineDispatcher
) :
  CredentialsTokenManager<InternalAuthProvider>(
    deferredProvider,
    blockingDispatcher,
    nonBlockingDispatcher
  ) {
  override suspend fun fetchToken(provider: InternalAuthProvider, forceRefresh: Boolean): String? =
    try {
      provider.getAccessToken(forceRefresh).await().token
    } catch (e: FirebaseNoSignedInUserException) {
      null
    }
}

/**
 * Gets the Firebase App Check token. When App Check fails, it returns a placeholder token together
 * with the error, and that token is sent: the server then decides whether to accept the request.
 */
internal class AppCheckTokenManager(
  deferredProvider: com.google.firebase.inject.Deferred<InteropAppCheckTokenProvider>,
  blockingDispatcher: CoroutineDispatcher,
  nonBlockingDispatcher: CoroutineDispatcher
) :
  CredentialsTokenManager<InteropAppCheckTokenProvider>(
    deferredProvider,
    blockingDispatcher,
    nonBlockingDispatcher
  ) {
  override suspend fun fetchToken(
    provider: InteropAppCheckTokenProvider,
    forceRefresh: Boolean
  ): String? {
    val result = provider.getToken(forceRefresh).await()
    return result.token.takeUnless { it.isEmpty() }
  }
}

/** A value, with a number that is greater for the values that were created later. */
internal data class SequencedReference<out T>(val sequenceNumber: Long, val ref: T)

private val lastSequenceNumber = AtomicLong(0)

/** Returns a number that is greater than every number that it returned before in this process. */
private fun nextSequenceNumber(): Long = lastSequenceNumber.incrementAndGet()
