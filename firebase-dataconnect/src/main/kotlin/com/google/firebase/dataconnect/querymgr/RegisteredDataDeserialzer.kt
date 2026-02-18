/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.dataconnect.querymgr

import com.google.firebase.dataconnect.core.DataConnectGrpcClient.OperationResult
import com.google.firebase.dataconnect.core.DataConnectGrpcClientGlobals.deserialize
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.debug
import com.google.firebase.dataconnect.core.LoggerGlobals.warn
import com.google.firebase.dataconnect.util.NullableReference
import com.google.firebase.dataconnect.util.SequencedReference
import com.google.firebase.dataconnect.util.SequencedReference.Companion.mapSuspending
import com.google.firebase.dataconnect.util.SuspendingLazy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.modules.SerializersModule

internal class RegisteredDataDeserializer<T>(
  val dataDeserializer: DeserializationStrategy<T>,
  val dataSerializersModule: SerializersModule?,
  private val blockingCoroutineDispatcher: CoroutineDispatcher,
  parentLogger: Logger,
) {
  private val logger =
    Logger("RegisteredDataDeserializer").apply {
      debug {
        "created by ${parentLogger.nameWithId} with" +
          " dataDeserializer=$dataDeserializer," +
          " dataSerializersModule=$dataSerializersModule"
      }
    }
  // A flow that emits a value every time that there is an update, either a successful or an
  // unsuccessful update. There is no replay cache in this shared flow because there is no way to
  // atomically emit a new event and ensure that it has a larger sequence number, and we don't want
  // to "replay" an older result. Use `latestUpdate` instead of relying on the replay cache.
  private val updates =
    MutableSharedFlow<SequencedReference<SuspendingLazy<Result<T>>>>(
      replay = 0,
      extraBufferCapacity = Int.MAX_VALUE,
      onBufferOverflow = BufferOverflow.SUSPEND,
    )

  // The latest update (that is, the update with the highest sequence number) that has ever been
  // emitted to `updates`. The `ref` of the value will be null if, and only if, no updates have ever
  // occurred.
  private val latestUpdate =
    MutableStateFlow<NullableReference<SequencedReference<SuspendingLazy<Result<T>>>>>(
      NullableReference(null)
    )

  // The same as `latestUpdate`, except that it only store the latest _successful_ update. That is,
  // if there was a successful update followed by a failed update then the value of this flow would
  // be that successful update, whereas `latestUpdate` would store the failed one.
  //
  // This flow is updated by initializing the lazy value from `latestUpdate`; therefore, make sure
  // to initialize the lazy value from `latestUpdate` before getting this flow's value.
  private val latestSuccessfulUpdate =
    MutableStateFlow<NullableReference<SequencedReference<T>>>(NullableReference(null))

  fun update(requestId: String, sequencedResult: SequencedReference<Result<OperationResult>>) {
    val newUpdate =
      SequencedReference(
        sequencedResult.sequenceNumber,
        lazyDeserialize(requestId, sequencedResult)
      )

    latestUpdate.update { currentUpdate ->
      if (
        currentUpdate.ref !== null &&
          currentUpdate.ref.sequenceNumber > sequencedResult.sequenceNumber
      ) {
        currentUpdate // don't clobber a newer update with an older one
      } else {
        NullableReference(newUpdate)
      }
    }

    // Emit to the `updates` shared flow _after_ setting `latestUpdate` to avoid others missing
    // the latest update.
    val emitSucceeded = updates.tryEmit(newUpdate)
    check(emitSucceeded) { "updates.tryEmit(newUpdate) should have returned true" }
  }

  suspend fun getLatestUpdate(): SequencedReference<Result<T>>? =
    latestUpdate.value.ref?.mapSuspending { it.get() }

  suspend fun getLatestSuccessfulUpdate(): SequencedReference<T>? {
    // Call getLatestUpdate() to populate `latestSuccessfulUpdate` with the most recent update.
    getLatestUpdate()
    return latestSuccessfulUpdate.value.ref
  }

  suspend fun onSuccessfulUpdate(
    sinceSequenceNumber: Long?,
    callback: suspend (SequencedReference<Result<T>>) -> Unit
  ): Nothing {
    var lastSequenceNumber = sinceSequenceNumber ?: Long.MIN_VALUE
    updates
      .onSubscription { latestUpdate.value.ref?.let { emit(it) } }
      .collect { update ->
        if (update.sequenceNumber > lastSequenceNumber) {
          lastSequenceNumber = update.sequenceNumber
          callback(update.mapSuspending { it.get() })
        }
      }
  }

  private fun lazyDeserialize(
    requestId: String,
    sequencedResult: SequencedReference<Result<OperationResult>>
  ): SuspendingLazy<Result<T>> = SuspendingLazy {
    sequencedResult.ref
      .mapCatching {
        withContext(blockingCoroutineDispatcher) {
          it.deserialize(dataDeserializer, dataSerializersModule)
        }
      }
      .onFailure {
        // If the overall result was successful then the failure _must_ have occurred during
        // deserialization. Log the deserialization failure so it doesn't go unnoticed.
        if (sequencedResult.ref.isSuccess) {
          logger.warn(it) { "executeQuery() [rid=$requestId] decoding response data failed: $it" }
        }
      }
      .onSuccess {
        // Update the latest successful update. Set the value in a compare-and-swap loop to ensure
        // that an older result does not clobber a newer one.
        while (true) {
          val latestSuccessful = latestSuccessfulUpdate.value
          if (
            latestSuccessful.ref !== null &&
              sequencedResult.sequenceNumber <= latestSuccessful.ref.sequenceNumber
          ) {
            break
          }
          if (
            latestSuccessfulUpdate.compareAndSet(
              latestSuccessful,
              NullableReference(SequencedReference(sequencedResult.sequenceNumber, it))
            )
          ) {
            break
          }
        }
      }
  }
}
