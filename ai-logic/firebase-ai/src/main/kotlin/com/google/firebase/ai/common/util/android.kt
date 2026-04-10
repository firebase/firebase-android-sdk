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

  while (true) {
    if (recordingState != AudioRecord.RECORDSTATE_RECORDING) {
      // Avoid busy looping when not recording
      delay(20)
      continue
    }
    
    // Use non-blocking read to avoid leaking threads if the hardware blocks
    val bytesRead = read(buffer, 0, buffer.size, AudioRecord.READ_NON_BLOCKING)
    
    if (bytesRead > 0) {
      emit(buffer.copyOf(bytesRead))
    } else if (bytesRead == 0) {
      // No data available yet, wait a bit
      delay(10)
    } else {
      // Error read, small delay to avoid tight loop
      delay(50)
    }
  }
}
