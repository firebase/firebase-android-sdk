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

import com.google.firebase.dataconnect.FirebaseDataConnect
import com.google.firebase.dataconnect.QueryRef
import com.google.firebase.dataconnect.core.DataConnectGrpcRPCs
import com.google.firebase.dataconnect.util.DeserializeUtils.deserialize
import com.google.firebase.dataconnect.util.SequencedReference
import google.firebase.dataconnect.proto.ExecuteQueryRequest as ExecuteQueryRequestProto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.modules.SerializersModule

internal class LocalQuery<Data>(
  private val dataConnectGrpcRPCs: DataConnectGrpcRPCs,
  private val cpuDispatcher: CoroutineDispatcher,
  private val requestProto: ExecuteQueryRequestProto,
  private val dataDeserializer: DeserializationStrategy<Data>,
  private val dataSerializersModule: SerializersModule?,
  private val fetchPolicy: QueryRef.FetchPolicy,
  private val coroutineScope: CoroutineScope,
) {
  private val mutex = Mutex()
  private var jobSequencedReference: SequencedReference<Deferred<Data>>? = null
  private var maxEnqueuedSequenceNumber: Long? = null

  suspend fun execute(
    requestId: String,
    sequenceNumber: Long,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
  ): ExecuteResult<Data> {
    val jobSequencedReference = getOrStartExecuteJob(requestId, sequenceNumber, callerSdkType)
    return if (jobSequencedReference.sequenceNumber < sequenceNumber) {
      jobSequencedReference.ref.join()
      ExecuteResult.Retry
    } else {
      val data = jobSequencedReference.ref.await()
      ExecuteResult.Success(data)
    }
  }

  private suspend fun getOrStartExecuteJob(
    requestId: String,
    sequenceNumber: Long,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
  ): SequencedReference<Deferred<Data>> =
    mutex.withLock {
      maxEnqueuedSequenceNumber =
        when (val currentValue = maxEnqueuedSequenceNumber) {
          null -> sequenceNumber
          else -> currentValue.coerceAtLeast(sequenceNumber)
        }

      val jobSequencedReference = this.jobSequencedReference

      if (
        jobSequencedReference !== null &&
          (jobSequencedReference.sequenceNumber >= sequenceNumber ||
            !jobSequencedReference.ref.isCompleted)
      ) {
        jobSequencedReference
      } else {
        val job =
          coroutineScope.async(cpuDispatcher) {
            val response =
              dataConnectGrpcRPCs.executeQuery(
                requestId = requestId,
                requestProto = requestProto,
                authToken = null,
                appCheckToken = null,
                callerSdkType = callerSdkType,
              )
            response.deserialize(dataDeserializer, dataSerializersModule)
          }

        val jobSequenceNumber =
          checkNotNull(maxEnqueuedSequenceNumber) {
            "internal error e47am2ys5n: maxEnqueuedSequenceNumber is null, " +
              "but a precondition of this method is that the caller ensures that it is not null"
          }
        check(jobSequenceNumber >= sequenceNumber) {
          "internal error e6d68zvgmz: jobSequenceNumber is $jobSequenceNumber, " +
            "but a precondition of this method is that the caller ensures that it is " +
            "at least the specified sequenceNumber, $sequenceNumber"
        }
        val newJobSequencedReference = SequencedReference(jobSequenceNumber, job)
        this.jobSequencedReference = newJobSequencedReference
        newJobSequencedReference
      }
    }

  sealed interface ExecuteResult<out T> {
    data class Success<T>(val data: T) : ExecuteResult<T>
    data object Retry : ExecuteResult<Nothing>
  }
}
