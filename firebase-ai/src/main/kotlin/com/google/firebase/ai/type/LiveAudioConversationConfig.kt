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

import android.media.AudioRecord
import android.media.AudioTrack

/**
 * Configuration parameters to use for conversation config.
 *
 * @property functionCallHandler A callback that is invoked whenever the model receives a function
 * call. The [FunctionResponsePart] that the callback function returns will be automatically sent to
 * the model.
 *
 * @property transcriptHandler A callback that is invoked whenever the model receives a transcript.
 * The first [Transcription] object is the input transcription, and the second is the output
 * transcription.
 *
 * @property audioHandler A callback that is invoked immediately following the successful
 * initialization of the associated [AudioRecord] and [AudioTrack] objects. This offers a final
 * opportunity to configure these objects, which will remain valid and effective for the duration of
 * the current audio session.
 *
 * @property enableInterruptions If enabled, allows the user to speak over or interrupt the model's
 * ongoing reply.
 *
 * **WARNING**: The user interruption feature relies on device-specific support, and may not be
 * consistently available.
 */
@PublicPreviewAPI
public class LiveAudioConversationConfig
private constructor(
  internal val functionCallHandler: ((FunctionCallPart) -> FunctionResponsePart)?,
  internal val audioHandler: ((AudioRecord, AudioTrack) -> Unit)?,
  internal val transcriptHandler: ((Transcription?, Transcription?) -> Unit)?,
  internal val enableInterruptions: Boolean
) {

  /**
   * Builder for creating a [LiveAudioConversationConfig].
   *
   * Mainly intended for Java interop. Kotlin consumers should use [liveAudioConversationConfig] for
   * a more idiomatic experience.
   *
   * @property functionCallHandler See [LiveAudioConversationConfig.functionCallHandler].
   *
   * @property audioHandler See [LiveAudioConversationConfig.audioHandler].
   *
   * @property transcriptHandler See [LiveAudioConversationConfig.transcriptHandler].
   *
   * @property enableInterruptions See [LiveAudioConversationConfig.enableInterruptions].
   */
  public class Builder {
    @JvmField public var functionCallHandler: ((FunctionCallPart) -> FunctionResponsePart)? = null
    @JvmField public var audioHandler: ((AudioRecord, AudioTrack) -> Unit)? = null
    @JvmField public var transcriptHandler: ((Transcription?, Transcription?) -> Unit)? = null
    @JvmField public var enableInterruptions: Boolean = false

    public fun setFunctionCallHandler(
      functionCallHandler: ((FunctionCallPart) -> FunctionResponsePart)?
    ): Builder = apply { this.functionCallHandler = functionCallHandler }

    public fun setAudioHandler(audioHandler: ((AudioRecord, AudioTrack) -> Unit)?): Builder =
      apply {
        this.audioHandler = audioHandler
      }

    public fun setTranscriptHandler(
      transcriptHandler: ((Transcription?, Transcription?) -> Unit)?
    ): Builder = apply { this.transcriptHandler = transcriptHandler }

    public fun setEnableInterruptions(enableInterruptions: Boolean): Builder = apply {
      this.enableInterruptions = enableInterruptions
    }

    /** Create a new [LiveAudioConversationConfig] with the attached arguments. */
    public fun build(): LiveAudioConversationConfig =
      LiveAudioConversationConfig(
        functionCallHandler = functionCallHandler,
        audioHandler = audioHandler,
        transcriptHandler = transcriptHandler,
        enableInterruptions = enableInterruptions
      )
  }

  public companion object {

    /**
     * Alternative casing for [LiveAudioConversationConfig.Builder]:
     * ```
     * val config = LiveAudioConversationConfig.builder()
     * ```
     */
    public fun builder(): Builder = Builder()
  }
}

/**
 * Helper method to construct a [LiveAudioConversationConfig] in a DSL-like manner.
 *
 * Example Usage:
 * ```
 * liveAudioConversationConfig {
 *   functionCallHandler = ...
 *   audioHandler = ...
 *   ...
 * }
 * ```
 */
@OptIn(PublicPreviewAPI::class)
public fun liveAudioConversationConfig(
  init: LiveAudioConversationConfig.Builder.() -> Unit
): LiveAudioConversationConfig {
  val builder = LiveAudioConversationConfig.builder()
  builder.init()
  return builder.build()
}
