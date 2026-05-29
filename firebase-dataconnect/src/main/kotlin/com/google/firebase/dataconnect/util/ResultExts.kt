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

import kotlinx.coroutines.CancellationException

/**
 * Throws the encapsulated [CancellationException] if this [Result] represents a failure with a
 * [CancellationException].
 *
 * This convenience method is helpful in coroutines because [CancellationException] should generally
 * be re-thrown rather than "passed around" (or swallowed) as it has special meaning for coroutine
 * cancellation and structured concurrency.
 *
 * @throws CancellationException if the encapsulated exception is a [CancellationException].
 */
internal fun Result<*>.throwIfCancellationException() {
  val exception = exceptionOrNull()
  if (exception is CancellationException) {
    throw exception
  }
}
