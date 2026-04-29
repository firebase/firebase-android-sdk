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
import kotlinx.coroutines.flow.getAndUpdate

/**
 * A thread-safe container for a value that can be cleared.
 *
 * This class is useful for scenarios where the reference to a value should be cleared at some later
 * time in order to allow it to be garbage collected.
 *
 * This class correctly handles nullable values for [T], correctly differentiating between a value
 * of `null` and the value having been cleared.
 *
 * All methods and properties of [ClearableValue] are thread-safe and may be safely called and/or
 * accessed concurrently from multiple threads and/or coroutines.
 *
 * @param initialValue The initial value for the [ClearableValue]. If it is [MaybeValue.Empty] then
 * the [ClearableValue] will initially be in the "cleared" state, as if [clear] had been invoked.
 * Otherwise, the initial value of this object will be the value of the given [MaybeValue.Value]
 * object.
 */
internal class ClearableValue<out T>(initialValue: MaybeValue<T>) {

  /**
   * Creates a new [ClearableValue] object that is initially in the non-cleared state and whose
   * value is the given value.
   *
   * This constructor is merely a shorthand for calling the primary constructor with a
   * [MaybeValue.Value] whose value is [initialValue].
   *
   * @param initialValue The value with which to initialize this object.
   */
  constructor(initialValue: T) : this(MaybeValue.Value(initialValue))

  private val _state = MutableStateFlow<MaybeValue<T>>(initialValue)

  /**
   * The current state of this object, either [MaybeValue.Empty] or the [MaybeValue.Value] that was
   * specified to the primary constructor.
   *
   * A value of [MaybeValue.Empty] indicates that either [clear] has been invoked or this object was
   * initialized with [MaybeValue.Empty] being specified to the primary constructor.
   *
   * Otherwise, a value of [MaybeValue.Value] indicates that [clear] has not been invoked and the
   * [MaybeValue.Value] object is that which was specified to the primary constructor.
   *
   * There is only one transition that can happen with the value of the [StateFlow]: it can
   * transition from [MaybeValue.Value] to [MaybeValue.Empty] by a call to [clear]. Once its value
   * is [MaybeValue.Empty] it will never change again.
   */
  val state: StateFlow<MaybeValue<T>> = _state.asStateFlow()

  /**
   * Transitions this [ClearableValue] to the "cleared" state.
   *
   * Once the value is cleared, it is cleared forever and can never be "un-cleared". If this object
   * was previously _not_ in the "cleared" state then all references to the value are released,
   * potentially making it candidate for garbage collection if there are no other strong references
   * to it.
   *
   * The directly-observable side effect of this method is that the value of [state] changes to
   * [MaybeValue.Empty]. Additionally, all future checks of [isCleared] will return `true`.
   *
   * This method may be called multiple times. Subsequent invocations have no effect and return as
   * if successful.
   *
   * @return the old value of [state]; will be an instance of [MaybeValue.Value] if this specific
   * invocation of [clear] caused the state to transition to the "cleared" state; otherwise, will be
   * [MaybeValue.Empty] if some other call caused the state to transition to the "cleared" state; at
   * most one invocation of this method on a given instance will return a [MaybeValue.Value] object,
   * with all others returning [MaybeValue.Empty].
   */
  fun clear(): MaybeValue<T> = _state.getAndUpdate { MaybeValue.Empty }

  /**
   * Generates and returns a string representation of this object.
   *
   * If this object is in the "cleared" state then the return value is a string indicating
   * "cleared"; otherwise, the result of [toString] on the value is returned verbatim.
   */
  override fun toString() =
    when (val currentState = state.value) {
      MaybeValue.Empty -> "<cleared>"
      is MaybeValue.Value -> currentState.value.toString()
    }
}

/**
 * Returns whether the receiver [ClearableValue] is in the "cleared" state.
 *
 * A [ClearableValue] object is considered to be in the "cleared" state if, and only if, the value
 * of its [ClearableValue.state] is [MaybeValue.Empty]. This "cleared" state is reachable either by
 * the [ClearableValue] being initialized with [MaybeValue.Empty] or by [clear] being called on a
 * [ClearableValue] that was initialized with a [MaybeValue.Value] object.
 */
internal val ClearableValue<*>.isCleared: Boolean
  get() = state.isEmpty

/**
 * Returns the receiver's value if the receiver is not in the "cleared" state according to
 * [isCleared], or `null` if it _is_ in the "cleared" state.
 *
 * Note that a return value of `null` could mean either that the receiver is in the "cleared" state
 * _or_ that the receiver is _not_ in the "cleared" state but its value is `null`. A subsequent
 * check of [isCleared] can largely differentiate between these two meanings of a `null` return
 * value; however, there is still a race condition in that the state could have transitioned from a
 * value of `null` to the "cleared" state by an interleaving call to [clear] by another thread. To
 * absolutely and atomically distinguish between these two cases use one of the other methods that
 * handle these two cases naturally, such as [getOrThrow], [getOrElse], [ifCleared], [ifNotCleared],
 * and [fold].
 */
internal fun <T> ClearableValue<T>.getOrNull(): T? = state.getOrNull()

/**
 * Returns the receiver's value if the receiver is not in the "cleared" state according to
 * [isCleared], or throws an exception if it _is_ in the "cleared" state.
 *
 * @throws MaybeValue.NoValueException if the receiver is in the "cleared" state.
 */
internal fun <T> ClearableValue<T>.getOrThrow(): T =
  when (val currentState = state.value) {
    is MaybeValue.Value -> currentState.value
    is MaybeValue.Empty -> throw MaybeValue.NoValueException("clear() has been called")
  }

/**
 * Returns the receiver's value if the receiver is not in the "cleared" state according to
 * [isCleared], or the return value of [block] if the receiver _is_ in the "cleared" state.
 *
 * @param block the block to call, and whose return value to return, if, and only if, the receiver
 * is in the "cleared" state; if called, the block will be called in-place exactly once.
 */
@OptIn(ExperimentalContracts::class)
internal inline fun <T> ClearableValue<T>.getOrElse(block: () -> T): T {
  contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
  return state.getOrElse(block)
}

/**
 * Clears the receiver's value if the receiver is not in the "cleared" state according to
 * [isCleared], returning the receiver's value, or the return value of [block] if the receiver _was_
 * in the "cleared" state.
 *
 * This function is identical to [getOrElse] except that it _also_ atomically clears the receiver's
 * value. If many threads are calling this method on a receiver that is _not_ in the "cleared" state
 * then exactly one of those calls will get the receiver's value and the rest will have their
 * [block] called.
 *
 * When this function returns the receiver is guaranteed to be in the "cleared" state.
 *
 * @param block the block to call, and whose return value to return, if, and only if, the receiver
 * is in the "cleared" state; if called, the block will be called in-place exactly once.
 */
@OptIn(ExperimentalContracts::class)
internal inline fun <T> ClearableValue<T>.clearOrElse(block: () -> T): T {
  contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
  return when (val oldValue = clear()) {
    MaybeValue.Empty -> block()
    is MaybeValue.Value -> oldValue.value
  }
}

/**
 * Calls the given [block] if, and only if, the receiver is in the "cleared" state according to
 * [isCleared].
 *
 * @param block the block to call if, and only if, the receiver is in the "cleared" state; if
 * called, it will be called in-place exactly once.
 */
@OptIn(ExperimentalContracts::class)
internal inline fun <T> ClearableValue<T>.ifCleared(block: () -> Unit) {
  contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
  state.ifEmpty(block)
}

/**
 * Calls the given [block] if, and only if, the receiver is _not_ in the "cleared" state according
 * to [isCleared].
 *
 * @param block the block to call if, and only if, the receiver is _not_ in the "cleared" state; if
 * called, it will be called in-place exactly once and its argument will be the value with which the
 * receiver was initialized.
 */
@OptIn(ExperimentalContracts::class)
internal inline fun <T> ClearableValue<T>.ifNotCleared(block: (T) -> Unit) {
  contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
  state.ifNonEmpty(block)
}

/**
 * Calls the given function that corresponds to whether the receiver is in the "cleared" state
 * according to [isCleared].
 *
 * Exactly one of [onCleared] or [onNotCleared] will be called, and will be called in-place exactly
 * once. This method returns whatever value is returned from the invocation of [onCleared] or
 * [onNotCleared].
 *
 * @param onCleared the function to call if the receiver is in the "cleared" state.
 * @param onNotCleared the function to call if the receiver is _not_ in the "cleared" state; the
 * single argument will be the receiver's current value (the value with which it was initialized).
 */
@OptIn(ExperimentalContracts::class)
internal inline fun <T, R> ClearableValue<T>.fold(
  onCleared: () -> R,
  onNotCleared: (T) -> R,
): R {
  contract {
    callsInPlace(onCleared, InvocationKind.AT_MOST_ONCE)
    callsInPlace(onNotCleared, InvocationKind.AT_MOST_ONCE)
  }
  return state.fold(onCleared, onNotCleared)
}
