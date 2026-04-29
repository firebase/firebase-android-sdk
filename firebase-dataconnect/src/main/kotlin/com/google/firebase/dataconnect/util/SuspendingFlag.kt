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

/**
 * A thread-safe "flag" that is initially "unset" and can eventually transition to being "set".
 *
 * All methods and properties of [SuspendingFlag] are thread-safe and may be safely called and/or
 * accessed concurrently from multiple threads and/or coroutines.
 *
 * @param initialValue The initial value for the [state] property, either [Values.unset] (the
 * default) or [Values.set].
 */
internal class SuspendingFlag(initialValue: MaybeValue<Unit> = Values.unset) {

  /**
   * The "state" of this flag.
   *
   * A value of [Values.unset] indicates that the flag is _not_ set and a value of [Values.set]
   * indicates that the flag _is_ set.
   */
  val state = ClearableValue(initialValue)

  /**
   * Generates and returns a string representation of this object.
   *
   * If this object is in the "cleared" state then the return value is a string indicating
   * "cleared"; otherwise, the result of [toString] on the value is returned verbatim.
   */
  override fun toString() = "SuspendingFlag(set=${isSet})"

  /** The values of [state] that are supported. */
  object Values {

    /** The [MaybeValue] that represents the flag being "set". */
    val set = MaybeValue.Empty

    /** The [MaybeValue] that represents the flag being "unset". */
    val unset = MaybeValue.Value(Unit)
  }
}

/**
 * Transitions the receiver [SuspendingFlag] to the "set" state, if it was not already in that
 * state.
 *
 * Once the flag is set, it is set forever and can never be "un-set".
 *
 * The directly-observable side effect of this method is that the value of the receiver's
 * [SuspendingFlag.state] changes to [SuspendingFlag.Values.set] (if it was not already that value).
 * Additionally, all future checks of [isSet] will return `true`.
 *
 * This method may be called multiple times. Subsequent invocations have no effect and return as if
 * successful.
 *
 * @return whether the receiver [SuspendingFlag] transition to the "set" state was as a result of
 * this specific invocation: `true` if it _was_ this specific call that transitioned the receiver to
 * the "set" state, or `false` if some other call caused the transition; at most one invocation of
 * this method return true object, with all others returning `false`.
 */
internal fun SuspendingFlag.set(): Boolean = state.clear() == SuspendingFlag.Values.unset

/**
 * Returns whether the receiver [SuspendingFlag] is in the "set" state.
 *
 * A [SuspendingFlag] object is considered to be in the "set" state if, and only if, the value of
 * its [SuspendingFlag.state] is [SuspendingFlag.Values.set]. This "set" state is reachable either
 * by the [SuspendingFlag] being initialized with [SuspendingFlag.Values.set] or by [set] being
 * called.
 */
internal val SuspendingFlag.isSet: Boolean
  get() = state.isCleared

/**
 * Calls the given [block] if, and only if, the receiver is in the "set" state according to [isSet].
 *
 * @param block the block to call if, and only if, the receiver is in the "set" state; if called, it
 * will be called in-place exactly once.
 */
@OptIn(ExperimentalContracts::class)
internal inline fun SuspendingFlag.ifSet(block: () -> Unit) {
  contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
  state.ifCleared(block)
}

/**
 * Calls the given [block] if, and only if, the receiver is _not_ in the "set" state according to
 * [isSet].
 *
 * @param block the block to call if, and only if, the receiver is _not_ in the "set" state; if
 * called, it will be called in-place exactly once and its argument will be the value with which the
 * receiver was initialized.
 */
@OptIn(ExperimentalContracts::class)
internal inline fun SuspendingFlag.ifNotSet(block: () -> Unit) {
  contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
  state.ifNotCleared { block() }
}

/**
 * Transitions the receiver [SuspendingFlag] to the "set" state, if it was not already in that state
 * according to [isSet], calling the given [block] if the receiver was already in the "set" state.
 *
 * When this function returns the receiver is guaranteed to be in the "cleared" state.
 *
 * @param block the block to call, and whose return value to return, if, and only if, the receiver
 * is in the "cleared" state; if called, the block will be called in-place exactly once.
 */
@OptIn(ExperimentalContracts::class)
internal inline fun SuspendingFlag.setOrElse(block: () -> Unit) {
  contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
  state.clearOrElse(block)
}

/**
 * Calls the given function that corresponds to whether the receiver is in the "set" state according
 * to [isSet].
 *
 * Exactly one of [onSet] or [onNotSet] will be called, and will be called in-place exactly once.
 * This method returns whatever value is returned from the invocation of [onSet] or [onNotSet].
 *
 * @param onSet the function to call if the receiver is in the "set" state.
 * @param onNotSet the function to call if the receiver is _not_ in the "set" state.
 */
@OptIn(ExperimentalContracts::class)
internal inline fun <T, R> SuspendingFlag.fold(
  onSet: () -> R,
  onNotSet: () -> R,
): R {
  contract {
    callsInPlace(onSet, InvocationKind.AT_MOST_ONCE)
    callsInPlace(onNotSet, InvocationKind.AT_MOST_ONCE)
  }
  return state.fold(onSet, { onNotSet() })
}
