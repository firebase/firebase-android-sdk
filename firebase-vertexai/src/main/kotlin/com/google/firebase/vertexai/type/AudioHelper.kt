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
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

@PublicPreviewAPI
internal class AudioHelper {

  private lateinit var audioRecord: AudioRecord
  private lateinit var audioTrack: AudioTrack
  private var stopRecording: Boolean = false

  internal fun release() {
    stopRecording = true
    if (::audioRecord.isInitialized) {
      audioRecord.stop()
      audioRecord.release()
    }
    if (::audioTrack.isInitialized) {
      audioTrack.stop()
      audioTrack.release()
    }
  }

  internal fun setupAudioTrack() {
    audioTrack =
      AudioTrack(
        AudioManager.STREAM_MUSIC,
        24000,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        AudioTrack.getMinBufferSize(
          24000,
          AudioFormat.CHANNEL_OUT_MONO,
          AudioFormat.ENCODING_PCM_16BIT
        ),
        AudioTrack.MODE_STREAM
      )
    audioTrack.play()
  }

  internal fun playAudio(data: ByteArray) {
    if (!stopRecording) {
      audioTrack.write(data, 0, data.size)
    }
  }

  fun stopRecording() {
    if(::audioRecord.isInitialized && audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
      audioRecord.stop()
    }
  }

  fun start() {
    if (::audioRecord.isInitialized && audioRecord.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
      audioRecord.startRecording()
    }
  }
  @RequiresPermission(Manifest.permission.RECORD_AUDIO)
  fun startRecording(): Flow<ByteArray> {

    val bufferSize =
      AudioRecord.getMinBufferSize(
        16000,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
      )
    if (
      bufferSize == AudioRecord.ERROR ||
        bufferSize == AudioRecord.ERROR_BAD_VALUE ||
        bufferSize <= 0
    ) {
      throw AudioRecordInitializationFailedException(
        "Audio Record buffer size is invalid (${bufferSize})"
      )
    }
    audioRecord =
      AudioRecord(
        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
        16000,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        bufferSize
      )
    if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
      throw AudioRecordInitializationFailedException(
        "Audio Record initialization has failed. State: ${audioRecord.state}"
      )
    }
    if (AcousticEchoCanceler.isAvailable()) {
      val echoCanceler = AcousticEchoCanceler.create(audioRecord.audioSessionId)
      echoCanceler?.enabled = true
    }

    audioRecord.startRecording()

    return flow {
      val buffer = ByteArray(bufferSize)
      while (!stopRecording) {
        if(audioRecord.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
          buffer.fill(0x00)
          continue
        }
        try {
          val bytesRead = audioRecord.read(buffer, 0, buffer.size)
          println(bytesRead)
          if (bytesRead > 0) {
            emit(buffer.copyOf(bytesRead))
          }
        } catch (_: Exception) {}
      }
    }
  }
}
