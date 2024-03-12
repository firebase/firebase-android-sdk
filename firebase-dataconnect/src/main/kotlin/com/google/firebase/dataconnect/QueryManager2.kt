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
import kotlin.coroutines.coroutineContext
import kotlin.random.Random
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.*
import kotlinx.serialization.DeserializationStrategy

private class ActiveQuery(
  val dataConnect: FirebaseDataConnect,
  val operationName: String,
  val variables: Struct
) {
  val queryExecutor = QueryExecutor(dataConnect, operationName, variables)

  private val mutex = Mutex()
  private val map = mutableMapOf<Key, ReferenceCounted<TypedActiveQuery<*>>>()

  suspend fun <Data> execute(
    dataDeserializer: DeserializationStrategy<Data>
  ): ActiveQueryResult<Data> {
    val typedActiveQuery = acquireTypedActiveQuery(dataDeserializer)
    try {
      return typedActiveQuery.execute()
    } finally {
      releaseTypedActiveQuery(typedActiveQuery)
    }
  }

  private suspend fun <Data> acquireTypedActiveQuery(
    dataDeserializer: DeserializationStrategy<Data>
  ): TypedActiveQuery<Data> {
    val key = Key(dataDeserializer)

    val typedActiveQuery =
      mutex
        .withLock {
          map
            .getOrPut(key) {
              ReferenceCounted(TypedActiveQuery(this, dataDeserializer), refCount = 0)
            }
            .also { it.refCount++ }
        }
        .obj

    check(typedActiveQuery.activeQuery === this)

    @Suppress("UNCHECKED_CAST") return typedActiveQuery as TypedActiveQuery<Data>
  }

  private suspend fun releaseTypedActiveQuery(typedActiveQuery: TypedActiveQuery<*>) {
    require(typedActiveQuery.activeQuery === this)

    val key = Key(typedActiveQuery.dataDeserializer)

    mutex.withLock {
      val entry = map[key]
      requireNotNull(entry)
      require(entry.obj === typedActiveQuery)
      require(entry.refCount > 0)
      entry.refCount--
      if (entry.refCount == 0) {
        map.remove(key)
      }
    }
  }

  class Key(val dataDeserializer: DeserializationStrategy<*>) {
    override fun equals(other: Any?) = other is Key && other.dataDeserializer === dataDeserializer
    override fun hashCode() = System.identityHashCode(dataDeserializer)
    override fun toString() = "ActiveQuery.Key(dataDeserializer=$dataDeserializer)"
  }
}

private class TypedActiveQuery<Data>(
  val activeQuery: ActiveQuery,
  val dataDeserializer: DeserializationStrategy<Data>,
) {
  suspend fun execute(): ActiveQueryResult<Data> =
    when (val result = activeQuery.queryExecutor.execute()) {
      is QueryExecutorResult.Success ->
        ActiveQueryResult.Success(activeQuery, dataDeserializer, result)
      is QueryExecutorResult.Failure ->
        ActiveQueryResult.Failure(activeQuery, dataDeserializer, result)
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
    override val queryExecutorResult: QueryExecutorResult.Success
  ) : ActiveQueryResult<Data>(activeQuery, dataDeserializer) {
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
