/*
 * Copyright 2026 Google LLC
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
import com.google.firebase.dataconnect.core.DataConnectGrpcRPCs
import com.google.firebase.dataconnect.util.SequencedReference
import google.firebase.dataconnect.proto.ExecuteQueryRequest as ExecuteQueryRequestProto
import google.firebase.dataconnect.proto.ExecuteQueryResponse
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class RemoteQuery(
  private val dataConnectGrpcRPCs: DataConnectGrpcRPCs,
  private val cpuDispatcher: CoroutineDispatcher,
  val requestProto: ExecuteQueryRequestProto,
  private val coroutineScope: CoroutineScope,
) {
  private val mutex = Mutex()
  private var jobSequencedReference: SequencedReference<Deferred<ExecuteQueryResponse>>? = null
  private var maxEnqueuedSequenceNumber: Long? = null

  suspend fun execute(
    requestId: String,
    sequenceNumber: Long,
    authToken: String?,
    appCheckToken: String?,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
  ): ExecuteResult {
    val jobSequencedReference =
      getOrStartExecuteJob(
        requestId = requestId,
        sequenceNumber = sequenceNumber,
        authToken = authToken,
        appCheckToken = appCheckToken,
        callerSdkType = callerSdkType,
      )

    return if (jobSequencedReference.sequenceNumber < sequenceNumber) {
      jobSequencedReference.ref.join()
      ExecuteResult.Retry
    } else {
      val data = jobSequencedReference.ref.await()
      ExecuteResult.Success(SequencedReference(jobSequencedReference.sequenceNumber, data))
    }
  }

  private suspend fun getOrStartExecuteJob(
    requestId: String,
    sequenceNumber: Long,
    authToken: String?,
    appCheckToken: String?,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
  ): SequencedReference<Deferred<ExecuteQueryResponse>> =
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
        val job: Deferred<ExecuteQueryResponse> =
          coroutineScope.async(cpuDispatcher) {
            dataConnectGrpcRPCs.executeQuery(
              requestId = requestId,
              requestProto = requestProto,
              authToken = authToken,
              appCheckToken = appCheckToken,
              callerSdkType = callerSdkType,
            )
          }

        val jobSequenceNumber =
          checkNotNull(maxEnqueuedSequenceNumber) {
            "internal error gjy6gjyth4: maxEnqueuedSequenceNumber is null, " +
              "but a precondition of this method is that the caller ensures that it is not null"
          }
        check(jobSequenceNumber >= sequenceNumber) {
          "internal error sawv9wj8y4: jobSequenceNumber is $jobSequenceNumber, " +
            "but a precondition of this method is that the caller ensures that it is " +
            "at least the specified sequenceNumber, $sequenceNumber"
        }
        val newJobSequencedReference = SequencedReference(jobSequenceNumber, job)
        this.jobSequencedReference = newJobSequencedReference
        newJobSequencedReference
      }
    }

  sealed interface ExecuteResult {
    data class Success(val response: SequencedReference<ExecuteQueryResponse>) : ExecuteResult
    data object Retry : ExecuteResult
  }
}
