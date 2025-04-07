/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.sessions.testing

import com.google.firebase.sessions.TimeProvider
import com.google.firebase.sessions.testing.TestSessionEventData.TEST_SESSION_TIMESTAMP_US
import kotlin.time.Duration
import kotlin.time.DurationUnit

/**
 * Fake [TimeProvider] that allows programmatically elapsing time forward.
 *
 * Default [elapsedRealtime] is [Duration.ZERO] until the time is moved using [addInterval].
 */
class FakeTimeProvider(private val initialTimeUs: Long = TEST_SESSION_TIMESTAMP_US) : TimeProvider {
  private var elapsed = Duration.ZERO

  fun addInterval(interval: Duration) {
    if (interval.isNegative()) {
      throw IllegalArgumentException("Cannot add a negative duration to elapsed time.")
    }
    elapsed += interval
  }

  override fun elapsedRealtime(): Duration = elapsed

  override fun currentTimeUs(): Long = initialTimeUs + elapsed.toLong(DurationUnit.MICROSECONDS)
}
