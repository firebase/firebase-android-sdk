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

/** Util object for "wall clock" time functions. */
internal object WallClock {
  /** Gets the [Duration] elapsed in "wall clock" time since device boot. */
  fun elapsedRealtime(): Duration = SystemClock.elapsedRealtime().milliseconds

  /**
   * Gets the current time in microseconds. The clock can be set by the user or phone network so it
   * is not universally accurate or increasing.
   */
  fun currentTimeUs(): Long {
    return System.currentTimeMillis() * 1000
  }
}
