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

package com.google.firebase.dataconnect.util

import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.debug
import com.google.firebase.dataconnect.util.CoroutineUtils.createSupervisorCoroutineScope
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext

/**
 * A thread-safe, lock-free/non-blocking lifecycle manager that handles on-demand lazy
 * initialization and safe teardown of a resource of type [T].
 *
 * Subclasses must override [create] and may optionally override [initialize] and [destroy] to
 * manage the specific instantiation, setup, and cleanup of the resource.
 *
 * @param T The type of the managed resource/service instance.
 * @param coroutineDispatcher The dispatcher used by the coroutines that call [create], [initialize]
 * , and [destroy].
 * @param logger The logger to use.
 */
internal abstract class ObjectLifecycleManager<T : Any>(
  coroutineDispatcher: CoroutineDispatcher,
  logger: Logger,
) {

  private val state: AtomicReference<State<T>>

  init {
    val coroutineScope =
      createSupervisorCoroutineScope(
        coroutineDispatcher,
        logger,
        coroutineName = logger.nameWithId,
      )

    val instanceRef = AtomicReference<T>()

    val openJob =
      coroutineScope.async(
        CoroutineName("${logger.nameWithId}-open"),
        start = CoroutineStart.LAZY,
      ) {
        logger.debug { "opening" }
        val instance = runInterruptible { create().also { instanceRef.set(it) } }
        initialize(instance)
        instance
      }

    val openJobRef = AtomicReference(openJob)
    openJob.invokeOnCompletion { openJobRef.set(null) }
    val destroyResultRef = AtomicReference<Result<Unit>?>(null)

    coroutineScope.launch(
      CoroutineName("${logger.nameWithId}-close"),
      start = CoroutineStart.UNDISPATCHED,
    ) {
      var cancellationException: CancellationException? = null
      try {
        awaitCancellation()
      } catch (e: CancellationException) {
        cancellationException = e
      } finally {
        logger.debug { "closing" }
        withContext(NonCancellable) {
          openJobRef.get()?.let { openJob ->
            openJob.cancel(cancellationException)
            openJob.join()
          }

          instanceRef.get()?.let { instance ->
            val destroyResult = runCatching { destroy(instance) }
            destroyResultRef.set(destroyResult)
          }
        }
      }
    }

    state = AtomicReference(State.Unopened(coroutineScope, openJob, destroyResultRef))
  }

  /**
   * Exception thrown by [open] when an attempt is made to open the manager after [close] has been
   * called.
   */
  class ClosedException(message: String) : Exception(message)

  /**
   * Opens the managed resource instance of type [T], initiating lazy creation and initialization if
   * this is the first call.
   *
   * The first call of this method will call [create] followed by [initialize]. Subsequent calls
   * will return the value returned from [create] and initialized by [initialize]. If [close] is
   * called concurrently then the coroutine that calls these methods will be canceled and this
   * method will throw [ClosedException].
   *
   * @return The fully created and initialized resource instance of type [T].
   * @throws ClosedException If [close] has been called or is called concurrently.
   */
  suspend fun open(): T {
    while (true) {
      val currentState = state.get()

      val newState: State.Opened<T> =
        when (currentState) {
          is State.Unopened -> {
            val openResult = currentState.openJob.runCatching { await() }
            currentCoroutineContext().ensureActive()
            State.Opened(currentState, openResult)
          }
          is State.Opened -> return currentState.openResult.getOrThrow()
          is State.Closing,
          is State.Closed -> throw ClosedException("close() has been called [cm2ga6aaej]")
        }

      state.compareAndSet(currentState, newState)
    }
  }

  /**
   * Closes the lifecycle manager and initiates the safe teardown of the managed resource.
   *
   * If [open] is in progress then it is first canceled and joined. Then, if [create] successfully
   * created the resource instance, [destroy] is called to clean it up.
   *
   * @throws Throwable The exception thrown by [destroy] if resource teardown fails, consistently
   * rethrown to all callers of [close].
   */
  suspend fun close() {
    while (true) {
      val currentState = state.get()

      val newState =
        when (currentState) {
          is State.Unopened -> State.Closing(currentState)
          is State.Opened -> State.Closing(currentState)
          is State.Closing -> {
            currentState.coroutineScope.cancel("close() called [ebe7bqm84m]")
            currentState.coroutineScope.coroutineContext.job.join()
            State.Closed(currentState)
          }
          is State.Closed -> {
            currentState.destroyResult?.getOrThrow()
            return
          }
        }

      state.compareAndSet(currentState, newState)
    }
  }

  /**
   * Factory method to create a new instance of the managed resource of type [T].
   *
   * This method is called exactly once when the resource is first opened. It is invoked inside the
   * background coroutine scope but runs synchronously (without suspending).
   *
   * @return The created instance of [T].
   */
  protected abstract fun create(): T

  /**
   * Suspending initialization hook executed immediately after the resource instance is successfully
   * created via [create], and before the instance is returned to any [open] callers.
   *
   * @param instance The newly created resource instance.
   */
  protected abstract suspend fun initialize(instance: T)

  /**
   * Suspending cleanup and teardown hook executed when the resource is closed.
   *
   * This method runs in a [NonCancellable] context within the cleanup coroutine job to ensure that
   * all teardown actions complete even if the calling or parent scopes are cancelled. Any failure
   * inside this method will be caught, saved, and propagated to the callers of [close].
   *
   * @param instance The active resource instance to be destroyed.
   */
  protected abstract suspend fun destroy(instance: T)

  private sealed interface State<out T> {

    class Unopened<T>(
      val coroutineScope: CoroutineScope,
      val openJob: Deferred<T>,
      val destroyResultRef: AtomicReference<Result<Unit>?>,
    ) : State<T> {
      override fun toString() = "Unopened"
    }

    class Opened<T>(
      val coroutineScope: CoroutineScope,
      val openResult: Result<T>,
      val destroyResultRef: AtomicReference<Result<Unit>?>,
    ) : State<T> {
      constructor(
        unopenedState: Unopened<T>,
        openResult: Result<T>,
      ) : this(
        unopenedState.coroutineScope,
        openResult,
        unopenedState.destroyResultRef,
      )

      override fun toString() = "Opened"
    }

    class Closing(
      val coroutineScope: CoroutineScope,
      val destroyResultRef: AtomicReference<Result<Unit>?>,
    ) : State<Nothing> {

      constructor(
        unopenedState: Unopened<*>,
      ) : this(unopenedState.coroutineScope, unopenedState.destroyResultRef)

      constructor(
        openedState: Opened<*>,
      ) : this(openedState.coroutineScope, openedState.destroyResultRef)

      override fun toString() = "Closing"
    }

    class Closed(val destroyResult: Result<Unit>?) : State<Nothing> {
      constructor(
        closingState: Closing,
      ) : this(closingState.destroyResultRef.get())

      override fun toString() = "Closed(destroyResult=$destroyResult)"
    }
  }
}
