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

import java.util.concurrent.atomic.AtomicReference
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * A thread-safe container for a value that can be set at most once.
 *
 * This class is useful for scenarios where a value is computed or received asynchronously and needs
 * to be stored for later retrieval, ensuring that it is not modified once set.
 *
 * This class correctly handles nullable values for [T].
 *
 * All methods and properties of [LaterValue] are thread-safe and may be safely called and/or
 * accessed concurrently from multiple threads and/or coroutines.
 */
internal class LaterValue<T> {

  private val state = AtomicReference<State<T>>(State.NoValue)

  /** Whether the value has been set by a call to [set]. */
  val isSet: Boolean
    get() = state.get() !is State.NoValue

  /**
   * Sets the value.
   *
   * @param value The value to set.
   * @throws IllegalStateException if [set] has already been called.
   */
  fun set(value: T) {
    if (!state.compareAndSet(State.NoValue, State.Value(value))) {
      error("set() has already been called")
    }
  }

  /**
   * Returns the value if it has been set, or `null` if it has not.
   *
   * Note that if [T] is a nullable type, this method returns `null` both when the value has not
   * been set and when it has been set to `null`. Use [isSet] to disambiguate.
   */
  fun getOrNull(): T? =
    when (val currentState = state.get()) {
      State.NoValue -> null
      is State.Value -> currentState.value
    }

  /**
   * Returns the value if it has been set, or throws an exception if it has not.
   *
   * @return The set value.
   * @throws IllegalStateException if the value has not yet been set.
   */
  fun getOrThrow(): T =
    when (val currentState = state.get()) {
      State.NoValue -> error("set() has not yet been called")
      is State.Value -> currentState.value
    }

  override fun toString() = state.get().toString()

  private sealed interface State<out T> {
    data object NoValue : State<Nothing> {
      override fun toString() = "<unset>"
    }
    data class Value<T>(val value: T) : State<T> {
      override fun toString() = value.toString()
    }
  }
}

/**
 * Executes the given [block] with the value of the receiver [LaterValue] if, and only if, it has
 * been set.
 *
 * If the value of the receiver [LaterValue] has _not_ been set, then [block] is not called.
 *
 * @param block The block to execute with the value.
 * @return This [LaterValue] instance for chaining.
 */
@OptIn(ExperimentalContracts::class)
internal inline fun <T> LaterValue<T>.ifSet(block: (T) -> Unit): LaterValue<T> {
  contract { callsInPlace(block, kotlin.contracts.InvocationKind.AT_MOST_ONCE) }
  if (isSet) {
    block(getOrThrow())
  }
  return this
}

/**
 * Returns the value set in the receiver [LaterValue], or the value returned from the given [block]
 * if [LaterValue.set] has not yet been called.
 *
 * If the value of the receiver [LaterValue] _has_ been set, then [block] is not called.
 *
 * @param block The block to execute if the receiver's [set] method has not been called.
 * @return This value set in the receiver [LaterValue], or the value returned from [block], if the
 * [LaterValue.set] has not been called on the receiver.
 */
@OptIn(ExperimentalContracts::class)
internal inline fun <T, R : T> LaterValue<T>.getOrElse(block: () -> R): T {
  contract { callsInPlace(block, kotlin.contracts.InvocationKind.AT_MOST_ONCE) }
  ifSet {
    return it
  }
  return block()
}
