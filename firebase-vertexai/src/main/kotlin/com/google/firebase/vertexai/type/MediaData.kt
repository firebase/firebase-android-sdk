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

import android.util.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/* Class to represent the media data that needs to be sent to the server. */
public class MediaData(public val mimeType: String, public val data: ByteArray) {
  @Serializable
  internal class Internal(@SerialName("mimeType") val mimeType: String, val data: String)

  internal fun toInternal(): Internal {
    return Internal(mimeType, Base64.encodeToString(data, BASE_64_FLAGS))
  }
}
