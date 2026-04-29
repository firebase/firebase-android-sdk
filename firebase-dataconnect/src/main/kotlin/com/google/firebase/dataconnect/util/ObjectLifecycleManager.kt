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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext

// ObjectLifecycleManager employs sophisticated Kotlin Coroutines patterns to manage the lifecycle
// of an "OpenedResource". It provides a robust guarantee: once the resource is registered, it
// will eventually be closed, regardless of the timing or interleaving of concurrent open() and
// close() calls.
//
// Concurrency Techniques Analysis
//
// 1. Thread-Safe State Transitions: The lifecycle (Unopened, Opened, Closing, Closed) is managed
//    using a MutableStateFlow. State transitions are performed atomically using compareAndSet,
//    ensuring consistency even under heavy contention.
//
// 2. Lazy openJob (CoroutineStart.LAZY): The resource initialization logic (the `open` lambda) is
//    encapsulated in an `openJob`. By starting this job lazily, we ensure that initialization
//    only occurs when open() is actually called, preventing unnecessary work if the manager is
//    closed before it is ever used.
//
// 3. Immediate Cleanup Registration (CoroutineStart.UNDISPATCHED): The `closeJob` is launched
//    immediately during object initialization using UNDISPATCHED. This ensures its `try-finally`
//    block is active from the very beginning. The job suspends on awaitCancellation(), waiting for
//    the manager's coroutineScope to be canceled (which happens in the close() method).
//
// 4. Non-Interruptible Cleanup (NonCancellable): The actual closing logic is wrapped in
//    withContext(NonCancellable). This guarantees that once the cleanup process starts (triggered
//    by cancellation), it will run to completion even if the calling context is itself canceled.
//
// 5. Atomic Hand-off with LaterValue: The `open` lambda is required to "hand off" the resource to
//    the manager by calling LaterValue.set(). This container ensures that the resource is only
//    set once and provides thread-safe visibility between the `openJob` and the `closeJob`.
//
// Execution Flow and Race Condition Handling
//
// * Concurrent open() calls: Multiple callers will all await() the same `openJob`. Once the first
//   one succeeds and updates the state to Opened, all callers receive the same resource.
//
// * Concurrent close() calls: The first caller moves the state to Closing and cancels the scope.
//   Subsequent callers see the Closing/Closed state and wait for the same cleanup to finish.
//
// * open() vs. close() Interleaving:
//   - If close() is called before open(), the `openJob` is canceled before it ever starts (or
//     immediately if it was just starting).
//   - If close() is called while open() is in progress, the scope cancellation propagates to the
//     `openJob`. Crucially, the `closeJob` joins the `openJob` inside its NonCancellable block
//     before calling the `close` lambda. This sequence ensures that the `open` lambda has
//     fully terminated (either successfully or via cancellation) before the `close` lambda
//     is invoked on the resource, preventing "closing while opening" race conditions.
//
// Guarantees
//
// The manager assumes ownership of the resource the moment LaterValue.set() is called. If the
// `open` lambda fails or is canceled *after* calling set(), the `closeJob`'s finally block will
// detect the registered resource and invoke the `close` lambda, preventing leaks. If cancellation
// occurs *before* set(), the manager does not yet own the resource, and it is the responsibility
// of the `open` lambda's implementation to perform any necessary local cleanup.

/**
 * A thread-safe manager for objects with a lifecycle consisting of an asynchronous "open" phase and
 * a guaranteed "close" phase.
 *
 * It ensures that the "resource" is opened at most once (even if multiple concurrent [open] calls
 * occur), its "resource" is shared among all callers, and the "resource", if registered by [open],
 * is unconditionally closed when [close] is called.
 *
 * Call [ObjectLifecycleManager.open] each time a reference to the "resource" is desired; it will
 * either open it and return it, return the previously-opened "resource", or throw an exception
 * because [close] has been called.
 *
 * [ObjectLifecycleManager] uses atomics rather than mutexes for synchronization. This has major
 * benefits, including superior scalability, lower overhead, and immunity to concurrency hazards
 * such as deadlock, livelock, and priority inversion.
 *
 * @param OpenedResource The type of the resource created by [open] and closed by [close].
 * @param open The function to call to open the resource. This function is called at most once and
 * is called in response to an invocation of [ObjectLifecycleManager.open]. The function **MUST**
 * call [LaterValue.set] on its argument as soon as the resource is allocated, especially before any
 * suspension points are reached. Otherwise, there is a possibility that the resource will leak and
 * never be closed. Once [LaterValue.set] is called, the calling [ObjectLifecycleManager] assumes
 * ownership of the object and guarantees that it will eventually be closed in response to
 * [ObjectLifecycleManager.close] by calling the given [close] function with it.
 * @param close The function to call to close the resource. This function is called at most once in
 * response to an invocation of [ObjectLifecycleManager.close]. As long as [open] abides by the
 * requirement to call [LaterValue.set] on its argument before reaching any suspension points, the
 * [ObjectLifecycleManager] guarantees that [close] will be called with that object. Note that
 * [close] is called within a [NonCancellable] context from a canceled coroutine.
 * @param coroutineDispatcher The dispatcher to use for the internal background jobs that manage the
 * resource's lifecycle.
 * @param logger A logger to use.
 */
internal class ObjectLifecycleManager<OpenedResource>(
  open: suspend (LaterValue<OpenedResource>) -> Unit,
  close: suspend (OpenedResource) -> Unit,
  coroutineDispatcher: CoroutineDispatcher,
  private val logger: Logger,
) {

  private val state =
    MutableStateFlow<State<OpenedResource>>(
      createUnopenedState(open, close, coroutineDispatcher, logger)
    )

  /**
   * Opens the resource and returns it, or returns the previously-opened resource.
   *
   * If the resource is already open, the existing resource is returned. If it's currently opening,
   * this method suspends until the internal `openJob` completes (which runs the `open` function
   * given to the constructor and verifies resource registration). Otherwise, the invocation of the
   * `open` function given to the constructor is triggered to open the resource, and this method
   * suspends until the `openJob` completes.
   *
   * If the `open` function given to the constructor throws an exception, then that exception will
   * be re-thrown from this method, and from all future invocations of this method (until [close] is
   * called, at which point this method throws [IllegalStateException]). Notably, if [close] is
   * called concurrently then this method is likely to throw [CancellationException] as a result of
   * the cancellation of the coroutine that calls the `open` function given to the constructor.
   *
   * @throws IllegalStateException If [close] has been called.
   */
  suspend fun open(): OpenedResource {
    while (true) {
      when (val currentState = state.value) {
        is State.Unopened -> {
          val openedResource = currentState.openJob.await()
          val newState = State.Opened(currentState, openedResource)
          state.compareAndSet(currentState, newState)
        }
        is State.Opened -> return currentState.openedResource
        is State.Closing,
        State.Closed -> error("${logger.nameWithId} close() has been called")
      }
    }
  }

  /**
   * Closes the resource and releases any associated assets.
   *
   * This operation is idempotent. It guarantees that the resource is closed at most once, even if
   * called multiple times or concurrently with [open]. Once this method is called, any subsequent
   * calls to [open] will throw [IllegalStateException]. Calling [close] after the closing operation
   * is complete is safe and will return immediately.
   *
   * This method suspends until the cleanup process (including the `open` and `close` functions
   * given to the constructor) is fully complete.
   */
  suspend fun close() {
    logger.debug { "close()" }

    while (true) {
      val currentState = state.value

      val newState =
        when (currentState) {
          is State.Unopened -> State.Closing(currentState)
          is State.Opened -> State.Closing(currentState)
          is State.Closing -> {
            currentState.coroutineScope.cancel("${logger.nameWithId} close()")
            currentState.closeJob.await()
            currentState.coroutineScope.coroutineContext.job.join()
            State.Closed
          }
          State.Closed -> return
        }

      state.compareAndSet(currentState, newState)
    }
  }

  private sealed interface State<out OpenedResource> {

    class Unopened<OpenedResource>(
      val coroutineScope: CoroutineScope,
      val openJob: Deferred<OpenedResource>,
      val closeJob: Deferred<Unit>,
    ) : State<OpenedResource>

    class Opened<OpenedResource>(
      val coroutineScope: CoroutineScope,
      val closeJob: Deferred<Unit>,
      val openedResource: OpenedResource,
    ) : State<OpenedResource> {
      constructor(
        state: Unopened<*>,
        openedResource: OpenedResource
      ) : this(state.coroutineScope, state.closeJob, openedResource)
    }

    class Closing(
      val coroutineScope: CoroutineScope,
      val closeJob: Deferred<Unit>,
    ) : State<Nothing> {
      constructor(state: Unopened<*>) : this(state.coroutineScope, state.closeJob)
      constructor(state: Opened<*>) : this(state.coroutineScope, state.closeJob)
    }

    data object Closed : State<Nothing>
  }

  private companion object {

    fun <OpenedResource> createUnopenedState(
      open: suspend (LaterValue<OpenedResource>) -> Unit,
      close: suspend (OpenedResource) -> Unit,
      coroutineDispatcher: CoroutineDispatcher,
      logger: Logger,
    ): State.Unopened<OpenedResource> {
      val coroutineScope = createSupervisorCoroutineScope(coroutineDispatcher, logger)
      val openedResource = LaterValue<OpenedResource>()

      val openJobRef =
        coroutineScope.launchOpenJob(
          coroutineName = "${logger.nameWithId} open job",
          start = CoroutineStart.LAZY,
          openRef = ClearableValue(open),
          openedResource = openedResource,
        )

      val closeJob =
        coroutineScope.launchCloseJob(
          coroutineName = "${logger.nameWithId} close job",
          start = CoroutineStart.UNDISPATCHED,
          closeRef = ClearableValue(close),
          openJobRef = openJobRef,
          openedResource = openedResource,
        )

      return State.Unopened(coroutineScope, openJobRef.getOrThrow(), closeJob)
    }

    fun <OpenedResource> CoroutineScope.launchOpenJob(
      coroutineName: String,
      start: CoroutineStart,
      openRef: ClearableValue<suspend (LaterValue<OpenedResource>) -> Unit>,
      openedResource: LaterValue<OpenedResource>,
    ): ClearableValue<Deferred<OpenedResource>> {
      val job =
        async(CoroutineName(coroutineName), start = start) {
          val open =
            openRef.clearOrElse {
              error("internal error xvjmyh9qk9: openRef has already been cleared")
            }

          open(openedResource)

          openedResource.getOrElse {
            error("open() failed to call LaterValue.set() on its argument [exr2qhcppc]")
          }
        }

      val jobRef = ClearableValue(job)

      job.invokeOnCompletion {
        openRef.clear()
        jobRef.clear()
      }

      return jobRef
    }

    fun <OpenedResource> CoroutineScope.launchCloseJob(
      coroutineName: String,
      start: CoroutineStart,
      closeRef: ClearableValue<suspend (OpenedResource) -> Unit>,
      openJobRef: ClearableValue<Deferred<OpenedResource>>,
      openedResource: LaterValue<OpenedResource>,
    ): Deferred<Unit> =
      async(CoroutineName(coroutineName), start = start) {
        val cancellationException = LaterValue<CancellationException>()
        try {
          awaitCancellation()
        } catch (e: CancellationException) {
          cancellationException.set(e)
        } finally {
          withContext(NonCancellable) {
            // Cleanup Step 1: Wait for the "open job" to complete; this ensures that if the "open
            // job" registers a resource then we will get it in order to close it in step 2.
            openJobRef.ifNotCleared { openJob ->
              openJob.cancel(cancellationException.getOrNull())
              openJob.join()
            }

            // Cleanup Step 2: Close the OpenedResource, if it was registered by the "open job".
            val close =
              closeRef.clearOrElse {
                error("internal error mfdyp7j973: closeRef has already been cleared")
              }
            openedResource.ifSet { close(it) }
          }
        }
      }
  }
}
