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

@file:OptIn(PublicPreviewAPI::class)

package com.google.firebase.ai.type

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Represents a single message in a live, bidirectional generate content stream.
 *
 * See the [API reference](https://ai.google.dev/api/live#bidigeneratecontentrealtimeinput) for more
 * details.
 *
 * @property audio These form the realtime audio input stream.
 * @property audioStreamEnd Indicates that the audio stream has ended, e.g. because the microphone
 * was turned off. This should only be sent when automatic activity detection is enabled (which is
 * the default). The client can reopen the stream by sending an audio message.
 * @property video These form the realtime video input stream.
 * @property text These form the realtime text input stream.
 * @property activityStart Marks the start of user activity. This can only be sent if automatic
 * (i.e. server-side) activity detection is disabled.
 * @property activityEnd Marks the end of user activity. This can only be sent if automatic (i.e.
 * server-side) activity detection is disabled.
 */
@PublicPreviewAPI
public class BidiGenerateContentRealtimeInput
private constructor(
  public val audio: InlineDataPart?,
  public val audioStreamEnd: Boolean?,
  public val video: InlineDataPart?,
  public val text: String?,
  public val activityStart: ActivityStart?,
  public val activityEnd: ActivityEnd?,
) {

  /** Builder for creating a [BidiGenerateContentRealtimeInput]. */
  public class Builder {
    @JvmField public var audio: InlineDataPart? = null
    @JvmField public var audioStreamEnd: Boolean? = null
    @JvmField public var video: InlineDataPart? = null
    @JvmField public var text: String? = null
    @JvmField public var activityStart: ActivityStart? = null
    @JvmField public var activityEnd: ActivityEnd? = null

    public fun setAudio(audio: InlineDataPart?): Builder = apply { this.audio = audio }
    public fun setAudioStreamEnd(audioStreamEnd: Boolean?): Builder = apply {
      this.audioStreamEnd = audioStreamEnd
    }
    public fun setVideo(video: InlineDataPart?): Builder = apply { this.video = video }
    public fun setText(text: String?): Builder = apply { this.text = text }
    public fun setActivityStart(activityStart: ActivityStart?): Builder = apply {
      this.activityStart = activityStart
    }
    public fun setActivityEnd(activityEnd: ActivityEnd?): Builder = apply {
      this.activityEnd = activityEnd
    }

    public fun build(): BidiGenerateContentRealtimeInput =
      BidiGenerateContentRealtimeInput(
        audio,
        audioStreamEnd,
        video,
        text,
        activityStart,
        activityEnd,
      )
  }

  /** Marks the start of user activity. */
  @PublicPreviewAPI public object ActivityStart

  /** Marks the end of user activity. */
  @PublicPreviewAPI public object ActivityEnd

  @Serializable
  internal data class Internal(
    val audio: InlineDataPart.Internal.InlineData? = null,
    val audioStreamEnd: Boolean? = null,
    val video: InlineDataPart.Internal.InlineData? = null,
    val text: String? = null,
    val activityStart: JsonObject? = null,
    val activityEnd: JsonObject? = null,
  )

  internal fun toInternal(): Internal {
    return Internal(
      audio = audio?.let { (it.toInternal() as InlineDataPart.Internal).inlineData },
      audioStreamEnd = audioStreamEnd,
      video = video?.let { (it.toInternal() as InlineDataPart.Internal).inlineData },
      text = text,
      activityStart = if (activityStart != null) JsonObject(emptyMap()) else null,
      activityEnd = if (activityEnd != null) JsonObject(emptyMap()) else null,
    )
  }

  public companion object {
    /** Returns a new [Builder] for constructing a [BidiGenerateContentRealtimeInput]. */
    @JvmStatic public fun builder(): Builder = Builder()
  }
}

/**
 * DSL for building a [BidiGenerateContentRealtimeInput].
 *
 * Example:
 * ```
 * bidiGenerateContentRealtimeInput {
 *   text = "Hello"
 * }
 * ```
 */
@PublicPreviewAPI
public fun bidiGenerateContentRealtimeInput(
  init: BidiGenerateContentRealtimeInput.Builder.() -> Unit
): BidiGenerateContentRealtimeInput {
  val builder = BidiGenerateContentRealtimeInput.builder()
  builder.init()
  return builder.build()
}
