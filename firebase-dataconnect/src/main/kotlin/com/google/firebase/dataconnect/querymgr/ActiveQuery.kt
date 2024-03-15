package com.google.firebase.dataconnect.querymgr

import com.google.firebase.dataconnect.core.FirebaseDataConnectImpl
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.core.debug
import com.google.firebase.dataconnect.withAcquiredValue
import com.google.protobuf.Struct
import kotlinx.serialization.DeserializationStrategy

internal class ActiveQuery(
  val dataConnect: FirebaseDataConnectImpl,
  val operationName: String,
  val variables: Struct,
  parentLogger: Logger
) {
  val logger = Logger("ActiveQuery").apply { debug { "Created by ${parentLogger.nameWithId}" } }

  val queryExecutor = QueryExecutor(dataConnect, operationName, variables)

  private val typedActiveQueries = TypedActiveQueries(this, logger)

  suspend fun <Data> execute(
    dataDeserializer: DeserializationStrategy<Data>
  ): ActiveQueryResult<Data> = withTypedActiveQuery(dataDeserializer) { it.execute() }

  suspend fun <Data> subscribe(
    dataDeserializer: DeserializationStrategy<Data>,
    executeQuery: Boolean,
    callback: suspend (ActiveQueryResult<Data>) -> Unit
  ): Nothing = withTypedActiveQuery(dataDeserializer) { it.subscribe(executeQuery, callback) }

  private suspend fun <Data, ReturnType> withTypedActiveQuery(
    dataDeserializer: DeserializationStrategy<Data>,
    callback: suspend (TypedActiveQuery<Data>) -> ReturnType,
  ): ReturnType {
    val key = TypedActiveQueryKey(dataDeserializer)
    return typedActiveQueries.withAcquiredValue(key) {
      @Suppress("UNCHECKED_CAST") callback(it as TypedActiveQuery<Data>)
    }
  }
}
