package com.google.firebase.vertexai.type

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

internal class AudioHelper {

  private lateinit var audioRecord: AudioRecord
  private lateinit var audioTrack: AudioTrack
  private var stopRecording: Boolean = false
  private val RECORDER_SAMPLE_RATE = 16000 // Adjust based on server settings
  private val RECORDER_CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
  private val RECORDER_AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

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
    val sampleRate = 24000 // Adjust based on server settings
    val channelConfig = AudioFormat.CHANNEL_OUT_MONO
    val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
    audioTrack =
      AudioTrack(
        AudioManager.STREAM_MUSIC,
        sampleRate,
        channelConfig,
        audioFormat,
        minBufferSize,
        AudioTrack.MODE_STREAM
      )
    audioTrack.play()
  }

  internal fun playAudio(data: ByteArray) {
    if (!stopRecording) {
      audioTrack.write(data, 0, data.size)
    }
  }

  suspend fun startRecording(): Flow<ByteArray> {
    val bufferSize =
      AudioRecord.getMinBufferSize(
        RECORDER_SAMPLE_RATE,
        RECORDER_CHANNEL_CONFIG,
        RECORDER_AUDIO_FORMAT
      )
    if (
      bufferSize == AudioRecord.ERROR ||
        bufferSize == AudioRecord.ERROR_BAD_VALUE ||
        bufferSize <= 0
    ) {
      println("Invalid buffer size: $bufferSize")
    }
    audioRecord =
      AudioRecord(
        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
        RECORDER_SAMPLE_RATE,
        RECORDER_CHANNEL_CONFIG,
        RECORDER_AUDIO_FORMAT,
        bufferSize
      )
    if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
      println("AudioRecord initialization failed.")
    }
    if (AcousticEchoCanceler.isAvailable()) {
      val echoCanceler = AcousticEchoCanceler.create(audioRecord.audioSessionId)
      echoCanceler?.enabled = true
    }

    audioRecord.startRecording()

    return flow {
      while (true) {
        if (stopRecording) {
          break
        }
        val buffer = ByteArray(bufferSize / 2)
        val bytesRead = audioRecord.read(buffer, 0, buffer.size)
        if (bytesRead > 0) {
          emit(buffer.copyOf(bytesRead))
        }
      }
    }
  }
}
