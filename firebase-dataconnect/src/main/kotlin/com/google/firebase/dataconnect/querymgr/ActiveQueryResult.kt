package com.google.firebase.dataconnect.querymgr

import com.google.firebase.dataconnect.util.SequencedReference
import com.google.firebase.dataconnect.util.SuspendingLazy
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.core.deserialize
import com.google.firebase.dataconnect.core.warn
import java.util.Objects
import kotlinx.coroutines.*
import kotlinx.serialization.DeserializationStrategy

internal sealed class ActiveQueryResult<Data>(
  val activeQuery: ActiveQuery,
  val dataDeserializer: DeserializationStrategy<Data>,
) {
  abstract val queryExecutorResult: SequencedReference<QueryExecutorResult>

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
    override val queryExecutorResult: SequencedReference<QueryExecutorResult.Success>,
    logger: Logger
  ) : ActiveQueryResult<Data>(activeQuery, dataDeserializer) {

    private val lazyDeserializedData =
      SuspendingLazy(
        coroutineContext = activeQuery.dataConnect.blockingExecutor.asCoroutineDispatcher()
      ) {
        queryExecutorResult.ref.operationResult
          .runCatching { deserialize(dataDeserializer) }
          .onFailure {
            logger.warn(it) {
              "executeQuery() [rid=${queryExecutorResult.ref.requestId}] " +
                "decoding response data failed: $it"
            }
          }
      }

    suspend fun deserializedData(): Data {
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
    override val queryExecutorResult: SequencedReference<QueryExecutorResult.Failure>
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

internal suspend fun <Data> ActiveQueryResult<Data>.toSequencedDataResult():
    SequencedReference<Result<Data>> =
  SequencedReference(
    queryExecutorResult.sequenceNumber,
    when (this) {
      is ActiveQueryResult.Success ->
        runCatching { deserializedData() }
          .fold(onSuccess = { Result.success(it) }, onFailure = { Result.failure(it) })
      is ActiveQueryResult.Failure -> Result.failure(queryExecutorResult.ref.exception)
    }
  )
