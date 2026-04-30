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
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first

/**
 * A thread-safe container for a value that can be set at most once.
 *
 * This class is useful for scenarios where a value is computed or received asynchronously and needs
 * to be stored for later retrieval, ensuring that it is not modified once set.
 *
 * This class correctly handles nullable values for [T], differentiating between a value of `null`
 * and the value not having been set.
 *
 * All methods and properties of [LaterValue] are thread-safe and may be safely called and/or
 * accessed concurrently from multiple threads and/or coroutines.
 *
 * @param initialValue The initial value for the [LaterValue]. If [MaybeValue.Empty] (the default)
 * then the [LaterValue] will initially _not_ be in the "set" state. Otherwise, the initial value of
 * this object will be the given [MaybeValue.Value] object and the object will be in the "set"
 * state.
 */
internal class LaterValue<T>(initialValue: MaybeValue<T> = MaybeValue.Empty) {

  private val _state = MutableStateFlow<MaybeValue<T>>(initialValue)

  /**
   * The current state of this object: either [MaybeValue.Empty] or the [MaybeValue.Value] that was
   * specified to the primary constructor or set by a call to [set].
   *
   * A value of [MaybeValue.Empty] indicates that [set] has not been invoked and this object was
   * initialized with [MaybeValue.Empty]. Otherwise, a value of [MaybeValue.Value] indicates that
   * either this object was initialized with a [MaybeValue.Value] or [set] was called and this
   * object was initialized with [MaybeValue.Empty].
   *
   * There is only one transition that can happen with the value of the [StateFlow]: it can
   * transition from [MaybeValue.Empty] to [MaybeValue.Value] by a call to [set]. Once its value is
   * [MaybeValue.Value] it will never change again.
   */
  val state: StateFlow<MaybeValue<T>> = _state.asStateFlow()

  /**
   * Transitions this [LaterValue] to the "set" state with the given value.
   *
   * Once the value is set, it is set forever and can never be "un-set" or changed.
   *
   * The directly-observable side effect of this method is that, on success, the value of [state]
   * changes to [MaybeValue.Value]. Additionally, all future checks of [isSet] will return `true`.
   *
   * This method may only be called when this object is _not_ in the "set" state, according to
   * [isSet]. An exception is thrown if this method is called when this object is already in the
   * "set" state, either by virtue of a [MaybeValue.Value] being specified to the primary
   * constructor or by a previous call of [set].
   *
   * @param value The value to set in this object.
   * @throws IllegalStateException if this object is already in the "set" state.
   */
  fun set(value: T) {
    if (!compareAndSet(MaybeValue.Empty, value)) {
      error("set() has already been called")
    }
  }

  /**
   * Transitions this [LaterValue] to the "set" state with the given `update` value, if this object
   * is not already in the "set" state.
   *
   * Note that although the [expected] argument is not technically needed since [MaybeValue.Empty]
   * is a singleton, it's been left there to mirror the idiomatic "compareAndSet" method in atomic
   * operations, such as [MutableStateFlow.compareAndSet].
   *
   * @param expected must be [MaybeValue.Empty].
   * @param update the value to set.
   * @return returns `true` if this [LaterValue] transitioned to the "set" state and now has the
   * given [update] value as a consequence of this invocation; returns `false` if this method did
   * nothing because it was _already_ in the "set" state.
   */
  fun compareAndSet(@Suppress("unused") expected: MaybeValue.Empty, update: T): Boolean =
    when (val currentValue = _state.value) {
      MaybeValue.Empty -> _state.compareAndSet(currentValue, MaybeValue.Value(update))
      is MaybeValue.Value -> false
    }

  /**
   * Generates and returns a string representation of this object.
   *
   * If this object is not in the "set" state then the return value is a string indicating "unset";
   * otherwise, the result of [toString] on the value is returned verbatim.
   */
  override fun toString() =
    when (val currentState = state.value) {
      MaybeValue.Empty -> "<unset>"
      is MaybeValue.Value -> currentState.value.toString()
    }
}

/**
 * Returns whether the receiver [LaterValue] is in the "set" state.
 *
 * A [LaterValue] object is considered to be in the "set" state if, and only if, the value of its
 * [LaterValue.state] is a [MaybeValue.Value]. This "set" state is reachable either by the
 * [LaterValue] being initialized with [MaybeValue.Value] or by [set] being called on a [LaterValue]
 * that was initialized with [MaybeValue.Empty].
 */
internal val LaterValue<*>.isSet: Boolean
  get() = !state.isEmpty

/**
 * Returns the receiver's value if the receiver is in the "set" state according to [isSet], or
 * `null` if it is _not_ in the "set" state.
 *
 * Note that a return value of `null` could mean either that the receiver is not in the "set" state
 * _or_ that the receiver _is_ in the "set" state but its value is `null`. A subsequent check of
 * [isSet] can largely differentiate between these two meanings of a `null` return value; however,
 * there is still a race condition in that the state could have transitioned from the "unset" state
 * to the "set" state with a value of `null` by an interleaving call to [set] by another thread. To
 * absolutely and atomically distinguish between these two cases use one of the other methods that
 * handle these two cases naturally, such as [getOrThrow], [getOrElse], [ifNotSet], [ifSet], and
 * [fold].
 */
internal fun <T> LaterValue<T>.getOrNull(): T? = state.getOrNull()

/**
 * Returns the receiver's value if the receiver is in the "set" state according to [isSet], or
 * throws an exception if it is _not_ in the "set" state.
 *
 * @throws MaybeValue.NoValueException if the receiver's value is not set.
 */
internal fun <T> LaterValue<T>.getOrThrow(): T =
  when (val currentState = state.value) {
    is MaybeValue.Value -> currentState.value
    MaybeValue.Empty -> throw MaybeValue.NoValueException("set() has not yet been called")
  }

/**
 * Returns the receiver's value if the receiver is in the "set" state according to [isSet], or the
 * return value of [block] if the receiver is _not_ in the "set" state.
 *
 * @param block the block to call, and whose return value to return, if, and only if, the receiver
 * is _not_ in the "set" state; if called, the block will be called in-place exactly once.
 */
@OptIn(ExperimentalContracts::class)
internal inline fun <T> LaterValue<T>.getOrElse(block: () -> T): T {
  contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
  return state.getOrElse(block)
}

/**
 * Returns the receiver's value if the receiver is in the "set" state according to [isSet], or
 * transitions it to the "set" state using the value returned from the given [value] function.
 *
 * Note that just because the given [value] function is invoked does _not_ mean that the value it
 * returned has necessarily become the receiver's value. This is because there could have been some
 * other interleaving call to [LaterValue.set] or [LaterValue.compareAndSet] that set the value
 * _after_ this function called [value].
 *
 * The given [value] function will _only_ be called if the receiver does not have a value set. When
 * it _is_ called, it will be called exactly once, in-place.
 *
 * @param value The function to create the value to set.
 * @return the receiver's value, which may be the value returned from [value] or a value that was
 * set by someone else.
 */
@OptIn(ExperimentalContracts::class)
internal inline fun <T> LaterValue<T>.getOrSet(value: () -> T): T {
  contract { callsInPlace(value, InvocationKind.AT_MOST_ONCE) }

  when (val currentValue = state.value) {
    is MaybeValue.Value -> return currentValue.value
    is MaybeValue.Empty -> {
      val newValue = value()
      if (compareAndSet(currentValue, newValue)) {
        return newValue
      }
    }
  }

  when (val currentState = state.value) {
    is MaybeValue.Value -> return currentState.value
    MaybeValue.Empty ->
      error(
        "internal error eha6py3s3d: MaybeValue.Value was changed to MaybeValue.Empty, " +
          "which violates the invariants of this object"
      )
  }
}

/**
 * Calls the given [block] if, and only if, the receiver is _not_ in the "set" state according to
 * [isSet].
 *
 * @param block the block to call if, and only if, the receiver is _not_ in the "set" state; if
 * called, it will be called in-place exactly once.
 */
@OptIn(ExperimentalContracts::class)
internal inline fun <T> LaterValue<T>.ifNotSet(block: () -> Unit) {
  contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
  state.ifEmpty(block)
}

/**
 * Calls the given [block] if, and only if, the receiver is in the "set" state according to [isSet].
 *
 * @param block the block to call if, and only if, the receiver is in the "set" state; if called, it
 * will be called in-place exactly once and its argument will be the receiver's current value.
 */
@OptIn(ExperimentalContracts::class)
internal inline fun <T> LaterValue<T>.ifSet(block: (T) -> Unit) {
  contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
  state.ifNonEmpty(block)
}

/**
 * Calls the given function that corresponds to whether the receiver is in the "set" state according
 * to [isSet].
 *
 * Exactly one of [onNotSet] or [onSet] will be called, and will be called in-place exactly once.
 * This method returns whatever value is returned from the invocation of [onNotSet] or [onSet].
 *
 * @param onNotSet the function to call if the receiver is _not_ in the "set" state.
 * @param onSet the function to call if the receiver is in the "set" state; the single argument will
 * be the receiver's current value.
 */
@OptIn(ExperimentalContracts::class)
internal inline fun <T, R> LaterValue<T>.fold(
  onNotSet: () -> R,
  onSet: (T) -> R,
): R {
  contract {
    callsInPlace(onNotSet, InvocationKind.AT_MOST_ONCE)
    callsInPlace(onSet, InvocationKind.AT_MOST_ONCE)
  }
  return state.fold(onNotSet, onSet)
}

/**
 * Suspends until the receiver transitions to the "set" state, at which point this function returns
 * the value to which it was set.
 */
internal suspend fun <T> LaterValue<T>.await(): T = getOrElse {
  state.filterIsInstance<MaybeValue.Value<T>>().first().value
}
