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

package com.google.firebase.sessions.testing

import androidx.datastore.core.DataStore
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Fake [DataStore] that can act like an in memory data store, or throw provided exceptions. */
@OptIn(DelicateCoroutinesApi::class)
internal class FakeDataStore<T>(
  private val firstValue: T,
  private val firstThrowable: Throwable? = null,
) : DataStore<T> {
  // The channel is buffered so data can be updated without blocking until collected
  // Default buffer size is 64. This makes unit tests more convenient to write
  private val channel = Channel<() -> T>(Channel.BUFFERED)
  private var value = firstValue

  private var throwOnUpdateData: Throwable? = null

  override val data: Flow<T> = flow {
    // If a first throwable is set, simply throw it
    // This is intended to simulate a failure on init
    if (firstThrowable != null) {
      throw firstThrowable
    }

    // Otherwise, emit the first value
    emit(firstValue)

    // Start receiving values on the channel, and emit them
    // The values are updated by updateData or throwOnNextEmit
    try {
      while (true) {
        // Invoke the lambda in the channel
        // Either emit the value, or throw
        emit(channel.receive().invoke())
      }
    } catch (_: ClosedReceiveChannelException) {
      // Expected when the channel is closed
    }
  }

  override suspend fun updateData(transform: suspend (t: T) -> T): T {
    // Check for a throwable to throw on this call to update data
    val throwable = throwOnUpdateData
    if (throwable != null) {
      // Clear the throwable since it should only throw once
      throwOnUpdateData = null
      throw throwable
    }

    // Apply the transformation and send it to the channel
    val transformedValue = transform(value)
    value = transformedValue
    if (!channel.isClosedForSend) {
      channel.send { transformedValue }
    }

    return transformedValue
  }

  /** Set an exception to throw on the next call to [updateData]. */
  fun throwOnNextUpdateData(throwable: Throwable) {
    throwOnUpdateData = throwable
  }

  /** Set an exception to throw on the next emit. */
  suspend fun throwOnNextEmit(throwable: Throwable) {
    if (!channel.isClosedForSend) {
      channel.send { throw throwable }
    }
  }

  /** Finish the test. */
  fun close() {
    // Close the channel to stop the flow from emitting more values
    // This might be needed if tests fail with UncompletedCoroutinesError
    channel.close()
  }
}
