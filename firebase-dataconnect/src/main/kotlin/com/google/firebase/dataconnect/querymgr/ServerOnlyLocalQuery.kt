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

import com.google.firebase.dataconnect.DataConnectPath
import com.google.firebase.dataconnect.DataConnectPathSegment
import com.google.firebase.dataconnect.DataSource
import com.google.firebase.dataconnect.FirebaseDataConnect
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.debug
import com.google.firebase.dataconnect.sqlite.GetEntityIdForPathFunction
import com.google.firebase.dataconnect.util.ImmutableByteArray
import com.google.firebase.dataconnect.util.ProtoUtil.toDataConnectPath
import com.google.firebase.dataconnect.util.SequenceNumberConflatedJobQueue
import com.google.firebase.dataconnect.util.SequencedReference
import google.firebase.dataconnect.proto.ExecuteQueryResponse as ExecuteQueryResponseProto
import google.firebase.dataconnect.proto.GraphqlResponseExtensions.DataConnectProperties as DataConnectPropertiesProto
import java.lang.System.currentTimeMillis
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.modules.SerializersModule

internal class ServerOnlyLocalQuery<Data>(
  private val cacheInfo: LocalQueries.CacheInfo?,
  private val remoteQuery: RemoteQuery,
  private val authUid: String?,
  private val queryId: ImmutableByteArray,
  cpuDispatcher: CoroutineDispatcher,
  coroutineScope: CoroutineScope,
  dataDeserializer: DeserializationStrategy<Data>,
  dataSerializersModule: SerializersModule?,
  logger: Logger,
) : LocalQuery<Data>(cpuDispatcher, dataDeserializer, dataSerializersModule, logger) {

  private val cacheUpdater =
    cacheInfo?.let { CacheUpdater(it, authUid, queryId, cpuDispatcher, coroutineScope, logger) }

  override suspend fun executeImpl(
    requestId: String,
    sequenceNumber: Long,
    authToken: String?,
    appCheckToken: String?,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
  ): SequencedReference<ExecuteImplResult> {
    val remoteQueryExecuteResult =
      remoteQuery.execute(
        requestId = requestId,
        sequenceNumber = sequenceNumber,
        authToken = authToken,
        appCheckToken = appCheckToken,
        callerSdkType = callerSdkType,
      )

    return processRemoteQueryExecuteResult(requestId, remoteQueryExecuteResult)
  }

  private suspend fun processRemoteQueryExecuteResult(
    requestId: String,
    ref: SequencedReference<ExecuteQueryResponseProto>
  ): SequencedReference<ExecuteImplResult> {
    val (sequenceNumber, executeQueryResponse) = ref

    cacheUpdater?.update(sequenceNumber, requestId, executeQueryResponse)

    return SequencedReference(
      sequenceNumber,
      ExecuteImplResult(executeQueryResponse, DataSource.SERVER)
    )
  }

  private class CacheUpdater(
    private val cacheInfo: LocalQueries.CacheInfo,
    private val authUid: String?,
    private val queryId: ImmutableByteArray,
    cpuDispatcher: CoroutineDispatcher,
    coroutineScope: CoroutineScope,
    private val logger: Logger,
  ) {

    suspend fun update(
      sequenceNumber: Long,
      requestId: String,
      executeQueryResponse: ExecuteQueryResponseProto,
    ) {
      jobQueue.execute(sequenceNumber, Params(requestId, executeQueryResponse))
    }

    private data class Params(
      val requestId: String,
      val executeQueryResponse: ExecuteQueryResponseProto,
    )

    private val jobQueue =
      SequenceNumberConflatedJobQueue<Params, Unit>(
        coroutineScope =
          CoroutineScope(
            coroutineScope.coroutineContext +
              SupervisorJob(coroutineScope.coroutineContext[Job]) +
              cpuDispatcher
          )
      ) {
        update(it.requestId, it.executeQueryResponse)
      }

    private suspend fun update(
      requestId: String,
      executeQueryResponse: ExecuteQueryResponseProto,
    ) {
      if (!cacheInfo.initializeJob.isCompleted) {
        logger.debug { "[rid=$requestId] waiting for cache database initialization" }
        cacheInfo.initializeJob.await()
        logger.debug { "[rid=$requestId] waiting for cache database initialization done" }
      }

      logger.debug { "[rid=$requestId] updating query result cache" }
      cacheInfo.db.insertQueryResult(
        authUid = authUid,
        queryId = queryId,
        queryData = executeQueryResponse.data,
        maxAge = cacheInfo.maxAge,
        currentTimeMillis = currentTimeMillis(),
        getEntityIdForPath = executeQueryResponse.getEntityIdForPathFunction(),
      )
    }
  }
}

@JvmName("getEntityIdForPathFunction_ExecuteQueryResponse")
private fun ExecuteQueryResponseProto.getEntityIdForPathFunction(): GetEntityIdForPathFunction? =
  if (extensions.dataConnectCount == 0) {
    null
  } else {
    extensions.dataConnectList.getEntityIdForPathFunction()
  }

@JvmName("getEntityIdForPathFunction_List_DataConnectProperties")
private fun List<DataConnectPropertiesProto>.getEntityIdForPathFunction():
  GetEntityIdForPathFunction? {
  val entityIdByPath: Map<DataConnectPath, String>
  val entityIdsByPath: Map<DataConnectPath, List<String>>

  run {
    val entityIdByPathBuilder = mutableMapOf<DataConnectPath, String>()
    val entityIdsByPathBuilder = mutableMapOf<DataConnectPath, List<String>>()

    this.filter { it.hasPath() && it.path.valuesCount > 0 }
      .filter { it.entityId.isNotEmpty() || it.entityIdsCount > 0 }
      .forEach {
        if (it.entityId.isNotEmpty()) {
          entityIdByPathBuilder[it.path.toDataConnectPath()] = it.entityId
        }
        if (it.entityIdsCount > 0) {
          entityIdsByPathBuilder[it.path.toDataConnectPath()] = it.entityIdsList
        }
      }

    if (entityIdByPathBuilder.isEmpty() && entityIdsByPathBuilder.isEmpty()) {
      return null
    }

    entityIdByPath = entityIdByPathBuilder.toMap()
    entityIdsByPath = entityIdsByPathBuilder.toMap()
  }

  fun getEntityIdForPathFunction(path: DataConnectPath): String? {
    entityIdByPath[path]?.let { entityId ->
      return entityId
    }

    val lastSegment = path.lastOrNull() as? DataConnectPathSegment.ListIndex
    return lastSegment?.index?.let { index ->
      val parentPath = path.dropLast(1)
      val entityIds = entityIdsByPath[parentPath]
      entityIds?.getOrNull(index)
    }
  }

  return ::getEntityIdForPathFunction
}
