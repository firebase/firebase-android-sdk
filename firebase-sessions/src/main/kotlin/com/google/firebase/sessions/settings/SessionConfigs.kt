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

package com.google.firebase.sessions.settings

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Session configs data for caching. */
@Serializable
internal data class SessionConfigs(
  val sessionsEnabled: Boolean?,
  val sessionSamplingRate: Double?,
  val sessionTimeoutSeconds: Int?,
  val cacheDurationSeconds: Int?,
  val cacheUpdatedTimeMs: Long?,
)

/** DataStore json [Serializer] for [SessionConfigs]. */
internal object SessionConfigsSerializer : Serializer<SessionConfigs> {
  override val defaultValue =
    SessionConfigs(
      sessionsEnabled = null,
      sessionSamplingRate = null,
      sessionTimeoutSeconds = null,
      cacheDurationSeconds = null,
      cacheUpdatedTimeMs = null,
    )

  override suspend fun readFrom(input: InputStream): SessionConfigs =
    try {
      Json.decodeFromString<SessionConfigs>(input.readBytes().decodeToString())
    } catch (ex: Exception) {
      throw CorruptionException("Cannot parse session configs", ex)
    }

  override suspend fun writeTo(t: SessionConfigs, output: OutputStream) {
    @Suppress("BlockingMethodInNonBlockingContext") // blockingDispatcher is safe for blocking calls
    output.write(Json.encodeToString(SessionConfigs.serializer(), t).encodeToByteArray())
  }
}
