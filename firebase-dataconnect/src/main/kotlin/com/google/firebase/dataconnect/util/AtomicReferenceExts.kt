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

/**
 * Atomically updates the value of this [AtomicReference] by repeatedly applying the given [block]
 * function until the update is successful.
 *
 * The [block] function takes the current value as its argument and returns the new value.
 *
 * Note that [block] may be called multiple times if concurrent updates occur. It should be free of
 * side effects. If the new value returned by [block] is referentially identical (`===`) to the
 * current value, the update is aborted early to avoid unnecessary write operations.
 *
 * **Important Concurrency Note (Memory Visibility):** If the new value is referentially identical
 * (`===`) to the current value, the update is aborted early and no write operation is performed.
 *
 * In multithreaded programming, writing to an atomic variable typically guarantees that all
 * previous changes made by this thread (even to regular, non-atomic variables) become visible to
 * other threads that subsequently read this atomic variable (known as a "happens-before"
 * relationship).
 *
 * Because this function skips the write operation when the value doesn't change, **it does not
 * guarantee that other threads will see prior changes to other variables.** Do not rely on this
 * function to "publish" changes to other variables if the atomic reference itself does not change.
 *
 * @param T the type of the value held by the reference.
 * @param block the function to calculate the next value based on the current value.
 */
internal inline fun <T> AtomicReference<T>.update(block: (currentValue: T) -> T) {
  while (true) {
    val currentValue = get()
    val newValue = block(currentValue)

    if (currentValue === newValue) {
      break
    }

    if (compareAndSet(currentValue, newValue)) {
      break
    }
  }
}

/**
 * Identical to [update] with a [Unit] return value, but instead returns the old value and updated
 * value, making this function slightly less performant than its [Unit]-returning counterpart due to
 * the allocation of the [AtomicReferenceUpdateResult] return value in the case of an update.
 */
internal inline fun <T> AtomicReference<T>.updateWithResult(
  block: (currentValue: T) -> T
): AtomicReferenceUpdateResult<T> {
  while (true) {
    val currentValue = get()
    val newValue = block(currentValue)

    if (currentValue === newValue) {
      return AtomicReferenceUpdateResult.NotUpdated
    }

    if (compareAndSet(currentValue, newValue)) {
      return AtomicReferenceUpdateResult.Updated(currentValue, newValue)
    }
  }
}

internal sealed interface AtomicReferenceUpdateResult<out T> {
  object NotUpdated : AtomicReferenceUpdateResult<Nothing> {
    override fun toString() = "NoChange"
  }

  class Updated<out T>(val oldValue: T, val newValue: T) : AtomicReferenceUpdateResult<T> {
    override fun toString() = "Updated"
  }
}
