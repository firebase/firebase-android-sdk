/*
 * Copyright 2026 Google LLC
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

import kotlinx.serialization.Serializable

/**
 * Structured speech metadata associated with a [TextPart].
 *
 * Used to guide speech generation for a specific piece of text, such as identifying which speaker
 * says it and the vocal style they should use.
 *
 * @property speaker Identifies which speaker is speaking this turn. When using a
 * [MultiSpeakerVoiceConfig], this should match the [SpeakerVoiceConfig.speaker] of one of the
 * configured speakers.
 * @property style A natural language description of the vocal style (such as `"cheerful"`).
 */
@PublicPreviewAPI
public class SpeechMetadata
@JvmOverloads
constructor(
  public val speaker: String? = null,
  public val style: String? = null,
) {
  internal fun toInternal() = SpeechMetadataInternal(speaker = speaker, style = style)
}

@Serializable
internal data class SpeechMetadataInternal(
  val speaker: String? = null,
  val style: String? = null,
) {
  @OptIn(PublicPreviewAPI::class)
  internal fun toPublic() = SpeechMetadata(speaker = speaker, style = style)
}
