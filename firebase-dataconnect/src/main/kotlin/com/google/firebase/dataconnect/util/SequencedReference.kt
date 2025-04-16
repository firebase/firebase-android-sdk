/*
 * Copyright 2024 Google LLC
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

import java.util.concurrent.atomic.AtomicLong

internal data class SequencedReference<out T>(val sequenceNumber: Long, val ref: T) {

  companion object {

    private val nextSequenceId = AtomicLong(0)

    /**
     * Returns a positive number on each invocation, with each returned value being strictly greater
     * than any value previously returned in this process.
     *
     * This function is thread-safe and may be called concurrently by multiple threads and/or
     * coroutines.
     */
    fun nextSequenceNumber(): Long {
      return nextSequenceId.incrementAndGet()
    }

    fun <T, U> SequencedReference<T>.map(block: (T) -> U): SequencedReference<U> =
      SequencedReference(sequenceNumber, block(ref))

    suspend fun <T, U> SequencedReference<T>.mapSuspending(
      block: suspend (T) -> U
    ): SequencedReference<U> = SequencedReference(sequenceNumber, block(ref))

    fun <T, U : SequencedReference<T>?> U.newerOfThisAnd(other: U): U =
      if (this == null && other == null) {
        // Suppress the warning that `this` is guaranteed to be null because the `null` literal
        // cannot
        // be used in place of `this` because if this extension function is called on a non-nullable
        // reference then `null` is a forbidden return value and compilation will fail.
        @Suppress("KotlinConstantConditions") this
      } else if (this == null) {
        other
      } else if (other == null) {
        this
      } else if (this.sequenceNumber > other.sequenceNumber) {
        this
      } else {
        other
      }

    inline fun <T : Any, reified U : T> SequencedReference<T>.asTypeOrNull():
      SequencedReference<U>? =
      if (ref is U) {
        @Suppress("UNCHECKED_CAST")
        this as SequencedReference<U>
      } else {
        null
      }

    inline fun <T : Any, reified U : T> SequencedReference<T>.asTypeOrThrow():
      SequencedReference<U> =
      asTypeOrNull()
        ?: throw IllegalStateException(
          "expected ref to have type ${U::class.qualifiedName}, " +
            "but got ${ref::class.qualifiedName} ($ref)"
        )
  }
}
