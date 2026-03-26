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
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.debug
import com.google.firebase.dataconnect.core.LoggerGlobals.warn
import com.google.firebase.dataconnect.querymgr.LocalQuery.ExecuteResult
import com.google.firebase.dataconnect.util.ImmutableByteArray
import com.google.firebase.dataconnect.util.ProtoUtil.calculateSha512
import com.google.firebase.dataconnect.util.ProtoUtil.encodeToStruct
import com.google.firebase.dataconnect.util.RequestIdGenerator.nextQueryRequestId
import com.google.firebase.dataconnect.util.SequencedReference.Companion.nextSequenceNumber
import google.firebase.dataconnect.proto.ExecuteQueryRequest as ExecuteQueryRequestProto
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
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
  private val localQueries = LocalQueries(dataConnectGrpcRPCs, cpuDispatcher, coroutineScope)

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
    while (true) {
      when (val result = localQuery.execute(requestId, sequenceNumber, callerSdkType)) {
        ExecuteResult.Retry -> {}
        is ExecuteResult.Success<Data> -> return result.data
      }
    }
  }
}
