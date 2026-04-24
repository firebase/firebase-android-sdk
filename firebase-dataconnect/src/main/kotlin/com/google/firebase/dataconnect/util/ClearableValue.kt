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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * A thread-safe container for a value that can be cleared.
 *
 * This class is useful for scenarios where the reference to a value should be cleared at some later
 * time in order to allow it to be garbage collected.
 *
 * This class correctly handles nullable values for [T].
 *
 * All methods and properties of [ClearableValue] are thread-safe and may be safely called and/or
 * accessed concurrently from multiple threads and/or coroutines.
 */
internal class ClearableValue<T>(initialValue: MaybeValue<T>) {

  constructor(initialValue: T) : this(MaybeValue.Value(initialValue))

  private val _state = MutableStateFlow(initialValue)

  val state: StateFlow<MaybeValue<T>> = _state.asStateFlow()

  /**
   * Clears the value.
   *
   * This method may be called multiple times. Subsequent invocations have no effect.
   */
  fun clear() {
    _state.value = MaybeValue.Empty
  }

}

/**
 * Returns [MaybeValue.isEmpty] of the [ClearableValue.state] of the receiver.
 */
internal val ClearableValue<*>.isEmpty: Boolean get() = state.value.isEmpty

/**
 * Returns [MaybeValue.getOrNull] of the [ClearableValue.state] of the receiver.
 */
internal fun <T> ClearableValue<T>.getOrNull(): T? = state.value.getOrNull()

/**
 * Returns [MaybeValue.getOrThrow] of the [ClearableValue.state] of the receiver.
 */
internal fun <T> ClearableValue<T>.getOrThrow(): T = state.value.getOrThrow()

/**
 * Returns [MaybeValue.getOrElse] of the [ClearableValue.state] of the receiver.
 */
@OptIn(ExperimentalContracts::class)
internal inline fun <T> ClearableValue<T>.getOrElse(block: () -> T): T {
  contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
  return state.value.getOrElse(block)
}

/**
 * Returns [MaybeValue.ifEmpty] of the [ClearableValue.state] of the receiver.
 */
@OptIn(ExperimentalContracts::class)
internal inline fun <T> ClearableValue<T>.ifEmpty(block: () -> Unit): ClearableValue<T> {
  contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
  state.value.ifEmpty(block)
  return this
}

/**
 * Returns [MaybeValue.ifNonEmpty] of the [ClearableValue.state] of the receiver.
 */
@OptIn(ExperimentalContracts::class)
internal inline fun <T> ClearableValue<T>.ifNonEmpty(block: (T) -> Unit): ClearableValue<T> {
  contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
  state.value.ifNonEmpty(block)
  return this
}
