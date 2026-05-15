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
