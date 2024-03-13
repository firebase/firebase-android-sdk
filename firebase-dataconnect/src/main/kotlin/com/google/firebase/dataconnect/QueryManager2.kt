// Copyright 2024 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.firebase.dataconnect

import com.google.firebase.util.nextAlphanumericString
import com.google.protobuf.Struct
import java.util.Objects
import kotlin.coroutines.*
import kotlin.random.Random
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.*
import kotlinx.serialization.DeserializationStrategy

internal class QueryManager2(val dataConnect: FirebaseDataConnect) {

  private val logger =
    Logger("QueryManager2").apply { debug { "Created by ${dataConnect.logger.nameWithId}" } }

  private val activeQueries = ActiveQueries(dataConnect, parentLogger = logger)

  suspend fun <Data, Variables> execute(
    query: QueryRef<Data, Variables>
  ): DataConnectQueryResult<Data, Variables> {
    require(query.dataConnect === dataConnect) {
      "The given query belongs to a different FirebaseDataConnect; " +
        "query belongs to ${query.dataConnect}, but expected ${dataConnect}"
    }

    val key = ActiveQueryKey.forQueryRef(query)
    val result = activeQueries.withAcquiredValue(key) { it.execute(query.dataDeserializer) }

    return when (result) {
      is ActiveQueryResult.Success -> result.deserializedData().toDataConnectQueryResult(query)
      is ActiveQueryResult.Failure -> throw result.queryExecutorResult.exception
    }
  }
}

private class ActiveQueryKey(val operationName: String, val variables: Struct) {

  private val variablesHash: String = variables.calculateSha512().toAlphaNumericString()

  override fun equals(other: Any?) =
    other is ActiveQueryKey &&
      other.operationName == operationName &&
      other.variablesHash == variablesHash

  override fun hashCode() = Objects.hash(operationName, variablesHash)

  override fun toString() =
    "ActiveQueryKey(" +
      "operationName=$operationName, " +
      "variables=${variables.toCompactString()})"

  companion object {
    fun <Data, Variables> forQueryRef(query: QueryRef<Data, Variables>): ActiveQueryKey {
      val variablesStruct = encodeToStruct(query.variablesSerializer, query.variables)
      return ActiveQueryKey(operationName = query.operationName, variables = variablesStruct)
    }
  }
}

private class ActiveQueries(val dataConnect: FirebaseDataConnect, parentLogger: Logger) :
  ReferenceCountedSet<ActiveQueryKey, ActiveQuery>() {

  private val logger =
    Logger("ActiveQueries").apply { debug { "Created by ${parentLogger.nameWithId}" } }

  override fun valueForKey(key: ActiveQueryKey) =
    ActiveQuery(
      dataConnect = dataConnect,
      operationName = key.operationName,
      variables = key.variables,
      parentLogger = logger,
    )

  override fun onAllocate(entry: Entry<ActiveQueryKey, ActiveQuery>) {
    logger.debug(
      "Allocated ${entry.value.logger.nameWithId} (" +
        "operationName=${entry.key.operationName}, " +
        "variables=${entry.key.variables.toCompactString()})"
    )
  }

  override fun onFree(entry: Entry<ActiveQueryKey, ActiveQuery>) {
    logger.debug("Deallocated ${entry.value.logger.nameWithId}")
  }
}

private class ActiveQuery(
  val dataConnect: FirebaseDataConnect,
  val operationName: String,
  val variables: Struct,
  parentLogger: Logger
) {
  val logger = Logger("ActiveQuery").apply { debug { "Created by ${parentLogger.nameWithId}" } }

  val queryExecutor = QueryExecutor(dataConnect, operationName, variables)

  private val typedActiveQueries = TypedActiveQueries(this, logger)

  suspend fun <Data> execute(
    dataDeserializer: DeserializationStrategy<Data>
  ): ActiveQueryResult<Data> {
    val key = TypedActiveQueryKey(dataDeserializer)
    return typedActiveQueries.withAcquiredValue(key) {
      @Suppress("UNCHECKED_CAST") (it as TypedActiveQuery<Data>).execute()
    }
  }
}

private class TypedActiveQuery<Data>(
  val activeQuery: ActiveQuery,
  val dataDeserializer: DeserializationStrategy<Data>,
  val logger: Logger
) {
  suspend fun execute(): ActiveQueryResult<Data> =
    when (val result = activeQuery.queryExecutor.execute()) {
      is QueryExecutorResult.Success ->
        ActiveQueryResult.Success(activeQuery, dataDeserializer, result, logger)
      is QueryExecutorResult.Failure ->
        ActiveQueryResult.Failure(activeQuery, dataDeserializer, result)
    }
}

private class TypedActiveQueryKey<Data>(val dataDeserializer: DeserializationStrategy<Data>) {
  override fun equals(other: Any?) =
    other is TypedActiveQueryKey<*> && other.dataDeserializer === dataDeserializer

  override fun hashCode() = System.identityHashCode(dataDeserializer)

  override fun toString() = "TypedActiveQueryKey(dataDeserializer=$dataDeserializer)"
}

private class TypedActiveQueries(val activeQuery: ActiveQuery, parentLogger: Logger) :
  ReferenceCountedSet<TypedActiveQueryKey<*>, TypedActiveQuery<*>>() {

  private val logger =
    Logger("TypedActiveQueries").apply { debug { "Created by ${parentLogger.nameWithId}" } }

  override fun valueForKey(key: TypedActiveQueryKey<*>) =
    TypedActiveQuery(
      activeQuery = activeQuery,
      dataDeserializer = key.dataDeserializer,
      logger = logger,
    )

  override fun onAllocate(entry: Entry<TypedActiveQueryKey<*>, TypedActiveQuery<*>>) {
    logger.debug(
      "Allocated ${entry.value.logger.nameWithId} (dataDeserializer=${entry.key.dataDeserializer})"
    )
  }

  override fun onFree(entry: Entry<TypedActiveQueryKey<*>, TypedActiveQuery<*>>) {
    logger.debug("Deallocated ${entry.value.logger.nameWithId}")
  }
}

private sealed class ActiveQueryResult<Data>(
  val activeQuery: ActiveQuery,
  val dataDeserializer: DeserializationStrategy<Data>,
) {
  abstract val queryExecutorResult: QueryExecutorResult

  override fun equals(other: Any?) =
    other is ActiveQueryResult<*> &&
      other.activeQuery === activeQuery &&
      other.dataDeserializer === dataDeserializer &&
      other.queryExecutorResult == queryExecutorResult

  override fun hashCode() =
    Objects.hash("Result", activeQuery, dataDeserializer, queryExecutorResult)

  override fun toString() =
    "ActiveQueryResult(" +
      "activeQuery=$activeQuery, " +
      "dataDeserializer=$dataDeserializer, " +
      "queryExecutorResult=$queryExecutorResult" +
      ")"

  class Success<Data>(
    activeQuery: ActiveQuery,
    dataDeserializer: DeserializationStrategy<Data>,
    override val queryExecutorResult: QueryExecutorResult.Success,
    logger: Logger
  ) : ActiveQueryResult<Data>(activeQuery, dataDeserializer) {

    private val lazyDeserializedData =
      SuspendingLazy(
        coroutineContext = activeQuery.dataConnect.blockingExecutor.asCoroutineDispatcher()
      ) {
        queryExecutorResult.operationResult
          .runCatching { deserialize(dataDeserializer) }
          .onFailure {
            logger.warn(it) {
              "executeQuery() [rid=${queryExecutorResult.requestId}] " +
                "decoding response data failed: $it"
            }
          }
      }

    suspend fun deserializedData(): DataConnectGrpcClient.DeserialzedOperationResult<Data> {
      return lazyDeserializedData.get().getOrThrow()
    }

    override fun equals(other: Any?) = other is Success<*> && super.equals(other)
    override fun hashCode() = Objects.hash("Success", super.hashCode())
    override fun toString() =
      "ActiveQueryResult.Success(" +
        "activeQuery=$activeQuery, " +
        "dataDeserializer=$dataDeserializer, " +
        "queryExecutorResult=$queryExecutorResult" +
        ")"
  }

  class Failure<Data>(
    activeQuery: ActiveQuery,
    dataDeserializer: DeserializationStrategy<Data>,
    override val queryExecutorResult: QueryExecutorResult.Failure
  ) : ActiveQueryResult<Data>(activeQuery, dataDeserializer) {
    override fun equals(other: Any?) = other is Failure<*> && super.equals(other)
    override fun hashCode() = Objects.hash("Failure", super.hashCode())
    override fun toString() =
      "ActiveQueryResult.Failure(" +
        "activeQuery=$activeQuery, " +
        "dataDeserializer=$dataDeserializer, " +
        "queryExecutorResult=$queryExecutorResult" +
        ")"
  }
}

private class QueryExecutor(
  val dataConnect: FirebaseDataConnect,
  val operationName: String,
  val variables: Struct
) {
  private val mutex = Mutex()

  private val state = MutableStateFlow(State(lastResult = null, lastSuccessfulResult = null))
  val lastResult: QueryExecutorResult?
    get() = state.value.lastResult
  val lastSuccessfulResult: QueryExecutorResult.Success?
    get() = state.value.lastSuccessfulResult

  suspend fun execute(): QueryExecutorResult {
    val minSequenceNumber = nextSequenceNumber()
    return state
      .map { it.lastResult }
      .filter {
        if (it != null && it.sequenceNumber > minSequenceNumber) {
          true
        } else {
          if (mutex.tryLock()) {
            executeLocked()
          }
          false
        }
      }
      .filterNotNull()
      .first()
  }

  suspend fun executeLocked(): QueryExecutorResult {
    val executeQueryResult =
      try {
        val requestId = Random.nextAlphanumericString()
        val sequenceNumber = nextSequenceNumber()
        dataConnect.lazyGrpcClient
          .get()
          .runCatching {
            executeQuery(
              requestId = requestId,
              sequenceNumber = sequenceNumber,
              operationName = operationName,
              variables = variables
            )
          }
          .fold(
            onSuccess = { QueryExecutorResult.Success(this, requestId, sequenceNumber, it) },
            onFailure = { QueryExecutorResult.Failure(this, requestId, sequenceNumber, it) },
          )
      } finally {
        mutex.unlock()
      }

    coroutineContext.ensureActive()

    while (true) {
      val originalState = state.value
      val updatedState = originalState.updatedFrom(executeQueryResult)

      if (updatedState == originalState) {
        break
      }

      if (state.compareAndSet(originalState, updatedState)) {
        break
      }
    }

    return executeQueryResult
  }

  private data class State(
    val lastResult: QueryExecutorResult?,
    val lastSuccessfulResult: QueryExecutorResult.Success?
  ) {

    fun updatedFrom(result: QueryExecutorResult): State {
      val newLastResult =
        if (lastResult == null || result.sequenceNumber > lastResult.sequenceNumber) result
        else lastResult

      val newLastSuccessfulResult =
        if (
          (lastSuccessfulResult == null ||
            result.sequenceNumber > lastSuccessfulResult.sequenceNumber) &&
            result is QueryExecutorResult.Success
        )
          result
        else lastSuccessfulResult

      return State(lastResult = newLastResult, lastSuccessfulResult = newLastSuccessfulResult)
    }
  }
}

private sealed class QueryExecutorResult(
  val queryExecutor: QueryExecutor,
  val requestId: String,
  val sequenceNumber: Long,
) {
  override fun equals(other: Any?) =
    other is QueryExecutorResult &&
      other.queryExecutor === queryExecutor &&
      other.requestId == requestId &&
      other.sequenceNumber == sequenceNumber
  override fun hashCode() = Objects.hash("Result", queryExecutor, requestId, sequenceNumber)
  override fun toString() =
    "QueryExecutorResult(" +
      "queryExecutor=$queryExecutor, " +
      "requestId=$requestId, " +
      "sequenceNumber=$sequenceNumber" +
      ")"

  class Success(
    queryExecutor: QueryExecutor,
    requestId: String,
    sequenceNumber: Long,
    val operationResult: DataConnectGrpcClient.OperationResult
  ) : QueryExecutorResult(queryExecutor, requestId, sequenceNumber) {
    override fun equals(other: Any?) =
      other is Success && super.equals(other) && other.operationResult == operationResult
    override fun hashCode() = Objects.hash("Success", super.hashCode(), operationResult)
    override fun toString() =
      "QueryExecutorResult.Success(" +
        "queryExecutor=$queryExecutor, " +
        "requestId=$requestId, " +
        "sequenceNumber=$sequenceNumber, " +
        "operationResult=$operationResult" +
        ")"
  }

  class Failure(
    queryExecutor: QueryExecutor,
    requestId: String,
    sequenceNumber: Long,
    val exception: Throwable
  ) : QueryExecutorResult(queryExecutor, requestId, sequenceNumber) {
    override fun equals(other: Any?) =
      other is Failure && super.equals(other) && other.exception == exception
    override fun hashCode() = Objects.hash("Failure", super.hashCode(), exception)
    override fun toString() =
      "QueryExecutorResult.Failure(" +
        "queryExecutor=$queryExecutor, " +
        "requestId=$requestId, " +
        "sequenceNumber=$sequenceNumber, " +
        "exception=$exception" +
        ")"
  }
}
