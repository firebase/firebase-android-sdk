/*
 * Copyright 2025 Google LLC
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

package com.google.firebase.ai.common.util

import android.media.AudioRecord
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.yield

/**
 * The minimum buffer size for this instance.
 *
 * The same as calling [AudioRecord.getMinBufferSize], except the params are pre-populated.
 */
internal val AudioRecord.minBufferSize: Int
  get() = AudioRecord.getMinBufferSize(sampleRate, channelConfiguration, audioFormat)

/**
 * Reads from this [AudioRecord] and returns the data in a flow.
 *
 * Will yield when this instance is not recording.
 */
internal fun AudioRecord.readAsFlow() = flow {
  val buffer = ByteArray(minBufferSize)

  var startTime = System.currentTimeMillis()
  while (true) {
    if (recordingState != AudioRecord.RECORDSTATE_RECORDING) {
      delay(10)
      yield()
      continue
    }
    if (System.currentTimeMillis() - startTime >= 100) {
      // This is the manual yield/pause point.
      // Using delay(1) suspends the coroutine, freeing the thread
      // for the dispatcher to run other tasks briefly.
      delay(1)
      yield()
      startTime = System.currentTimeMillis() // Reset the timer
    }

    val bytesRead = read(buffer, 0, buffer.size)
    if (bytesRead > 0) {
      emit(buffer.copyOf(bytesRead))
    }
    yield()
  }
}
