package com.google.firebase.dataconnect.querymgr

import com.google.firebase.dataconnect.SequencedReference
import com.google.firebase.dataconnect.core.Logger
import kotlinx.serialization.DeserializationStrategy

internal class TypedActiveQuery<Data>(
  val activeQuery: ActiveQuery,
  val dataDeserializer: DeserializationStrategy<Data>,
  val logger: Logger
) {
  suspend fun execute(): ActiveQueryResult<Data> =
    activeQuery.queryExecutor.execute().toActiveQueryResult()

  suspend fun subscribe(
    executeQuery: Boolean,
    callback: suspend (ActiveQueryResult<Data>) -> Unit
  ): Nothing =
    activeQuery.queryExecutor.subscribe(executeQuery) { callback(it.toActiveQueryResult()) }

  private fun SequencedReference<QueryExecutorResult>.toActiveQueryResult():
    ActiveQueryResult<Data> =
    when (ref) {
      is QueryExecutorResult.Success ->
        ActiveQueryResult.Success(activeQuery, dataDeserializer, successOrThrow(), logger)
      is QueryExecutorResult.Failure ->
        ActiveQueryResult.Failure(activeQuery, dataDeserializer, failureOrThrow())
    }
}
