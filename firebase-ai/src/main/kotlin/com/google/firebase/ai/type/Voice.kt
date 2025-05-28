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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Various voices supported by the server. The list of all voices can be found
 * [here](https://cloud.google.com/text-to-speech/docs/chirp3-hd)
 */
@PublicPreviewAPI
public class Voice public constructor(public val voiceName: String) {

  @Serializable internal data class Internal(@SerialName("voice_name") val voiceName: String)

  internal fun toInternal(): Internal {
    return Internal(this.voiceName)
  }
}
