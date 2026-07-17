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

package com.google.firebase.dataconnect.core

import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToLong

/**
 * Calculates exponential backoff durations for connection retries.
 *
 * This class is thread-safe.
 *
 * @param getRandomJitter A function that returns a pseudorandom Double value in the range [-0.5,
 * 0.5) to introduce random jitter into backoff durations. This function must be thread-safe (safe
 * to call concurrently).
 */
internal class RetryBackoffCalculator(private val getRandomJitter: () -> Double) {
  private val nextBackoffMs = AtomicLong(INITIAL_BACKOFF_MS)

  /** Resets the backoff duration to the initial backoff value. */
  fun reset() {
    nextBackoffMs.set(INITIAL_BACKOFF_MS)
  }

  /**
   * Returns the current backoff duration in milliseconds and calculates the next backoff duration
   * by applying the growth multiplier, capped at the maximum backoff limit.
   *
   * @return The backoff duration in milliseconds for the current attempt.
   */
  fun next(): Long {
    while (true) {
      val current = nextBackoffMs.get()
      val next = calculateNextBackoffMs(current)
      if (nextBackoffMs.compareAndSet(current, next)) {
        val jitter = (current * getRandomJitter()).roundToLong()
        return current + jitter
      }
    }
  }

  private companion object {

    const val INITIAL_BACKOFF_MS: Long = 1000L
    const val MAX_BACKOFF_MS: Long = 600_000L
    const val MULTIPLIER: Double = 1.75

    fun calculateNextBackoffMs(currentBackoffMs: Long): Long =
      (currentBackoffMs * MULTIPLIER).roundToLong().coerceAtMost(MAX_BACKOFF_MS)
  }
}
