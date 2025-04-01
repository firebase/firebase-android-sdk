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

package com.google.firebase.sessions

import android.util.Log
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import com.google.firebase.Firebase
import com.google.firebase.annotations.concurrent.Background
import com.google.firebase.app
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Data for sessions information */
@Serializable internal data class SessionData(val sessionId: String?)

/** DataStore json [Serializer] for [SessionData]. */
internal object SessionDataSerializer : Serializer<SessionData> {
  override val defaultValue = SessionData(sessionId = null)

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

/** Handles reading to and writing from the [DataStore]. */
internal interface SessionDatastore {
  /** Stores a new session ID value in the [DataStore] */
  fun updateSessionId(sessionId: String)

  /**
   * Gets the currently stored session ID from the [DataStore]. This will be null if no session has
   * been stored previously.
   */
  fun getCurrentSessionId(): String?

  companion object {
    val instance: SessionDatastore
      get() = Firebase.app[FirebaseSessionsComponent::class.java].sessionDatastore
  }
}

@Singleton
internal class SessionDatastoreImpl
@Inject
constructor(
  @Background private val backgroundDispatcher: CoroutineContext,
  private val sessionDataStore: DataStore<SessionData>,
) : SessionDatastore {

  /** Most recent session from datastore is updated asynchronously whenever it changes */
  private val currentSessionFromDatastore = AtomicReference<SessionData>()

  private val firebaseSessionDataFlow: Flow<SessionData> =
    sessionDataStore.data.catch { ex ->
      Log.e(TAG, "Error reading stored session data.", ex)
      emit(SessionDataSerializer.defaultValue)
    }

  init {
    CoroutineScope(backgroundDispatcher).launch {
      firebaseSessionDataFlow.collect { currentSessionFromDatastore.set(it) }
    }
  }

  override fun updateSessionId(sessionId: String) {
    CoroutineScope(backgroundDispatcher).launch {
      try {
        sessionDataStore.updateData { SessionData(sessionId) }
      } catch (ex: IOException) {
        Log.w(TAG, "Failed to update session Id", ex)
      }
    }
  }

  override fun getCurrentSessionId(): String? = currentSessionFromDatastore.get()?.sessionId

  private companion object {
    private const val TAG = "FirebaseSessionsRepo"
  }
}
