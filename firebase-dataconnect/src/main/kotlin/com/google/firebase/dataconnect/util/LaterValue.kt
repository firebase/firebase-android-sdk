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

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
internal class LaterValue<T>(initialValue: MaybeValue<T> = MaybeValue.Empty) {

  private val _state = MutableStateFlow(initialValue)

  val state: StateFlow<MaybeValue<T>> = _state.asStateFlow()

  /**
   * Sets the value.
   *
   * @param value The value to set.
   * @throws IllegalStateException if [set] has already been called.
   */
  fun set(value: T) {
    if (!_state.compareAndSet(MaybeValue.Empty, MaybeValue.Value(value))) {
      error("set() has already been called")
    }
  }

  override fun toString() =
    when (val currentState = state.value) {
      MaybeValue.Empty -> "<unset>"
      is MaybeValue.Value -> currentState.value.toString()
    }
}

/** Returns [MaybeValue.isEmpty] of the [LaterValue.state] of the receiver. */
internal val LaterValue<*>.isEmpty: Boolean
  get() = state.value.isEmpty

/** Returns [MaybeValue.getOrNull] of the [LaterValue.state] of the receiver. */
internal fun <T> LaterValue<T>.getOrNull(): T? = state.value.getOrNull()

/** Returns [MaybeValue.getOrThrow] of the [LaterValue.state] of the receiver. */
internal fun <T> LaterValue<T>.getOrThrow(): T =
  when (val currentState = state.value) {
    is MaybeValue.Value -> currentState.value
    is MaybeValue.Empty -> error("set() has not yet been called")
  }

/** Returns [MaybeValue.getOrElse] of the [LaterValue.state] of the receiver. */
@OptIn(ExperimentalContracts::class)
internal inline fun <T> LaterValue<T>.getOrElse(block: () -> T): T {
  contract { callsInPlace(block, kotlin.contracts.InvocationKind.AT_MOST_ONCE) }
  return state.value.getOrElse(block)
}

/** Returns [MaybeValue.ifEmpty] of the [LaterValue.state] of the receiver. */
@OptIn(ExperimentalContracts::class)
internal inline fun <T> LaterValue<T>.ifEmpty(block: () -> Unit): LaterValue<T> {
  contract { callsInPlace(block, kotlin.contracts.InvocationKind.AT_MOST_ONCE) }
  state.value.ifEmpty(block)
  return this
}

/** Returns [MaybeValue.ifNonEmpty] of the [LaterValue.state] of the receiver. */
@OptIn(ExperimentalContracts::class)
internal inline fun <T> LaterValue<T>.ifNonEmpty(block: (T) -> Unit): LaterValue<T> {
  contract { callsInPlace(block, kotlin.contracts.InvocationKind.AT_MOST_ONCE) }
  state.value.ifNonEmpty(block)
  return this
}
