/*
 * Copyright 2024 Google LLC
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

package com.google.firebase.dataconnect.core

import com.google.firebase.annotations.DeferredApi
import com.google.firebase.appcheck.interop.InteropAppCheckTokenProvider
import com.google.firebase.auth.internal.InternalAuthProvider
import com.google.firebase.dataconnect.DataConnectException
import com.google.firebase.dataconnect.core.Globals.toScrubbedAccessToken
import com.google.firebase.dataconnect.core.LoggerGlobals.debug
import com.google.firebase.dataconnect.core.LoggerGlobals.warn
import com.google.firebase.dataconnect.util.SequencedReference
import com.google.firebase.dataconnect.util.SequencedReference.Companion.nextSequenceNumber
import com.google.firebase.inject.Deferred.DeferredHandler
import com.google.firebase.inject.Provider
import com.google.firebase.internal.api.FirebaseNoSignedInUserException
import com.google.firebase.util.nextAlphanumericString
import java.lang.ref.WeakReference
import kotlin.coroutines.coroutineContext
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Base class that shares logic for managing the Auth token and AppCheck token. */
internal sealed class DataConnectCredentialsTokenManager<T : Any>(
  private val deferredProvider: com.google.firebase.inject.Deferred<T>,
  parentCoroutineScope: CoroutineScope,
  private val blockingDispatcher: CoroutineDispatcher,
  protected val logger: Logger,
) {
  val instanceId: String
    get() = logger.nameWithId

  @Suppress("LeakingThis") private val weakThis = WeakReference(this)

  private val coroutineScope =
    CoroutineScope(
      parentCoroutineScope.coroutineContext +
        SupervisorJob(parentCoroutineScope.coroutineContext[Job]) +
        CoroutineName(instanceId) +
        CoroutineExceptionHandler { context, throwable ->
          logger.warn(throwable) {
            "uncaught exception from a coroutine named ${context[CoroutineName]}: $throwable"
          }
        }
    )

  private sealed interface State<out T> {

    /**
     * State indicating that the object has just been created and [initialize] has not yet been
     * called.
     */
    object New : State<Nothing>

    /**
     * State indicating that [initialize] has been invoked but the token provider is not (yet?)
     * available.
     */
    data class Initialized(override val forceTokenRefresh: Boolean) :
      StateWithForceTokenRefresh<Nothing> {
      constructor() : this(false)
    }

    /** State indicating that [close] has been invoked. */
    object Closed : State<Nothing>

    sealed interface StateWithForceTokenRefresh<out T> : State<T> {
      /** The value to specify for `forceRefresh` on the next invocation of [getToken]. */
      val forceTokenRefresh: Boolean
    }

    sealed interface StateWithProvider<out T> : State<T> {
      /** The token provider, [InternalAuthProvider] or [InteropAppCheckTokenProvider] */
      val provider: T
    }

    /** State indicating that there is no outstanding "get token" request. */
    data class Idle<T>(override val provider: T, override val forceTokenRefresh: Boolean) :
      StateWithProvider<T>, StateWithForceTokenRefresh<T>

    /** State indicating that there _is_ an outstanding "get token" request. */
    data class Active<out T>(
      override val provider: T,

      /** The job that is performing the "get token" request. */
      val job: Deferred<SequencedReference<Result<GetTokenResult>>>
    ) : StateWithProvider<T>
  }

  /** The current state of this object. */
  private val state = MutableStateFlow<State<T>>(State.New)

  /**
   * Adds the token listener to the given provider.
   *
   * @see removeTokenListener
   */
  @DeferredApi protected abstract fun addTokenListener(provider: T)

  /**
   * Removes the token listener from the given provider.
   *
   * @see addTokenListener
   */
  protected abstract fun removeTokenListener(provider: T)

  /**
   * Starts an asynchronous task to get a new access token from the given provider, forcing a token
   * refresh if and only if `forceRefresh` is true.
   */
  protected abstract suspend fun getToken(provider: T, forceRefresh: Boolean): GetTokenResult

  /**
   * Initializes this object.
   *
   * Before calling this method, the _only_ other methods that are allowed to be called on this
   * object are [awaitTokenProvider] and [close].
   *
   * This method may only be called once; subsequent calls result in an exception.
   */
  fun initialize() {
    logger.debug { "initialize()" }

    state.update { currentState ->
      when (currentState) {
        is State.New -> State.Initialized()
        is State.Closed ->
          throw IllegalStateException("initialize() cannot be called after close()")
        else -> throw IllegalStateException("initialize() has already been called")
      }
    }

    // Call `whenAvailable()` on a non-main thread because it accesses SharedPreferences, which
    // performs disk i/o, violating the StrictMode policy android.os.strictmode.DiskReadViolation.
    val coroutineName = CoroutineName("k6rwgqg9gh $instanceId whenAvailable")
    coroutineScope.launch(coroutineName + blockingDispatcher) {
      deferredProvider.whenAvailable(DeferredProviderHandlerImpl(weakThis))
    }
  }

  /**
   * Closes this object, releasing its resources, unregistering any registered listeners, and
   * cancelling any in-flight token requests.
   *
   * This method is re-entrant; that is, it may be invoked multiple times; however, only one such
   * invocation will actually do the work of closing. If invoked concurrently, invocations other
   * than the one that actually does the work of closing may return _before_ the work of closing has
   * actually completed. In other words, this method does _not_ block waiting for the work of
   * closing to be completed by another thread.
   */
  fun close() {
    logger.debug { "close()" }

    weakThis.clear()
    coroutineScope.cancel()

    val oldState = state.getAndUpdate { State.Closed }
    when (oldState) {
      is State.New -> {}
      is State.Initialized -> {}
      is State.Closed -> {}
      is State.StateWithProvider -> {
        removeTokenListener(oldState.provider)
      }
    }
  }

  /**
   * Suspends until the token provider becomes available to this object.
   *
   * This method _may_ be called before [initialize], which is the method that asynchronously gets
   * the token provider.
   *
   * If [close] has been invoked, or is invoked _before_ a token provider becomes available, then
   * this method returns normally, as if a token provider _had_ become available.
   */
  suspend fun awaitTokenProvider() {
    logger.debug { "awaitTokenProvider() start" }
    val currentState =
      state
        .filter {
          when (it) {
            State.Closed -> true
            is State.New -> false
            is State.Initialized -> false
            is State.Idle -> true
            is State.Active -> true
          }
        }
        .first()
    logger.debug { "awaitTokenProvider() done: currentState=$currentState" }
  }

  /**
   * Sets a flag to force-refresh the token upon the next call to [getToken].
   *
   * If [close] has been called, this method does nothing.
   */
  fun forceRefresh() {
    logger.debug { "forceRefresh()" }
    val oldState =
      state.getAndUpdate { currentState ->
        val newState =
          when (currentState) {
            is State.Closed -> State.Closed
            is State.New -> currentState
            is State.Initialized -> currentState.copy(forceTokenRefresh = true)
            is State.Idle -> currentState.copy(forceTokenRefresh = true)
            is State.Active -> State.Idle(currentState.provider, forceTokenRefresh = true)
          }

        check(
          newState is State.New ||
            newState is State.Closed ||
            newState is State.StateWithForceTokenRefresh<T>
        ) {
          "internal error gbazc7qr66: newState should have been Closed or " +
            "StateWithForceTokenRefresh, but got: $newState"
        }
        if (newState is State.StateWithForceTokenRefresh<T>) {
          check(newState.forceTokenRefresh) {
            "internal error fnzwyrsez2: newState.forceTokenRefresh should have been true"
          }
        }

        newState
      }

    when (oldState) {
      is State.Closed -> {}
      is State.New ->
        throw IllegalStateException("initialize() must be called before forceRefresh()")
      is State.Initialized -> {}
      is State.Idle -> {}
      is State.Active -> {
        val message = "needs token refresh (wgrwbrvjxt)"
        oldState.job.cancel(message, ForceRefresh(message))
      }
    }
  }

  private fun newActiveState(
    invocationId: String,
    provider: T,
    forceRefresh: Boolean
  ): State.Active<T> {
    val coroutineName =
      CoroutineName(
        "$instanceId 535gmcvv5a $invocationId getToken(" +
          "provider=${provider}, forceRefresh=$forceRefresh)"
      )
    val job =
      coroutineScope.async(coroutineName, CoroutineStart.LAZY) {
        val sequenceNumber = nextSequenceNumber()
        logger.debug { "$invocationId getToken(forceRefresh=$forceRefresh)" }
        val result = runCatching { getToken(provider, forceRefresh) }
        SequencedReference(sequenceNumber, result)
      }
    return State.Active(provider, job)
  }

  /**
   * Gets the access token, force-refreshing it if [forceRefresh] has been called.
   *
   * @throws DataConnectException if [close] has been called or is called while the operation is in
   * progress.
   */
  suspend fun getToken(requestId: String): String? {
    val invocationId = "gat" + Random.nextAlphanumericString(length = 8)
    logger.debug { "$invocationId getToken(requestId=$requestId)" }
    while (true) {
      val attemptSequenceNumber = nextSequenceNumber()
      val oldState = state.value

      val newState: State.Active<T> =
        when (oldState) {
          is State.New ->
            throw IllegalStateException("initialize() must be called before getToken()")
          is State.Closed -> {
            logger.debug {
              "$invocationId getToken() throws CredentialsTokenManagerClosedException" +
                " because the DataConnectCredentialsTokenManager instance has been closed"
            }
            throw CredentialsTokenManagerClosedException(this)
          }
          is State.Initialized -> {
            logger.debug {
              "$invocationId getToken() returns null (token provider is not (yet?) available)"
            }
            return null
          }
          is State.Idle -> {
            newActiveState(invocationId, oldState.provider, oldState.forceTokenRefresh)
          }
          is State.Active -> {
            if (
              oldState.job.isCompleted &&
                !oldState.job.isCancelled &&
                oldState.job.await().sequenceNumber < attemptSequenceNumber
            ) {
              newActiveState(invocationId, oldState.provider, forceRefresh = false)
            } else {
              oldState
            }
          }
        }

      if (oldState !== newState) {
        if (!state.compareAndSet(oldState, newState)) {
          continue
        }
        logger.debug {
          "$invocationId getToken() starts a new coroutine to get the token" +
            " (oldState=${oldState::class.simpleName})"
        }
      }

      val jobResult = newState.job.runCatching { await() }

      // Ensure that any exception checking below is due to an exception that happened in the
      // coroutine that called getToken(), not from the calling coroutine being cancelled.
      coroutineContext.ensureActive()

      val sequencedResult = jobResult.getOrNull()
      if (sequencedResult !== null && sequencedResult.sequenceNumber < attemptSequenceNumber) {
        logger.debug { "$invocationId getToken() got an old result; retrying" }
        continue
      }

      val exception = jobResult.exceptionOrNull() ?: jobResult.getOrNull()?.ref?.exceptionOrNull()
      if (exception !== null) {
        val retryException = exception.getRetryIndicator()
        if (retryException !== null) {
          logger.debug { "$invocationId getToken() retrying due to ${retryException.message}" }
          continue
        } else if (exception is FirebaseNoSignedInUserException) {
          logger.debug {
            "$invocationId getToken() returns null (FirebaseAuth reports no signed-in user)"
          }
          return null
        } else if (exception is CancellationException) {
          logger.warn(exception) {
            "$invocationId getToken() throws GetTokenCancelledException," +
              " likely due to DataConnectCredentialsTokenManager.close() being called"
          }
          throw GetTokenCancelledException(exception)
        } else {
          logger.warn(exception) { "$invocationId getToken() failed unexpectedly: $exception" }
          throw exception
        }
      }

      val accessToken = sequencedResult!!.ref.getOrThrow().token
      logger.debug {
        "$invocationId getToken() returns retrieved token: ${accessToken?.toScrubbedAccessToken()}"
      }
      return accessToken
    }
  }

  private sealed class GetTokenRetry(message: String) : Exception(message)
  private class ForceRefresh(message: String) : GetTokenRetry(message)
  private class NewProvider(message: String) : GetTokenRetry(message)

  @DeferredApi
  private fun onProviderAvailable(newProvider: T) {
    logger.debug { "onProviderAvailable(newProvider=$newProvider)" }
    addTokenListener(newProvider)

    val oldState =
      state.getAndUpdate { currentState ->
        when (currentState) {
          is State.New -> currentState
          is State.Closed -> State.Closed
          is State.Initialized -> State.Idle(newProvider, currentState.forceTokenRefresh)
          is State.Idle -> State.Idle(newProvider, currentState.forceTokenRefresh)
          is State.Active -> State.Idle(newProvider, forceTokenRefresh = false)
        }
      }

    when (oldState) {
      is State.New ->
        throw IllegalStateException(
          "internal error sdpzwhmhd3: " +
            "initialize() should have been called before onProviderAvailable()"
        )
      is State.Closed -> {
        logger.debug {
          "onProviderAvailable(newProvider=$newProvider)" +
            " unregistering token listener that was just added"
        }
        removeTokenListener(newProvider)
      }
      is State.Initialized -> {}
      is State.Idle -> {}
      is State.Active -> {
        val newProviderClassName = newProvider::class.qualifiedName
        val message = "a new provider $newProviderClassName is available (symhxtmazy)"
        oldState.job.cancel(message, NewProvider(message))
      }
    }
  }

  /**
   * An implementation of [DeferredHandler] to be registered with the [Deferred] given to the
   * constructor.
   *
   * This separate class is used (as opposed to using a more-convenient lambda) to avoid holding a
   * strong reference to the [DataConnectCredentialsTokenManager] instance indefinitely, in the case
   * that the callback never occurs.
   */
  private class DeferredProviderHandlerImpl<T : Any>(
    private val weakCredentialsTokenManagerRef: WeakReference<DataConnectCredentialsTokenManager<T>>
  ) : DeferredHandler<T> {
    override fun handle(provider: Provider<T>) {
      weakCredentialsTokenManagerRef.get()?.onProviderAvailable(provider.get())
    }
  }

  private class CredentialsTokenManagerClosedException(
    tokenProvider: DataConnectCredentialsTokenManager<*>
  ) :
    DataConnectException(
      "DataConnectCredentialsTokenManager ${tokenProvider.instanceId} was closed (code cqrbq4zfvy)"
    )

  private class GetTokenCancelledException(cause: Throwable) :
    DataConnectException("getToken() was cancelled, likely by close() (code rqdd4jam9d)", cause)

  protected data class GetTokenResult(val token: String?)

  private companion object {

    fun Throwable.getRetryIndicator(): GetTokenRetry? {
      var currentCause: Throwable? = this
      while (true) {
        if (currentCause === null) {
          return null
        } else if (currentCause is GetTokenRetry) {
          return currentCause
        }
        currentCause = currentCause.cause ?: return null
      }
    }
  }
}
