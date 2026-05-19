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

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * A coroutine-based signaling mechanism that allows a waiter to suspend until notified by a sender,
 * conflating multiple signals into one signal.
 *
 * This class is designed to coordinate execution flow between asynchronous operations (such as a
 * producer and a consumer). A consumer can wait for a signal either by calling the suspending
 * method [await] or by collecting from the [signals] flow. In both cases, the consumer will resume
 * immediately once a producer calls [signal].
 *
 * ### Key Behavioral Characteristics
 *
 * * **Single Signal Delivery / Competing Consumers:** If multiple coroutines are waiting for a
 * signal, whether they are suspended in [await] or collecting from the [signals] flow, then exactly
 * _one_ of them will be resumed/notified upon [signal] being called, chosen indeterminately. This
 * is *not* a broadcast event or a `signalAll` style primitive. Waiters and flow collectors
 * **compete** for signals.
 * * **Signal Conflation:** The signal is conflated and persistent until consumed. If [signal] is
 * called multiple times before any coroutine awaits, only a single signal is retained. The first
 * subsequent waiter (either via [await] or [signals] collection) will consume the signal and resume
 * immediately, while any further waiters will suspend.
 *
 * ### Prompt cancellation guarantee
 *
 * All suspending functions and flows in this class provide a **prompt cancellation guarantee**. If
 * the job was canceled while suspended in [await] (or while collecting from [signals]), it will not
 * resume successfully, even if it already received a signal, but will throw [CancellationException]
 * . See the documentation regarding "Prompt cancellation guarantee" in [Channel] for further
 * details.
 *
 * ### Safe for concurrent use
 *
 * All methods and properties of [ConflatedSignal] are thread-safe and may be safely called and/or
 * accessed concurrently from multiple threads and/or coroutines.
 */
internal class ConflatedSignal {

  private val signalState = AtomicBoolean(false)
  private val channel = Channel<Unit>(CONFLATED)

  /**
   * A cold [Flow] that emits [Unit] every time a signal is received.
   *
   * ### Important Concurrency & Competing-Consumer Semantics
   *
   * This is a **competing-consumer** flow. Because [ConflatedSignal] only resumes one waiter per
   * signal, collectors of this flow **compete** for signals with each other and with direct callers
   * of [await].
   *
   * Specifically, if multiple collectors are actively collecting this flow concurrently, or if some
   * coroutines are suspended in [await], a single call to [signal] will notify exactly **one** of
   * them (either resuming a direct [await] caller or emitting to a single flow collector). It will
   * *not* broadcast the signal to all collectors.
   */
  val signals: Flow<Unit> = flow {
    while (true) {
      await()
      emit(Unit)
    }
  }

  /**
   * Whether there is a pending signal from a call to [signal] that has not yet been consumed by a
   * call to [await] or a collector of [signals].
   */
  val hasPendingSignal: Boolean
    get() = signalState.get()

  /**
   * Emits a signal to resume a suspended waiter, if one is present, or buffers the signal for the
   * next waiter if none are currently awaiting.
   *
   * Because this signal is conflated, multiple sequential calls to [signal] without intervening
   * calls to [await] (or collectors of [signal]) will be merged into a single signal. Only one
   * [await] call (or [signals] collector) will be resumed immediately, regardless of how many times
   * [signal] was called.
   *
   * This method is non-blocking, thread-safe, and safe to call from any context (including outside
   * of coroutines).
   */
  fun signal() {
    signalState.set(true)
    channel.trySend(Unit)
  }

  /**
   * Suspends the calling coroutine until a signal is received via [signal].
   *
   * If a signal has already been emitted and not yet consumed, this method returns immediately,
   * consuming that signal.
   *
   * ### Important Concurrency & Competing-Consumer Semantics
   *
   * Direct callers of [await] **compete** for signals with each other and with active collectors of
   * the [signals] flow. Specifically, if multiple coroutines are waiting (either suspended in
   * [await] or collecting from [signals]), a single call to [signal] will resume/notify exactly
   * **one** of them, chosen indeterminately. It will *not* broadcast the signal to all.
   *
   * ### Prompt cancellation guarantee
   *
   * This function provides **prompt cancellation guarantee**. That is, if the job is canceled while
   * [await] is suspended, it will not resume successfully, even if it consumed a signal, but throws
   * a [CancellationException].
   */
  suspend fun await() {
    while (true) {
      if (signalState.compareAndSet(true, false)) {
        break
      }
      channel.receive()
    }
  }
}
