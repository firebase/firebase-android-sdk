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

package com.google.firebase.sessions

import android.os.SystemClock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Time with accessors for microseconds, milliseconds, and seconds. */
internal data class Time(val ms: Long) {
  val us = ms * 1_000
  val seconds = ms / 1_000
}

/** Time provider interface, for testing purposes. */
internal interface TimeProvider {
  fun elapsedRealtime(): Duration

  fun currentTime(): Time
}

/** "Wall clock" time provider implementation. */
internal object TimeProviderImpl : TimeProvider {
  /**
   * Gets the [Duration] elapsed in "wall clock" time since device boot.
   *
   * This clock is guaranteed to be monotonic, and continues to tick even when the CPU is in power
   * saving modes, so is the recommend basis for general purpose interval timing.
   */
  override fun elapsedRealtime(): Duration = SystemClock.elapsedRealtime().milliseconds

  /**
   * Gets the current "wall clock" time.
   *
   * This clock can be set by the user or the phone network, so the time may jump backwards or
   * forwards unpredictably. This clock should only be used when correspondence with real-world
   * dates and times is important, such as in a calendar or alarm clock application.
   */
  override fun currentTime(): Time = Time(ms = System.currentTimeMillis())
}
