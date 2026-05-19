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

package com.google.firebase.dataconnect.util.coroutines

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED

/**
 * A coroutine-based signaling mechanism that allows a waiter to suspend until notified by a sender,
 * conflating multiple signals into one signal.
 *
 * This class is designed to coordinate execution flow between asynchronous operations (such as a
 * producer and a consumer). A consumer call to [await] will suspend if no signal is available, and
 * will resume immediately once a producer calls [signal].
 *
 * **Intended Use & Example Workflow:**
 * 1. A background worker (sender) performs tasks in a loop, and calls [signal] after each task is
 * complete.
 * 2. A consumer (waiter) calls [await] to block/suspend its execution until the worker signals that
 * some work is done.
 *
 * ### Key Behavioral Characteristics
 *
 * * **Single Signal Delivery:** If multiple coroutines are suspended in [await] then exactly _one_
 * of them will be resumed upon [signal] being called. This is *not* a broadcast event or a
 * `signalAll` style primitive.
 * * **Signal Conflation:** The signal is conflated and persistent until consumed. If [signal] is
 * called multiple times before any coroutine awaits, only a single signal permit is retained. The
 * first subsequent call to [await] will consume the signal and resume immediately, while any
 * further calls to [await] will suspend.
 *
 * ### Prompt cancellation guarantee
 *
 * All suspending functions in this class provide **prompt cancellation guarantee**. If the job was
 * canceled while [await] was suspended, it will not resume successfully, even if it already
 * received a signal, but throws a [CancellationException]. See the documentation regarding "Prompt
 * cancellation guarantee" in [Channel] for further details.
 *
 * ### Safe for concurrent use
 *
 * All methods and properties of [ConflatedSignal] are thread-safe and may be safely called and/or
 * accessed concurrently from multiple threads and/or coroutines.
 */
internal class ConflatedSignal {

  private val channel = Channel<Unit>(CONFLATED)

  /**
   * Emits a signal to resume a suspended waiter, if one is present, or buffers the signal for the
   * next waiter if none are currently awaiting.
   *
   * Because this signal is conflated, multiple sequential calls to [signal] without intervening
   * calls to [await] will be merged into a single signal. Only one [await] call will be resumed
   * immediately, regardless of how many times [signal] was called.
   *
   * This method is non-blocking, thread-safe, and safe to call from any context (including outside
   * of coroutines).
   */
  fun signal() {
    channel.trySend(Unit)
  }

  /**
   * Suspends the calling coroutine until a signal is received via [signal].
   *
   * If a signal has already been emitted and not yet consumed, this method returns immediately,
   * consuming that signal.
   *
   * ### Prompt cancellation guarantee
   *
   * This function provides **prompt cancellation guarantee**. That is, if the job is canceled while
   * [await] is suspended, it will not resume successfully, even if it consumed a signal, but throws
   * a [CancellationException].
   *
   * ### Concurrent Waiters
   *
   * If multiple coroutines are concurrently suspended in [await], only one of them, chosen in an
   * indeterminate manner, will be resumed when [signal] is called.
   */
  suspend fun await() {
    channel.receive()
  }
}
