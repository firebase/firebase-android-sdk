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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

// ObjectLifecycleManager employs sophisticated Kotlin Coroutines patterns to manage the lifecycle
// of a "Resource". It provides a robust guarantee: once the resource is registered, it will
// eventually be closed, regardless of the timing or interleaving of concurrent open() and
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
 * occur) by calling [openResource], its "resource" is shared among all callers of [open], and the
 * resource is closed when [close] is called.
 *
 * Call [open] each time a reference to the "resource" is desired; it will either open it and return
 * it, return the previously-opened resource, or throw an exception because [close] has been called.
 * Alternately, call [poll] to return the "resource" if it happens to be available, or `null`
 * otherwise.
 *
 * [ObjectLifecycleManager] uses atomics rather than mutexes for synchronization. This has major
 * benefits, including superior scalability, lower overhead, and immunity to concurrency hazards
 * such as deadlock, livelock, and priority inversion.
 *
 * @param Resource The type of the resource created by [openResource].
 * @param openResource The function to call to open the resource. This function is called at most
 * once and is called in response to an invocation of [open] on a thread from [coroutineDispatcher].
 * When [openResource] returns, the [ObjectLifecycleManager] assumes ownership of the returned
 * resource and guarantees that the returned [OpenResult.close] method will be called to close it in
 * response to an invocation of [close].
 * @param coroutineDispatcher The dispatcher to use for the internal background jobs that manage the
 * resource's lifecycle and for calling [openResource] and [OpenResult.close].
 * @param logger A logger to use.
 */
internal class ObjectLifecycleManager<Resource>(
  coroutineDispatcher: CoroutineDispatcher,
  private val logger: Logger,
  openResource: OpenContext.() -> OpenResult<Resource>,
) {

  private val state =
    MutableStateFlow<State<Resource>>(
      run {
        val coroutineScope = createSupervisorCoroutineScope(
          coroutineDispatcher,
          logger,
          coroutineName = logger.nameWithId,
        )
        val resourceRef = LaterValue<Resource>()
        val closeResourceRef = LaterValue<suspend () -> Unit>()

        val openJobRef =
          coroutineScope.launchOpenJob(
            coroutineName = "${logger.nameWithId} open job",
            start = CoroutineStart.LAZY,
            openResourceRef = ClearableValue(openResource),
            resourceRef=resourceRef,
            closeResourceRef=closeResourceRef,
            backgroundScope = createSupervisorCoroutineScope(
              coroutineDispatcher,
              logger,
              parent = coroutineScope.coroutineContext.job,
              coroutineName = "${logger.nameWithId} background",
            ),
          )

        val closeJob =
          coroutineScope.launchCloseJob(
            coroutineName = "${logger.nameWithId} close job",
            start = CoroutineStart.UNDISPATCHED,
            openJobRef = openJobRef,
            closeResourceRef = closeResourceRef,
          )

        State.Unopened(coroutineScope, openJobRef.getOrThrow(), closeJob)
      }
    )

  /**
   * Opens the resource and returns it, or returns the previously-opened resource.
   *
   * If the resource is already open, the existing resource is returned. If it's currently opening,
   * this method suspends until the internal "open job" completes. Otherwise, the "open job" is
   * started, which calls the `openResource` function given to the constructor.
   *
   * The "open job" is considered to be "completed" once the `openResource` function with which this
   * object was initialized returns, and any coroutines it launches on the given [CoroutineScope]
   * complete.
   *
   * If the `open` function given to the constructor throws an exception, then that exception will
   * be re-thrown from this method, and from all future invocations of this method (until [close] is
   * called, at which point this method throws [IllegalStateException]). Notably, if [close] is
   * called concurrently then this method is likely to throw [CancellationException] as a result of
   * the cancellation of the coroutine that calls the `open` function given to the constructor.
   *
   * @throws IllegalStateException If [close] has been called.
   */
  suspend fun open(): Resource {
    while (true) {
      when (val currentState = state.value) {
        is State.Unopened -> {
          val openResult = currentState.openJob.await()
          val newState = State.Opened(currentState, openResult.resource)
          state.compareAndSet(currentState, newState)
        }
        is State.Opened -> return currentState.openedResource
        is State.Closing,
        State.Closed -> error("${logger.nameWithId} close() has been called")
      }
    }
  }

  /**
   * Immediately returns either the resource managed by this object, if it has been opened and not yet closed, or `null` otherwise.
   *
   * The only case where this function returns a value other than `null` is when a previous invocation
   * of [open] completed successfully _and_ there has not been a call to [close].
   * If [open] has never been called then this function returns `null`.
   * If [open] has been called but the "open job" is not yet complete then this function returns `null`.
   * If [close] has been called then this function returns `null`.
   *
   * This function neither blocks nor suspends: it effectively returns "immediately".
   *
   * @return Returns the resource managed by this object, if opened and not yet closed.
   */
  fun poll(): Resource? = when (val currentState = state.value) {
    is State.Unopened -> null
    is State.Opened -> currentState.openedResource
    is State.Closing, State.Closed -> null
  }

  /**
   * Closes the resource and releases any associated assets.
   *
   * This operation is idempotent. It guarantees that the resource is closed at most once, even if
   * called multiple times or concurrently with [open]. Once this method is called, any subsequent
   * calls to [open] will throw [IllegalStateException]. Calling [close] after the closing operation
   * is complete is safe and will return immediately.
   *
   * This method suspends until the "open job" and "cleanup job" (including the `openResource` and
   * `closeResource` functions given to the constructor) are fully complete.
   */
  suspend fun close() {
    logger.debug { "close()" }

    while (true) {
      val currentState = state.value

      val newState = when (currentState) {
        is State.Unopened -> State.Closing(currentState)
        is State.Opened -> State.Closing(currentState)
        is State.Closing ->
          currentState.coroutineScope.run {
            cancel("${logger.nameWithId} close()")
            currentState.closeJob.await()
            coroutineContext.job.join()
          State.Closed
        }
        State.Closed -> return
      }

      state.compareAndSet(currentState, newState)
    }
  }

  /**
   * Starts this object's closing process and optionally blocks (not suspends, blocks!) until the
   * closing process has completed.
   *
   * This method is provided to allow closing this object from a normal, non-suspending context.
   * It ultimately has the exact same result as the suspending version of [close], except that the
   * caller has the option to block the calling thread until the close operation is complete, or
   * simply run the closing process in the background.
   *
   * @return a [Deferred] that completes when the closing process completes.
   * If [SuspendingCloseHandlingStrategy.Block] is specified then it will unconditionally be completed
   * successfully. Otherwise, If [SuspendingCloseHandlingStrategy.Async] is specified,
   * then it will complete when the closing process has completed, completing exceptionally if any
   * errors occurred during the closing process.
   */
  fun close(suspendHandlingStrategy: SuspendingCloseHandlingStrategy): Deferred<*> {
    val coroutineScope = when (val currentState = state.value) {
      is State.Unopened -> currentState.coroutineScope
      is State.Opened -> currentState.coroutineScope
      is State.Closing -> currentState.coroutineScope
      State.Closed -> return CompletableDeferred(Unit)
    }
    return suspendHandlingStrategy.handle(coroutineScope) { close() }
  }

  private sealed interface State<out Resource> {

    class Unopened<Resource>(
      val coroutineScope: CoroutineScope,
      val openJob: Deferred<OpenResult<Resource>>,
      val closeJob: Deferred<Unit>,
    ) : State<Resource>

    class Opened<Resource>(
      val coroutineScope: CoroutineScope,
      val closeJob: Deferred<Unit>,
      val openedResource: Resource,
    ) : State<Resource> {
      constructor(
        state: Unopened<*>,
        openedResource: Resource
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

  interface OpenContext {

    /**
     * A [CoroutineScope] whose lifetime matches that of the "open job".
     *
     * Any jobs launched in this scope will cause calls to [ObjectLifecycleManager.open] to suspend
     * until all such jobs are completed. A failed job will result in the opening process failing,
     * and the exception will be re-thrown from [ObjectLifecycleManager.open].
     *
     * This scope is intended to be used for work that is part of the "opening" process, and before
     * whose completion the "resource" is not usable. For example, the `openResource` function may
     * create a `Database` object and launch a coroutine on this scope to call its `initialize()`
     * function.
     *
     * This scope is canceled by a call to [ObjectLifecycleManager.close] of the owner.
     */
    val openScope: CoroutineScope

    /**
     * A [CoroutineScope] whose lifetime matches that of the owner [ObjectLifecycleManager].
     *
     * Any jobs launched in this scope will run _concurrently_ with the "open job" and may continue
     * to run after the "open job" has completed. The [kotlinx.coroutines.Job] of this scope is a
     * [kotlinx.coroutines.SupervisorJob] and, therefore, failure of a coroutine is isolated to that
     * coroutine, and other coroutines continue to run unaffected.
     *
     * This scope is intended to be used for long-running coroutines that are intended to exist for
     * the lifetime of the [ObjectLifecycleManager], such as a background cleanup loop.
     *
     * This scope is canceled by a call to [ObjectLifecycleManager.close] of the owner.
     */
    val backgroundScope: CoroutineScope
  }

  class OpenResult<Resource>(
    val resource: Resource,
    val close: suspend () -> Unit = {},
  )

  private class OpenContextImpl(override val openScope: CoroutineScope, override val backgroundScope: CoroutineScope,) : OpenContext

  private companion object {

    fun <Resource> CoroutineScope.launchOpenJob(
      coroutineName: String,
      start: CoroutineStart,
      openResourceRef: ClearableValue<OpenContext.() -> OpenResult<Resource>>,
      resourceRef: LaterValue<Resource>,
      closeResourceRef: LaterValue<suspend () -> Unit>,
      backgroundScope: CoroutineScope,
    ): ClearableValue<Deferred<OpenResult<Resource>>> {
      val job =
        async(CoroutineName(coroutineName), start = start) {
          val open =
            openResourceRef.clearOrElse {
              error("internal error xvjmyh9qk9: openRef has already been cleared")
            }
          val openContext = OpenContextImpl(
            openScope = this@launchOpenJob,
            backgroundScope=backgroundScope,
          )
          open(openContext).apply {
            resourceRef.set(resource)
            closeResourceRef.set(close)
          }
        }

      val jobRef = ClearableValue(job)

      job.invokeOnCompletion {
        openResourceRef.clear()
        jobRef.clear()
      }

      return jobRef
    }

    fun CoroutineScope.launchCloseJob(
      coroutineName: String,
      start: CoroutineStart,
      openJobRef: ClearableValue<Job>,
      closeResourceRef: LaterValue<suspend () -> Unit>,
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

            // Cleanup Step 2: Close the Resource, if it was registered by the "open job".
            closeResourceRef.ifSet { it() }
          }
        }
      }
  }
}
