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

import com.google.android.gms.tasks.Task
import com.google.firebase.annotations.DeferredApi
import com.google.firebase.appcheck.interop.InteropAppCheckTokenProvider
import com.google.firebase.auth.GetTokenResult
import com.google.firebase.auth.internal.InternalAuthProvider
import com.google.firebase.dataconnect.DataConnectException
import com.google.firebase.dataconnect.util.SequencedReference
import com.google.firebase.dataconnect.util.nextSequenceNumber
import com.google.firebase.inject.Deferred.DeferredHandler
import com.google.firebase.inject.Provider
import com.google.firebase.internal.api.FirebaseNoSignedInUserException
import com.google.firebase.util.nextAlphanumericString
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.yield

/** Base class that shares logic for managing the Auth token and AppCheck token. */
internal sealed class DataConnectCredentialsTokenManager<T : Any, L : Any>(
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

  private interface ProviderListenerPair<T, L> {
    val provider: T?
    val tokenListener: L
  }

  private sealed interface State<out T, out L> {

    /** State indicating that [initialize] has not yet been invoked. */
    object Uninitialized : State<Nothing, Nothing>

    /** State indicating that [close] has been invoked. */
    object Closed : State<Nothing, Nothing>

    /**
     * State indicating that [initialize] has been invoked and there is no outstanding "get token"
     * request.
     */
    class Ready<T, L>(

      /**
       * The [InternalAuthProvider] or [InteropAppCheckTokenProvider]; may be null if the deferred
       * has not yet given us a provider.
       */
      override val provider: T?,

      /** The token listener that is, or will be, registered with [provider]. */
      override val tokenListener: L,

      /** The value to specify for `forceRefresh` on the next invocation of [getAccessToken]. */
      val forceTokenRefresh: Boolean
    ) : State<T, L>, ProviderListenerPair<T, L>

    /**
     * State indicating that [initialize] has been invoked and there _is_ an outstanding "get token"
     * request.
     */
    class Active<T, L>(

      /**
       * The [InternalAuthProvider] or [InteropAppCheckTokenProvider] that is performing the "get
       * token" request.
       */
      override val provider: T,

      /** The token listener that is registered with [provider]. */
      override val tokenListener: L,

      /** The job that is performing the "get token" request. */
      val job: Deferred<SequencedReference<Result<GetTokenResult>>>
    ) : State<T, L>, ProviderListenerPair<T, L>
  }

  /**
   * The current state of this object. The value should only be changed in a compare-and-swap loop
   * in order to be thread-safe. Such a loop should call `yield()` on each iteration to allow other
   * coroutines to run on the thread.
   */
  private val state = AtomicReference<State<T, L>>(State.Uninitialized)

  /**
   * Creates and returns a new "token listener" that will be registered with the provider returned
   * from the [deferredProvider] specified to the constructor.
   *
   * @see addTokenListener
   * @see removeTokenListener
   */
  protected abstract fun newTokenListener(): L

  /**
   * Adds the given listener to the given provider.
   *
   * @see removeTokenListener
   */
  @DeferredApi protected abstract fun addTokenListener(provider: T, listener: L)

  /**
   * Removes the given listener from the given provider.
   *
   * @see addTokenListener
   */
  protected abstract fun removeTokenListener(provider: T, listener: L)

  /**
   * Starts an asynchronous task to get a new access token from the given provider, forcing a token
   * refresh if and only if `forceRefresh` is true.
   */
  protected abstract fun getAccessToken(provider: T, forceRefresh: Boolean): Task<GetTokenResult>

  /**
   * Initializes this object, acquiring resources and registering necessary listeners.
   *
   * This method must be called exactly once before any other methods on this object. The only
   * exception is that [close] may be invoked without having invoked this method.
   *
   * @throws IllegalStateException if invoked more than once or after [close].
   *
   * @see close
   */
  fun initialize() {
    val newState =
      State.Ready(provider = null, tokenListener = newTokenListener(), forceTokenRefresh = false)

    while (true) {
      val oldState = state.get()
      if (oldState != State.Uninitialized) {
        throw IllegalStateException(
          if (oldState == State.Closed) {
            "initialize() may not be called after close()"
          } else {
            "initialize() has already been called"
          }
        )
      }

      if (state.compareAndSet(oldState, newState)) {
        break
      }
    }

    // Call `whenAvailable()` on a non-main thread because it accesses SharedPreferences, which
    // performs disk i/o, violating the StrictMode policy android.os.strictmode.DiskReadViolation.
    val coroutineName = CoroutineName("k6rwgqg9gh $instanceId whenAvailable")
    coroutineScope.launch(coroutineName + blockingDispatcher) {
      deferredProvider.whenAvailable(DeferredProviderHandlerImpl(weakThis, newState.tokenListener))
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
    setClosedState()
  }

  // This function must ONLY be called from close().
  private fun setClosedState() {
    while (true) {
      val oldState = state.get()
      val providerListenerPair: ProviderListenerPair<T, L>? =
        when (oldState) {
          is State.Closed -> return
          is State.Uninitialized -> null
          is State.Ready -> oldState
          is State.Active -> oldState
        }

      if (state.compareAndSet(oldState, State.Closed)) {
        providerListenerPair?.run {
          provider?.let { provider ->
            runIgnoringFirebaseAppDeleted { removeTokenListener(provider, tokenListener) }
          }
        }
        return
      }
    }
  }

  /**
   * Sets a flag to force-refresh the token upon the next call to [getAccessToken].
   *
   * If [close] has been called, this method does nothing.
   *
   * @throws IllegalStateException if [initialize] has not been called.
   */
  suspend fun forceRefresh() {
    logger.debug { "forceRefresh()" }
    while (true) {
      val oldState = state.get()
      val providerListenerPair: ProviderListenerPair<T, L> =
        when (oldState) {
          is State.Uninitialized ->
            throw IllegalStateException("forceRefresh() cannot be called before initialize()")
          is State.Closed -> return
          is State.Ready -> oldState
          is State.Active -> {
            val message = "needs token refresh (wgrwbrvjxt)"
            oldState.job.cancel(message, ForceRefresh(message))
            oldState
          }
        }

      val newState =
        State.Ready(
          providerListenerPair.provider,
          providerListenerPair.tokenListener,
          forceTokenRefresh = true
        )
      if (state.compareAndSet(oldState, newState)) {
        break
      }

      yield()
    }
  }

  private fun newActiveState(
    invocationId: String,
    provider: T,
    tokenListener: L,
    forceRefresh: Boolean
  ): State.Active<T, L> {
    val coroutineName =
      CoroutineName(
        "$instanceId 535gmcvv5a $invocationId getAccessToken(" +
          "provider=${provider}, forceRefresh=$forceRefresh)"
      )
    val job =
      coroutineScope.async(coroutineName, CoroutineStart.LAZY) {
        val sequenceNumber = nextSequenceNumber()
        logger.debug { "$invocationId getAccessToken(forceRefresh=$forceRefresh)" }
        val result = runCatching { getAccessToken(provider, forceRefresh).await() }
        SequencedReference(sequenceNumber, result)
      }
    return State.Active(provider, tokenListener, job)
  }

  /**
   * Gets the access token, force-refreshing it if [forceRefresh] has been called.
   *
   * @throws IllegalStateException if [initialize] has not been called.
   * @throws DataConnectException if [close] has not been called or is called while the operation is
   * in progress.
   */
  suspend fun getAccessToken(requestId: String): String? {
    val invocationId = "gat" + Random.nextAlphanumericString(length = 8)
    logger.debug { "$invocationId getAccessToken(requestId=$requestId)" }
    while (true) {
      val attemptSequenceNumber = nextSequenceNumber()
      val oldState = state.get()

      val newState: State.Active<T, L> =
        when (oldState) {
          is State.Uninitialized ->
            throw IllegalStateException("getAccessToken() cannot be called before initialize()")
          is State.Closed -> {
            logger.debug {
              "$invocationId getAccessToken() throws CredentialsTokenManagerClosedException" +
                " because the DataConnectCredentialsTokenManager instance has been closed"
            }
            throw CredentialsTokenManagerClosedException(this)
          }
          is State.Ready -> {
            if (oldState.provider === null) {
              logger.debug {
                "$invocationId getAccessToken() returns null" +
                  " (token provider is not (yet?) available)"
              }
              return null
            }
            newActiveState(
              invocationId,
              oldState.provider,
              oldState.tokenListener,
              oldState.forceTokenRefresh
            )
          }
          is State.Active -> {
            if (
              oldState.job.isCompleted &&
                !oldState.job.isCancelled &&
                oldState.job.await().sequenceNumber < attemptSequenceNumber
            ) {
              newActiveState(
                invocationId,
                oldState.provider,
                oldState.tokenListener,
                forceRefresh = false
              )
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
          "$invocationId getAccessToken() starts a new coroutine to get the token" +
            " (oldState=${oldState::class.simpleName})"
        }
      }

      val jobResult = newState.job.runCatching { await() }

      // Ensure that any exception checking below is due to an exception that happened in the
      // coroutine that called getAccessToken(), not from the calling coroutine being cancelled.
      coroutineContext.ensureActive()

      val sequencedResult = jobResult.getOrNull()
      if (sequencedResult !== null && sequencedResult.sequenceNumber < attemptSequenceNumber) {
        logger.debug { "$invocationId getAccessToken() got an old result; retrying" }
        continue
      }

      val exception = jobResult.exceptionOrNull() ?: jobResult.getOrNull()?.ref?.exceptionOrNull()
      if (exception !== null) {
        val retryException = exception.getRetryIndicator()
        if (retryException !== null) {
          logger.debug {
            "$invocationId getAccessToken() retrying due to ${retryException.message}"
          }
          continue
        } else if (exception is FirebaseNoSignedInUserException) {
          logger.debug {
            "$invocationId getAccessToken() returns null" +
              " (FirebaseAuth reports no signed-in user)"
          }
          return null
        } else if (exception is CancellationException) {
          logger.warn(exception) {
            "$invocationId getAccessToken() throws GetAccessTokenCancelledException," +
              " likely due to DataConnectCredentialsTokenManager.close() being called"
          }
          throw GetAccessTokenCancelledException(exception)
        } else {
          logger.warn(exception) {
            "$invocationId getAccessToken() failed unexpectedly: $exception"
          }
          throw exception
        }
      }

      val accessToken = sequencedResult!!.ref.getOrThrow().token
      logger.debug {
        "$invocationId getAccessToken() returns retrieved token: " +
          accessToken?.toScrubbedAccessToken()
      }
      return accessToken
    }
  }

  private sealed class GetAccessTokenRetry(message: String) : Exception(message)
  private class ForceRefresh(message: String) : GetAccessTokenRetry(message)
  private class NewProvider(message: String) : GetAccessTokenRetry(message)

  @DeferredApi
  private fun onProviderAvailable(newProvider: T, tokenListener: L) {
    logger.debug { "onProviderAvailable(newProvider=$newProvider)" }
    runIgnoringFirebaseAppDeleted { addTokenListener(newProvider, tokenListener) }

    while (true) {
      val oldState = state.get()
      val newState =
        when (oldState) {
          is State.Uninitialized ->
            throw IllegalStateException(
              "INTERNAL ERROR: onProviderAvailable() called before initialize()"
            )
          is State.Closed -> {
            logger.debug {
              "onProviderAvailable(newProvider=$newProvider)" +
                " unregistering token listener that was just added"
            }
            runIgnoringFirebaseAppDeleted { removeTokenListener(newProvider, tokenListener) }
            break
          }
          is State.Ready ->
            State.Ready(newProvider, oldState.tokenListener, oldState.forceTokenRefresh)
          is State.Active -> {
            val newProviderClassName = newProvider::class.qualifiedName
            val message = "a new provider $newProviderClassName is available (symhxtmazy)"
            oldState.job.cancel(message, NewProvider(message))
            State.Ready(newProvider, tokenListener, forceTokenRefresh = false)
          }
        }

      if (state.compareAndSet(oldState, newState)) {
        break
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
  private class DeferredProviderHandlerImpl<T : Any, L : Any>(
    private val weakCredentialsTokenManagerRef:
      WeakReference<DataConnectCredentialsTokenManager<T, L>>,
    private val tokenListener: L,
  ) : DeferredHandler<T> {
    override fun handle(provider: Provider<T>) {
      weakCredentialsTokenManagerRef.get()?.onProviderAvailable(provider.get(), tokenListener)
    }
  }

  private class CredentialsTokenManagerClosedException(
    tokenProvider: DataConnectCredentialsTokenManager<*, *>
  ) :
    DataConnectException(
      "DataConnectCredentialsTokenManager ${tokenProvider.instanceId} was closed"
    )

  private class GetAccessTokenCancelledException(cause: Throwable) :
    DataConnectException("getAccessToken() was cancelled, likely by close()", cause)

  // Work around a race condition where addIdTokenListener() and removeIdTokenListener() throw if
  // the FirebaseApp is deleted during or before its invocation.
  private fun runIgnoringFirebaseAppDeleted(block: () -> Unit) {
    try {
      block()
    } catch (e: IllegalStateException) {
      if (e.message == "FirebaseApp was deleted") {
        logger.warn(e) { "ignoring exception: $e" }
      } else {
        throw e
      }
    }
  }

  private companion object {
    fun Throwable.getRetryIndicator(): GetAccessTokenRetry? {
      var currentCause: Throwable? = this
      while (true) {
        if (currentCause === null) {
          return null
        } else if (currentCause is GetAccessTokenRetry) {
          return currentCause
        }
        currentCause = currentCause.cause ?: return null
      }
    }
  }
}

/**
 * Returns a new string that is equal to this string but only includes a chunk from the beginning
 * and the end.
 *
 * This method assumes that the contents of this string are an access token. The returned string
 * will have enough information to reason about the access token in logs without giving its value
 * away.
 */
internal fun String.toScrubbedAccessToken(): String =
  if (length < 30) {
    "<redacted>"
  } else {
    buildString {
      append(this@toScrubbedAccessToken, 0, 6)
      append("<redacted>")
      append(
        this@toScrubbedAccessToken,
        this@toScrubbedAccessToken.length - 6,
        this@toScrubbedAccessToken.length
      )
    }
  }
