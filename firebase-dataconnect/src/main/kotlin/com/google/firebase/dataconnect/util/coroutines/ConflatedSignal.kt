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

import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * A coroutine-based signaling mechanism that allows a waiter to suspend until a sender provides a
 * signal value of type [T], conflating multiple signal values by retaining only the latest one.
 *
 * This class is designed to coordinate execution flow and transfer data between asynchronous
 * operations (such as a producer and a consumer). A consumer can wait for a signal value either by
 * calling the suspending method [await] or by collecting from the [signals] flow. In both cases,
 * the consumer will resume immediately and receive the value once a producer calls [signal].
 *
 * @param T the non-nullable type of the signal value (payload) carried by this signal.
 *
 * ### Key Behavioral Characteristics
 *
 * * **Single Signal Delivery / Competing Consumers:** If multiple coroutines are waiting for a
 * signal, whether they are suspended in [await] or collecting from the [signals] flow, then exactly
 * _one_ of them will be resumed with the value upon [signal] being called, chosen indeterminately.
 * This is *not* a broadcast event or a `signalAll` style primitive. Waiters and flow collectors
 * **compete** for signals and their associated values.
 * * **Signal Conflation:** The signal value is conflated and persistent until consumed. If [signal]
 * is called multiple times before any coroutine awaits, only the **most recent** signal value is
 * retained, and all previous values are overwritten and lost. The first subsequent waiter (either
 * via [await] or [signals] collection) will consume this latest value and resume immediately, while
 * any further waiters will suspend. The pending signal value can also be discarded without being
 * consumed by calling [clear].
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
internal class ConflatedSignal<T : Any> {

  private val signalState = AtomicReference<T?>(null)
  private val channel = Channel<Unit>(CONFLATED)

  /**
   * A cold [Flow] that emits the signal value of type [T] every time a signal is successfully
   * consumed by this flow collector.
   *
   * ### Important Concurrency & Competing-Consumer Semantics
   *
   * This is a **competing-consumer** flow. Because [ConflatedSignal] only delivers the signal value
   * to one waiter per signal, collectors of this flow **compete** for signals with each other and
   * with direct callers of [await].
   *
   * Specifically, if multiple collectors are actively collecting this flow concurrently, or if some
   * coroutines are suspended in [await], a single call to [signal] will deliver the value to
   * exactly **one** of them (either returning the value to a direct [await] caller or emitting the
   * value to a single flow collector, chosen indeterminately). It will *not* broadcast the signal
   * value to all collectors.
   */
  val signals: Flow<T> = flow {
    while (true) {
      emit(await())
    }
  }

  /**
   * Whether there is a pending signal from a call to [signal] that has not yet been consumed by a
   * call to [await], collected by a [signals] collector, or discarded by a call to [clear].
   */
  val hasPendingSignal: Boolean
    get() = signalState.get() != null

  /**
   * The value of the pending signal, or `null` if there is no pending signal.
   *
   * Reading this property does not consume the signal.
   */
  val pendingSignal: T?
    get() = signalState.get()

  /**
   * Emits a signal carrying the given [value].
   *
   * If a waiter is currently suspended (either in [await] or collecting from [signals]), it is
   * resumed and receives this [value]. If no waiters are currently awaiting, the [value] is
   * buffered as a pending signal for the next waiter.
   *
   * Because this signal is conflated, multiple sequential calls to [signal] without intervening
   * consumption (via [await] or [signals]) or clearance (via [clear]) will overwrite the pending
   * value, retaining only the **most recent** [value]. Only one waiter will be resumed with this
   * latest value, and all previously signaled values since the last consumption are lost.
   *
   * This method is non-blocking, thread-safe, and safe to call from any context (including outside
   * of coroutines).
   *
   * @param value the signal value of type [T] to emit.
   */
  fun signal(value: T) {
    signalState.set(value)
    channel.trySend(Unit)
  }

  /**
   * Suspends the calling coroutine until a signal value of type [T] is received.
   *
   * If a signal value has already been emitted and not yet consumed (or cleared via [clear]), this
   * method returns immediately, returning and consuming that value.
   *
   * @return the signal value of type [T] that was emitted.
   *
   * ### Important Concurrency & Competing-Consumer Semantics
   *
   * Direct callers of [await] **compete** for signals and their values with each other and with
   * active collectors of the [signals] flow. Specifically, if multiple coroutines are waiting
   * (either suspended in [await] or collecting from [signals]), a single call to [signal] will
   * deliver the value and resume exactly **one** of them, chosen indeterminately. It will *not*
   * broadcast the value to all.
   *
   * ### Prompt cancellation guarantee
   *
   * This function provides a **prompt cancellation guarantee**. That is, if the job is canceled
   * while [await] is suspended, it will not resume successfully (even if it technically received a
   * signal value), but will throw a [CancellationException].
   */
  suspend fun await(): T {
    while (true) {
      val current = signalState.get()
      if (current == null) {
        channel.receive()
      } else if (signalState.compareAndSet(current, null)) {
        return current
      }
    }
  }

  /**
   * Clears any pending signal, discarding its value.
   *
   * After calling this method, any subsequent call to [await] will suspend until a new signal is
   * emitted via [signal].
   */
  fun clear() {
    signalState.set(null)
    channel.tryReceive()
  }

  override fun toString() = "ConflatedSignal(hasPendingSignal=$hasPendingSignal)"
}

/**
 * Extension function to allow signaling a [ConflatedSignal] of type [Unit] without passing an
 * argument, mimicking the original parameterless `signal()` behavior.
 */
internal fun ConflatedSignal<Unit>.signal() {
  signal(Unit)
}

/** Convenience extension function to signal a [ConflatedSignal] if the given signal is not null. */
internal fun <T : Any> ConflatedSignal<T>.signalIfNotNull(signal: T?) {
  if (signal != null) {
    signal(signal)
  }
}
