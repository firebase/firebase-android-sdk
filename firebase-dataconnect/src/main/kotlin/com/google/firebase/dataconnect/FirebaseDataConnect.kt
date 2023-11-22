// Copyright 2023 Google LLC
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

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.app
import com.google.firebase.concurrent.FirebaseExecutors
import java.io.Closeable
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.serializer

class FirebaseDataConnect
internal constructor(
  private val context: Context,
  val app: FirebaseApp,
  private val projectId: String,
  val location: String,
  val service: String,
  internal val blockingExecutor: Executor,
  internal val nonBlockingExecutor: Executor,
  private val creator: FirebaseDataConnectFactory,
  val settings: FirebaseDataConnectSettings,
) : Closeable {

  private val logger =
    Logger("FirebaseDataConnect").apply {
      debug {
        "New instance created with " +
          "app=${app.name}, projectId=$projectId, location=$location, service=$service"
      }
    }

  class Queries internal constructor(val dataConnect: FirebaseDataConnect)
  val queries = Queries(this)

  class Mutations internal constructor(val dataConnect: FirebaseDataConnect)
  val mutations = Mutations(this)

  internal val coroutineScope =
    CoroutineScope(
      SupervisorJob() +
        nonBlockingExecutor.asCoroutineDispatcher() +
        CoroutineName("FirebaseDataConnect")
    )

  // Dispatcher used to access `this.closed` and `this.grpcClient` simple.
  private val sequentialDispatcher =
    FirebaseExecutors.newSequentialExecutor(nonBlockingExecutor).asCoroutineDispatcher()

  // This boolean value MUST only be accessed from code running on `sequentialDispatcher`.
  private var closed = false

  // This reference MUST only be set or dereferenced from code running on `sequentialDispatcher`.
  private val grpcClient: DataConnectGrpcClient by lazy {
    logger.debug { "DataConnectGrpcClient initialization started" }
    if (closed) {
      throw IllegalStateException("instance has been closed")
    }
    DataConnectGrpcClient(
        context = context,
        projectId = projectId,
        location = location,
        service = service,
        hostName = settings.hostName,
        port = settings.port,
        sslEnabled = settings.sslEnabled,
        executor = blockingExecutor,
        creatorLoggerId = logger.id,
      )
      .also { logger.debug { "DataConnectGrpcClient initialization complete: $it" } }
  }

  internal suspend fun <V, D> executeQuery(
    ref: QueryRef<V, D>,
    variables: V
  ): DataConnectResult<V, D> =
    withContext(sequentialDispatcher) { grpcClient }
      .run {
        executeQuery(
          operationName = ref.operationName,
          operationSet = ref.operationSet,
          revision = ref.revision,
          variables = variables,
          variablesSerializer = ref.variablesSerializer,
          dataDeserializer = ref.dataDeserializer
        )
      }

  internal suspend fun <V, D> executeMutation(
    ref: MutationRef<V, D>,
    variables: V
  ): DataConnectResult<V, D> =
    withContext(sequentialDispatcher) { grpcClient }
      .run {
        executeMutation(
          operationName = ref.operationName,
          operationSet = ref.operationSet,
          revision = ref.revision,
          variables = variables,
          variablesSerializer = ref.variablesSerializer,
          dataDeserializer = ref.dataDeserializer
        )
      }

  override fun close() {
    logger.debug { "close() called" }
    runBlocking(sequentialDispatcher) {
      if (!closed) {
        doClose()
        closed = true
      }
    }
  }

  private fun doClose() {
    grpcClient.close()
    coroutineScope.cancel()
    creator.remove(this@FirebaseDataConnect)
  }

  override fun toString(): String {
    return "FirebaseDataConnect" +
      "{app=${app.name}, projectId=$projectId, location=$location, service=$service}"
  }

  companion object {
    fun getInstance(
      app: FirebaseApp,
      location: String,
      service: String,
      settings: FirebaseDataConnectSettings? = null
    ): FirebaseDataConnect =
      app.get(FirebaseDataConnectFactory::class.java).run {
        get(location = location, service = service, settings = settings)
      }

    fun getInstance(
      location: String,
      service: String,
      settings: FirebaseDataConnectSettings? = null
    ): FirebaseDataConnect =
      getInstance(app = Firebase.app, location = location, service = service, settings = settings)
  }
}

inline fun <reified VariablesType, reified DataType> FirebaseDataConnect.query(
  operationName: String,
  operationSet: String,
  revision: String
): QueryRef<VariablesType, DataType> =
  query(
    operationName = operationName,
    operationSet = operationSet,
    revision = revision,
    variablesSerializer = serializer(),
    dataDeserializer = serializer(),
  )

fun <VariablesType, DataType> FirebaseDataConnect.query(
  operationName: String,
  operationSet: String,
  revision: String,
  variablesSerializer: SerializationStrategy<VariablesType>,
  dataDeserializer: DeserializationStrategy<DataType>
): QueryRef<VariablesType, DataType> =
  QueryRef(
    dataConnect = this,
    operationName = operationName,
    operationSet = operationSet,
    revision = revision,
    variablesSerializer = variablesSerializer,
    dataDeserializer = dataDeserializer
  )

inline fun <reified VariablesType, reified DataType> FirebaseDataConnect.mutation(
  operationName: String,
  operationSet: String,
  revision: String,
): MutationRef<VariablesType, DataType> =
  mutation(
    operationName = operationName,
    operationSet = operationSet,
    revision = revision,
    variablesSerializer = serializer(),
    dataDeserializer = serializer(),
  )

fun <VariablesType, DataType> FirebaseDataConnect.mutation(
  operationName: String,
  operationSet: String,
  revision: String,
  variablesSerializer: SerializationStrategy<VariablesType>,
  dataDeserializer: DeserializationStrategy<DataType>
): MutationRef<VariablesType, DataType> =
  MutationRef(
    dataConnect = this,
    operationName = operationName,
    operationSet = operationSet,
    revision = revision,
    variablesSerializer = variablesSerializer,
    dataDeserializer = dataDeserializer
  )

open class DataConnectException internal constructor(message: String, cause: Throwable? = null) :
  Exception(message, cause)

open class NetworkTransportException internal constructor(message: String, cause: Throwable) :
  DataConnectException(message, cause)

open class GraphQLException internal constructor(message: String, val errors: List<String>) :
  DataConnectException(message)
