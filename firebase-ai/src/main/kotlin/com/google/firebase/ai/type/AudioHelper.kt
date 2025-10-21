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

package com.google.firebase.ai.type

import android.Manifest
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.util.Log
import androidx.annotation.RequiresPermission
import com.google.firebase.ai.common.util.readAsFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Helper class for recording audio and playing back a separate audio track at the same time.
 *
 * @see AudioHelper.build
 * @see LiveSession.startAudioConversation
 */
@PublicPreviewAPI
internal class AudioHelper(
  /** Record for recording the System microphone. */
  private val recorder: AudioRecord,
  /** Track for playing back what the model says. */
  private val playbackTrack: AudioTrack,
) {
  private var released: Boolean = false

  /**
   * Release the system resources on the recorder and playback track.
   *
   * Once an [AudioHelper] has been "released", it can _not_ be used again.
   *
   * This method can safely be called multiple times, as it won't do anything if this instance has
   * already been released.
   */
  fun release() {
    if (released) return
    released = true

    recorder.release()
    playbackTrack.release()
  }

  /**
   * Play the provided audio data on the playback track.
   *
   * Does nothing if this [AudioHelper] has been [released][release].
   *
   * @throws IllegalStateException If the playback track was not properly initialized.
   * @throws IllegalArgumentException If the playback data is invalid.
   * @throws RuntimeException If we fail to play the audio data for some unknown reason.
   */
  fun playAudio(data: ByteArray) {
    if (released) return
    if (data.isEmpty()) return

    if (playbackTrack.playState == AudioTrack.PLAYSTATE_STOPPED) playbackTrack.play()

    val result = playbackTrack.write(data, 0, data.size)
    if (result > 0) return
    if (result == 0) {
      Log.w(
        TAG,
        "Failed to write any audio bytes to the playback track. The audio track may have been stopped or paused."
      )
      return
    }

    // ERROR_INVALID_OPERATION and ERROR_BAD_VALUE should never occur
    when (result) {
      AudioTrack.ERROR_INVALID_OPERATION ->
        throw IllegalStateException("The playback track was not properly initialized.")
      AudioTrack.ERROR_BAD_VALUE ->
        throw IllegalArgumentException("Playback data is somehow invalid.")
      AudioTrack.ERROR_DEAD_OBJECT -> {
        Log.w(TAG, "Attempted to playback some audio, but the track has been released.")
        release() // to ensure `released` is set and `record` is released too
      }
      AudioTrack.ERROR ->
        throw RuntimeException("Failed to play the audio data for some unknown reason.")
    }
  }

  /**
   * Pause the recording of the microphone, if it's recording.
   *
   * Does nothing if this [AudioHelper] has been [released][release].
   *
   * @see resumeRecording
   *
   * @throws IllegalStateException If the playback track was not properly initialized.
   */
  fun pauseRecording() {
    if (released || recorder.recordingState == AudioRecord.RECORDSTATE_STOPPED) return

    try {
      recorder.stop()
    } catch (e: IllegalStateException) {
      release()
      throw IllegalStateException("The playback track was not properly initialized.")
    }
  }

  /**
   * Resumes the recording of the microphone, if it's not already running.
   *
   * Does nothing if this [AudioHelper] has been [released][release].
   *
   * @see pauseRecording
   */
  fun resumeRecording() {
    if (released || recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) return

    recorder.startRecording()
  }

  /**
   * Start perpetually recording the system microphone, and return the bytes read in a flow.
   *
   * Returns an empty flow if this [AudioHelper] has been [released][release].
   */
  fun listenToRecording(): Flow<ByteArray> {
    if (released) return emptyFlow()
    resumeRecording()

    return recorder.readAsFlow()
  }

  companion object {
    private val TAG = AudioHelper::class.simpleName

    /**
     * Creates an instance of [AudioHelper] with the track and record initialized.
     *
     * A separate build method is necessary so that we can properly propagate the required manifest
     * permission, and throw exceptions when needed.
     *
     * It also makes it easier to read, since the long initialization is separate from the
     * constructor.
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun build(audioHandler: ((AudioRecord, AudioTrack) -> Unit)? = null): AudioHelper {
      val playbackTrack =
        AudioTrack(
          AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build(),
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

      if (audioHandler != null) {
        audioHandler(recorder, playbackTrack)
      }
      return AudioHelper(recorder, playbackTrack)
    }
  }
}
