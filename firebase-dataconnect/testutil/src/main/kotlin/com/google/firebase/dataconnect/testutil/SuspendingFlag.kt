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

package com.google.firebase.dataconnect.testutil

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

/** Encapsulates a boolean "flag", allowing coroutines to suspend until the flag is set. */
class SuspendingFlag {
  private val _isSet = MutableStateFlow(false)

  /**
   * The value of the flag encapsulated by this [SuspendingFlag].
   *
   * This value is always initialized to `false` and can be set to `true` by an invocation of [set].
   *
   * @see set
   * @see await
   */
  val isSet: Boolean by _isSet::value

  /**
   * Suspend the current coroutine until the flag encapsulated by this [SuspendingFlag] is set, or
   * the calling coroutine is cancelled.
   *
   * If the flag is already set then this method returns immediately.
   *
   * @see isSet
   * @see set
   */
  suspend fun await() {
    _isSet.filter { it }.first()
  }

  /**
   * Sets the flag encapsulated by this [SuspendingFlag].
   *
   * @see isSet
   * @see await
   */
  fun set() {
    _isSet.value = true
  }
}
