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

package com.google.firebase.vertexai.type

import android.Manifest
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow

@PublicPreviewAPI
internal class AudioHelper(
  // Record for recording the user's mic
  private val recorder: AudioRecord,
  // Track for playing back what the model says
  private val playbackTrack: AudioTrack,
) {
  private var released: Boolean = false

  fun release() {
    if (released) return
    released = true

    recorder.release()
    playbackTrack.release()
  }

  fun playAudio(data: ByteArray) {
    if (released) return

    playbackTrack.write(data, 0, data.size)
  }

  fun pauseRecording() {
    if (released || recorder.state == AudioRecord.RECORDSTATE_STOPPED) return

    recorder.stop()
  }

  fun resumeRecording() {
    if (released || recorder.state == AudioRecord.RECORDSTATE_RECORDING) return

    recorder.startRecording()
  }

  fun listenToRecording(): Flow<ByteArray> {
    if (released) return emptyFlow()

    resumeRecording()

    return recorder.readAsFlow()
  }

  companion object {
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun Build(): AudioHelper {
      val playbackTrack =
        AudioTrack(
          AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).build(),
          AudioFormat.Builder()
            .setSampleRate(24000)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build(),
          AudioTrack.getMinBufferSize(
            24000,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
          ),
          AudioTrack.MODE_STREAM,
          AudioManager.AUDIO_SESSION_ID_GENERATE
        )

      playbackTrack.play()

      val bufferSize =
        AudioRecord.getMinBufferSize(
          16000,
          AudioFormat.CHANNEL_IN_MONO,
          AudioFormat.ENCODING_PCM_16BIT
        )

      if (bufferSize <= 0)
        throw AudioRecordInitializationFailedException(
          "Audio Record buffer size is invalid ($bufferSize)"
        )

      val recorder =
        AudioRecord(
          MediaRecorder.AudioSource.VOICE_COMMUNICATION,
          16000,
          AudioFormat.CHANNEL_IN_MONO,
          AudioFormat.ENCODING_PCM_16BIT,
          bufferSize
        )
      if (recorder.state != AudioRecord.STATE_INITIALIZED)
        throw AudioRecordInitializationFailedException(
          "Audio Record initialization has failed. State: ${recorder.state}"
        )

      if (AcousticEchoCanceler.isAvailable()) {
        AcousticEchoCanceler.create(recorder.audioSessionId)?.enabled = true
      }

      return AudioHelper(recorder, playbackTrack)
    }
  }
}

internal val AudioRecord.minBufferSize: Int
  get() = AudioRecord.getMinBufferSize(sampleRate, channelConfiguration, audioFormat)

internal fun AudioRecord.readAsFlow() = flow {
  val buffer = ByteArray(minBufferSize)

  while (true) {
    if (recordingState != AudioRecord.RECORDSTATE_RECORDING) {
      delay(1)
      continue
    }

    val bytesRead = read(buffer, 0, buffer.size)
    if (bytesRead > 0) {
      emit(buffer.copyOf(bytesRead))
    } else {
      delay(1)
    }
  }
}
