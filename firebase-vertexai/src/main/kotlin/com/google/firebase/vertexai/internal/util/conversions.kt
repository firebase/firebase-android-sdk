/*
 * Copyright 2023 Google LLC
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

package com.google.firebase.vertexai.internal.util

import android.util.Base64
import com.google.firebase.vertexai.type.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.json.JSONObject

internal const val BASE_64_FLAGS = Base64.NO_WRAP

internal fun makeMissingCaseException(source: String, ordinal: Int): SerializationException {
  return SerializationException(
    """
    |Missing case for a $source: $ordinal
    |This error indicates that one of the `toInternal` conversions needs updating.
    |If you're a developer seeing this exception, please file an issue on our GitHub repo:
    |https://github.com/firebase/firebase-android-sdk
  """
      .trimMargin()
  )
}

internal fun JSONObject.toInternal() = Json.decodeFromString<JsonObject>(toString())

internal fun JsonObject.toPublic() = JSONObject(toString())
