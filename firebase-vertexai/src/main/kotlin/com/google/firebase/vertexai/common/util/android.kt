package com.google.firebase.vertexai.common.util

import android.media.AudioRecord
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
 * Will emit a zeroed out buffer when this instance is not recording.
 */
internal fun AudioRecord.readAsFlow() = flow {
  val buffer = ByteArray(minBufferSize)
  val emptyBuffer = ByteArray(minBufferSize)

  while (true) {
    if (recordingState != AudioRecord.RECORDSTATE_RECORDING) {
      yield()
      continue
    }

    val bytesRead = read(buffer, 0, buffer.size)
    if (bytesRead > 0) {
      emit(buffer.copyOf(bytesRead))
    } else {
      emit(emptyBuffer)
    }
  }
}
