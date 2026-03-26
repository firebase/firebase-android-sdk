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
import com.google.firebase.dataconnect.core.DataConnectGrpcClientGlobals.deserialize
import com.google.firebase.dataconnect.core.DataConnectGrpcRPCs
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.debug
import com.google.firebase.dataconnect.core.LoggerGlobals.warn
import com.google.firebase.dataconnect.util.DeserializeUtils.deserialize
import com.google.firebase.dataconnect.util.ImmutableByteArray
import com.google.firebase.dataconnect.util.ProtoUtil.calculateSha512
import com.google.firebase.dataconnect.util.ProtoUtil.encodeToStruct
import com.google.firebase.dataconnect.util.RequestIdGenerator.nextQueryRequestId
import com.google.firebase.dataconnect.util.SequencedReference
import com.google.firebase.dataconnect.util.SequencedReference.Companion.nextSequenceNumber
import google.firebase.dataconnect.proto.ExecuteQueryRequest as ExecuteQueryRequestProto
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.SerializersModule

internal class QueryManager(
  private val requestName: String,
  private val dataConnectGrpcRPCs: DataConnectGrpcRPCs,
  private val ioDispatcher: CoroutineDispatcher,
  private val cpuDispatcher: CoroutineDispatcher,
  private val secureRandom: Random,
  private val logger: Logger,
) {

  private val coroutineScope =
    CoroutineScope(
      SupervisorJob() +
        CoroutineName(logger.nameWithId) +
        CoroutineExceptionHandler { context, throwable ->
          logger.warn(throwable) {
            "uncaught exception from a coroutine named ${context[CoroutineName]}: " +
              "$throwable [emhpq6ag2r]"
          }
        }
    )

  private val mutex = Mutex()
  private val localQueries = LocalQueries()

  suspend fun close() {
    coroutineScope.cancel("close() called")
    coroutineScope.coroutineContext.job.join()
  }

  suspend fun <Data, Variables> execute(
    operationName: String,
    variables: Variables,
    dataDeserializer: DeserializationStrategy<Data>,
    variablesSerializer: SerializationStrategy<Variables>,
    dataSerializersModule: SerializersModule?,
    variablesSerializersModule: SerializersModule?,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
    fetchPolicy: QueryRef.FetchPolicy,
  ): Data {
    val sequenceNumber = nextSequenceNumber()
    val requestId = withContext(ioDispatcher) { secureRandom.nextQueryRequestId() }
    logger.debug { "[rid=$requestId] Executing query with operationName=$operationName" }

    val requestProto: ExecuteQueryRequestProto
    val queryId: ImmutableByteArray
    withContext(cpuDispatcher) {
      val variablesStruct =
        encodeToStruct(variables, variablesSerializer, variablesSerializersModule)
      queryId = variablesStruct.calculateSha512(preamble = operationName)
      requestProto =
        ExecuteQueryRequestProto.newBuilder()
          .setName(requestName)
          .setOperationName(operationName)
          .setVariables(variablesStruct)
          .build()
    }

    val localKey = LocalQueries.Key(queryId, dataDeserializer, dataSerializersModule, fetchPolicy)
    return execute(requestId, sequenceNumber, localKey, requestProto, callerSdkType)
  }

  private suspend fun <Data> execute(
    requestId: String,
    sequenceNumber: Long,
    localKey: LocalQueries.Key<Data>,
    requestProto: ExecuteQueryRequestProto,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
  ): Data {
    val localQuery: LocalQuery<Data> =
      mutex.withLock { localQueries.getOrPut(localKey, requestProto) }

    localQuery.mutex.withLock { localQuery.enqueueSequenceNumber(sequenceNumber) }

    while (true) {
      when (val result = execute(requestId, sequenceNumber, localQuery, callerSdkType)) {
        ExecuteResult.Retry -> {}
        is ExecuteResult.Success<Data> -> return result.data
      }
    }
  }

  private suspend fun <Data> execute(
    requestId: String,
    sequenceNumber: Long,
    localQuery: LocalQuery<Data>,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
  ): ExecuteResult<Data> {
    val jobRef: SequencedReference<Deferred<Data>> =
      localQuery.mutex.withLock {
        val jobRef = localQuery.job

        if (
          jobRef !== null && (jobRef.sequenceNumber >= sequenceNumber || !jobRef.ref.isCompleted)
        ) {
          jobRef
        } else {
          val job =
            coroutineScope.async(cpuDispatcher) {
              val response =
                dataConnectGrpcRPCs.executeQuery(
                  requestId = requestId,
                  requestProto = localQuery.requestProto,
                  authToken = null,
                  appCheckToken = null,
                  callerSdkType = callerSdkType,
                )
              response.deserialize(localQuery.dataDeserializer, localQuery.dataSerializersModule)
            }

          val jobSequenceNumber =
            checkNotNull(localQuery.maxEnqueuedSequenceNumber) {
              "internal error e47am2ys5n: localQuery.maxEnqueuedSequenceNumber is null, " +
                "but a precondition of this method is that the caller ensures that it is not null"
            }
          check(jobSequenceNumber >= sequenceNumber) {
            "internal error e6d68zvgmz: jobSequenceNumber is $jobSequenceNumber, " +
              "but a precondition of this method is that the caller ensures that it is " +
              "at least the specified sequenceNumber, $sequenceNumber"
          }
          val newJobRef = SequencedReference(jobSequenceNumber, job)
          localQuery.job = newJobRef
          newJobRef
        }
      }

    return if (jobRef.sequenceNumber < sequenceNumber) {
      jobRef.ref.join()
      ExecuteResult.Retry
    } else {
      val data = jobRef.ref.await()
      ExecuteResult.Success(data)
    }
  }

  private sealed interface ExecuteResult<out T> {
    data class Success<T>(val data: T) : ExecuteResult<T>
    data object Retry : ExecuteResult<Nothing>
  }
}

private class LocalQuery<Data>(
  val requestProto: ExecuteQueryRequestProto,
  val dataDeserializer: DeserializationStrategy<Data>,
  val dataSerializersModule: SerializersModule?,
  val fetchPolicy: QueryRef.FetchPolicy,
) {
  val mutex = Mutex()
  var job: SequencedReference<Deferred<Data>>? = null
  var maxEnqueuedSequenceNumber: Long? = null
}

private fun LocalQuery<*>.enqueueSequenceNumber(sequenceNumber: Long) {
  maxEnqueuedSequenceNumber =
    when (val currentValue = maxEnqueuedSequenceNumber) {
      null -> sequenceNumber
      else -> currentValue.coerceAtLeast(sequenceNumber)
    }
}

private class LocalQueries {

  private val map = mutableMapOf<Key<*>, LocalQuery<*>>()

  fun <T> getOrPut(
    key: Key<T>,
    requestProto: ExecuteQueryRequestProto,
  ): LocalQuery<T> {
    val localQuery: LocalQuery<*> =
      map.getOrPut(key) {
        LocalQuery(requestProto, key.dataDeserializer, key.dataSerializersModule, key.fetchPolicy)
      }

    @Suppress("UNCHECKED_CAST") return localQuery as LocalQuery<T>
  }

  data class Key<Data>(
    val queryId: ImmutableByteArray,
    val dataDeserializer: DeserializationStrategy<Data>,
    val dataSerializersModule: SerializersModule?,
    val fetchPolicy: QueryRef.FetchPolicy,
  )
}
