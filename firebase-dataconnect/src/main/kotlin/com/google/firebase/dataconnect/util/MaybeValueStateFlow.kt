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
import kotlinx.coroutines.flow.StateFlow

/** Returns [MaybeValue.isEmpty] of the [StateFlow.value] of the receiver. */
internal val StateFlow<MaybeValue<*>>.isEmpty: Boolean
  get() = value.isEmpty

/** Returns [MaybeValue.getOrNull] of the [StateFlow.value] of the receiver. */
internal fun <T> StateFlow<MaybeValue<T>>.getOrNull(): T? = value.getOrNull()

/** Returns [MaybeValue.getOrThrow] of the [StateFlow.value] of the receiver. */
internal fun <T> StateFlow<MaybeValue<T>>.getOrThrow(): T = value.getOrThrow()

/** Returns [MaybeValue.getOrElse] of the [StateFlow.value] of the receiver. */
@OptIn(ExperimentalContracts::class)
internal inline fun <T> StateFlow<MaybeValue<T>>.getOrElse(block: () -> T): T {
  contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
  return value.getOrElse(block)
}

/**
 * Calls [MaybeValue.ifEmpty] of the [StateFlow.value] of the receiver.
 *
 * Note that unlike [MaybeValue.ifEmpty], which returns a reference to itself to facilitate
 * chaining, this function returns [Unit]. This is done to _discourage_ calling [ifEmpty] and
 * [ifNonEmpty] one after the other because it is a race condition because the value _could_ change
 * in between the calls. Instead, [fold] is provided for this purpose, which does _not_ have a race
 * condition.
 */
@OptIn(ExperimentalContracts::class)
internal inline fun <T> StateFlow<MaybeValue<T>>.ifEmpty(block: () -> Unit) {
  contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
  value.ifEmpty(block)
}

/**
 * Returns [MaybeValue.ifNonEmpty] of the [StateFlow.value] of the receiver.
 *
 * Note that unlike [MaybeValue.ifNonEmpty], which returns a reference to itself to facilitate
 * chaining, this function returns [Unit]. This is done to _discourage_ calling [ifEmpty] and
 * [ifNonEmpty] one after the other because it is a race condition because the value _could_ change
 * in between the calls. Instead, [fold] is provided for this purpose, which does _not_ have a race
 * condition.
 */
@OptIn(ExperimentalContracts::class)
internal inline fun <T> StateFlow<MaybeValue<T>>.ifNonEmpty(block: (T) -> Unit) {
  contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
  value.ifNonEmpty(block)
}

/** Returns [MaybeValue.fold] of the [StateFlow.value] of the receiver. */
@OptIn(ExperimentalContracts::class)
internal inline fun <T, R> StateFlow<MaybeValue<T>>.fold(
  onEmpty: () -> R,
  onNonEmpty: (T) -> R,
): R {
  contract {
    callsInPlace(onEmpty, InvocationKind.AT_MOST_ONCE)
    callsInPlace(onNonEmpty, InvocationKind.AT_MOST_ONCE)
  }
  return value.fold(onEmpty, onNonEmpty)
}
