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

/** A discriminated union of a (possibly null) value or the absence of a value (empty). */
internal sealed interface MaybeValue<out T> {

  /** Whether this object is "empty" (has no value). */
  val isEmpty: Boolean

  /** Returns the value, if this object has a value, or `null` if it does not. */
  fun getOrNull(): T?

  /**
   * Returns the value, if this object has a value, or throws an exception if it does not.
   *
   * @throws IllegalStateException if this object does not have a value.
   */
  fun getOrThrow(): T

  /** The implementation of [MaybeValue] that _has_ a value (is non-empty). */
  data class Value<out T>(val value: T) : MaybeValue<T> {
    override val isEmpty: Boolean
      get() = false

    override fun getOrNull(): T? = value

    override fun getOrThrow(): T = value

    override fun toString() = "MaybeValue.Value($value)"
  }

  /** The implementation of [MaybeValue] that _does not_ have a value (is empty). */
  object Empty : MaybeValue<Nothing> {
    override val isEmpty: Boolean
      get() = true

    override fun getOrNull() = null

    override fun getOrThrow() = error("no value")

    override fun toString() = "MaybeValue.Empty"
  }
}

/**
 * Returns the receiver's value if it is non-empty, or the value returned from the given [block]
 * otherwise.
 *
 * If receiver is [MaybeValue.Empty] then [block] is called exactly once and the value returned from
 * [block] is returned; otherwise, the [MaybeValue.Value.value] of the receiver is returned and
 * [block] is _not_ called.
 *
 * @param block The block to execute if the receiver is empty.
 * @return The value of the receiver, or the value returned from [block] if [isEmpty].
 */
@OptIn(ExperimentalContracts::class)
internal inline fun <T> MaybeValue<T>.getOrElse(block: () -> T): T {
  contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
  return when (this) {
    MaybeValue.Empty -> block()
    is MaybeValue.Value -> value
  }
}

@Suppress("UnusedReceiverParameter")
@OptIn(ExperimentalContracts::class)
internal inline fun <T> MaybeValue.Empty.getOrElse(block: () -> T): T {
  contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
  return block()
}

@OptIn(ExperimentalContracts::class)
internal inline fun <T> MaybeValue.Value<T>.getOrElse(block: () -> T): T {
  contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
  return value
}

/**
 * Calls the given [block] if, and only if, the receiver is empty.
 *
 * If receiver is [MaybeValue.Empty] then [block] is called exactly once; otherwise, this method
 * does nothing and, specifically, [block] is _not_ called.
 *
 * @param block The block to execute if the receiver is empty.
 * @return The receiver, for chaining.
 */
@OptIn(ExperimentalContracts::class)
internal inline fun <T> MaybeValue<T>.ifEmpty(block: () -> Unit): MaybeValue<T> {
  contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
  if (this is MaybeValue.Empty) {
    block()
  }
  return this
}

@OptIn(ExperimentalContracts::class)
internal inline fun MaybeValue.Empty.ifEmpty(block: () -> Unit): MaybeValue.Empty {
  contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
  block()
  return this
}

@OptIn(ExperimentalContracts::class)
internal inline fun <T> MaybeValue.Value<T>.ifEmpty(block: () -> Unit): MaybeValue.Value<T> {
  contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
  return this
}

/**
 * Calls the given [block] if, and only if, the receiver is non-empty.
 *
 * If receiver is [MaybeValue.Value] then [block] is called exactly once with the value; otherwise,
 * this method does nothing and, specifically, [block] is _not_ called.
 *
 * @param block The block to execute if the receiver is non-empty.
 * @return The receiver, for chaining.
 */
@OptIn(ExperimentalContracts::class)
internal inline fun <T> MaybeValue<T>.ifNonEmpty(block: (T) -> Unit): MaybeValue<T> {
  contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
  if (this is MaybeValue.Value) {
    block(value)
  }
  return this
}

@OptIn(ExperimentalContracts::class)
internal inline fun MaybeValue.Empty.ifNonEmpty(block: (Nothing) -> Unit): MaybeValue.Empty {
  contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
  return this
}

@OptIn(ExperimentalContracts::class)
internal inline fun <T> MaybeValue.Value<T>.ifNonEmpty(block: (T) -> Unit): MaybeValue.Value<T> {
  contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
  block(value)
  return this
}

/**
 * Calls the given [onEmpty] if the receiver is empty, or [onNonEmpty] if the receiver is non-empty.
 *
 * If receiver is [MaybeValue.Value] then [onNonEmpty] is called exactly once with the value and
 * [onEmpty] is not called at all; otherwise, if receiver is [MaybeValue.Empty], [onEmpty] is called
 * exactly once and [onNonEmpty] is not called at all.
 *
 * @param onEmpty The block to execute if the receiver is empty.
 * @param onNonEmpty The block to execute if the receiver is non-empty.
 * @return The value returned from [onEmpty] or [onNonEmpty], whichever was called.
 */
@OptIn(ExperimentalContracts::class)
internal inline fun <T, R> MaybeValue<T>.fold(
  onEmpty: () -> R,
  onNonEmpty: (T) -> R,
): R {
  contract {
    callsInPlace(onEmpty, InvocationKind.AT_MOST_ONCE)
    callsInPlace(onNonEmpty, InvocationKind.AT_MOST_ONCE)
  }
  return when (this) {
    MaybeValue.Empty -> onEmpty()
    is MaybeValue.Value -> onNonEmpty(value)
  }
}

@Suppress("UnusedReceiverParameter")
@OptIn(ExperimentalContracts::class)
internal inline fun <R> MaybeValue.Empty.fold(
  onEmpty: () -> R,
  onNonEmpty: (Nothing) -> R,
): R {
  contract {
    callsInPlace(onEmpty, InvocationKind.EXACTLY_ONCE)
    callsInPlace(onNonEmpty, InvocationKind.AT_MOST_ONCE)
  }
  return onEmpty()
}

@OptIn(ExperimentalContracts::class)
internal inline fun <T, R> MaybeValue.Value<T>.fold(
  onEmpty: () -> R,
  onNonEmpty: (T) -> R,
): R {
  contract {
    callsInPlace(onEmpty, InvocationKind.AT_MOST_ONCE)
    callsInPlace(onNonEmpty, InvocationKind.EXACTLY_ONCE)
  }
  return onNonEmpty(value)
}
