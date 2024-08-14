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
package com.google.firebase.dataconnect.gradle.plugin

import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

fun toHexString(bytes: ByteArray): String = buildString {
  for (b in bytes) {
    append(String.format("%02x", b))
  }
}

fun Long.toStringWithThousandsSeparator(): String = String.format(Locale.US, "%,d", this)

class Debouncer(val period: Duration) {

  private val lastLogTime = AtomicLong(Long.MIN_VALUE)

  fun debounce(): Boolean {
    val currentTime = System.nanoTime()
    val capturedLastLogTime = lastLogTime.get()
    val timeSinceLastLog = (currentTime - capturedLastLogTime).toDuration(DurationUnit.NANOSECONDS)
    return timeSinceLastLog >= period && lastLogTime.compareAndSet(capturedLastLogTime, currentTime)
  }

  inline fun <T> maybeRun(block: () -> T) {
    if (debounce()) {
      block()
    }
  }
}
