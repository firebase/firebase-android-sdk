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
import kotlinx.coroutines.flow.update

/**
 * An implementation of [java.util.concurrent.CountDownLatch] that suspends instead of blocking.
 * @param count the number of times [countDown] must be invoked before threads can pass through
 * [await].
 * @throws IllegalArgumentException if `count` is negative
 */
class SuspendingCountDownLatch(count: Int) {
  init {
    require(count > 0) { "invalid count: $count" }
  }

  private val _count = MutableStateFlow(count)
  val count: Int by _count::value

  /**
   * Causes the current coroutine to suspend until the latch has counted down to zero, unless the
   * coroutine is cancelled.
   *
   * If the current count is zero then this method returns immediately.
   *
   * If the current count is greater than zero then the current coroutine suspends until one of two
   * things happen:
   * 1. The count reaches zero due to invocations of the [countDown] method; or
   * 2. The calling coroutine is cancelled.
   */
  suspend fun await() {
    _count.filter { it == 0 }.first()
  }

  /**
   * Decrements the count of the latch, un-suspending all waiting coroutines if the count reaches
   * zero.
   *
   * If the current count is greater than zero then it is decremented. If the new count is zero then
   * all waiting coroutines are re-enabled for scheduling on their respective dispatchers.
   *
   * @return returns this object, to make it easy to chain it with [await].
   * @throws IllegalStateException if called when the count has already reached zero.
   */
  fun countDown(): SuspendingCountDownLatch {
    _count.update { currentValue ->
      check(currentValue > 0) { "countDown() called too many times (currentValue=$currentValue)" }
      currentValue - 1
    }
    return this
  }
}
