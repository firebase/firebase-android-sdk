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

import kotlinx.serialization.json.JsonObject

/**
 * The audio transcription configuration.
 * @property enable If true, the server will use Gemini to transcribe the audio.
 * @property prefixPrompt Prefix prompt for the audio transcription op. This is useful to override
 * the default prefix prompt that only asks the model to transcribe the audio. Overriding can be
 * useful to provide additional context to the model such as what language is expected to be spoken
 * in the audio.
 */
public class AudioTranscriptionConfig(
  internal val enable: Boolean? = null,
  internal val prefixPrompt: String? = null
) {

  internal fun toInternal() = JsonObject(emptyMap())
}
