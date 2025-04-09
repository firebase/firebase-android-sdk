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

package com.google.firebase.sessions

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Session data to be persisted. */
@Serializable
internal data class SessionData(val sessionDetails: SessionDetails, val backgroundTime: Time)

/** DataStore json [Serializer] for [SessionData]. */
@Singleton
internal class SessionDataSerializer
@Inject
constructor(
  private val sessionGenerator: SessionGenerator,
  private val timeProvider: TimeProvider,
) : Serializer<SessionData> {
  override val defaultValue: SessionData
    get() =
      SessionData(
        sessionDetails = sessionGenerator.generateNewSession(currentSession = null),
        backgroundTime = timeProvider.currentTime(),
      )

  override suspend fun readFrom(input: InputStream): SessionData =
    try {
      Json.decodeFromString<SessionData>(input.readBytes().decodeToString())
    } catch (ex: Exception) {
      throw CorruptionException("Cannot parse session data", ex)
    }

  override suspend fun writeTo(t: SessionData, output: OutputStream) {
    @Suppress("BlockingMethodInNonBlockingContext") // blockingDispatcher is safe for blocking calls
    output.write(Json.encodeToString(SessionData.serializer(), t).encodeToByteArray())
  }
}
