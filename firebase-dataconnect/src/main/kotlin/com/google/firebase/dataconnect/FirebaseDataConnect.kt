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

  internal suspend fun <V, R> executeQuery(ref: QueryRef<V, R>, variables: V): R =
    withContext(sequentialDispatcher) { grpcClient }
      .run {
        executeQuery(
          operationName = ref.operationName,
          operationSet = ref.operationSet,
          revision = ref.revision,
          variables = variables,
          variablesSerializer = ref.variablesSerializer,
          resultDeserializer = ref.resultDeserializer
        )
      }

  internal suspend fun <V, R> executeMutation(ref: MutationRef<V, R>, variables: V): R =
    withContext(sequentialDispatcher) { grpcClient }
      .run {
        executeMutation(
          operationName = ref.operationName,
          operationSet = ref.operationSet,
          revision = ref.revision,
          variables = variables,
          variablesSerializer = ref.variablesSerializer,
          resultDeserializer = ref.resultDeserializer
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

    fun getInstance(
      app: FirebaseApp,
      location: String,
      service: String,
      settingsBlock: FirebaseDataConnectSettings.Builder.() -> Unit
    ): FirebaseDataConnect =
      getInstance(
        app = app,
        location = location,
        service = service,
        settings = FirebaseDataConnectSettings.defaults.build(settingsBlock)
      )

    fun getInstance(
      location: String,
      service: String,
      settingsBlock: FirebaseDataConnectSettings.Builder.() -> Unit
    ): FirebaseDataConnect =
      getInstance(
        location = location,
        service = service,
        settings = FirebaseDataConnectSettings.defaults.build(settingsBlock)
      )
  }
}

inline fun <reified VariablesType, reified ResultType> FirebaseDataConnect.query(
  operationName: String,
  operationSet: String,
  revision: String
): QueryRef<VariablesType, ResultType> =
  query(
    operationName = operationName,
    operationSet = operationSet,
    revision = revision,
    variablesSerializer = serializer(),
    resultDeserializer = serializer(),
  )

fun <VariablesType, ResultType> FirebaseDataConnect.query(
  operationName: String,
  operationSet: String,
  revision: String,
  variablesSerializer: SerializationStrategy<VariablesType>,
  resultDeserializer: DeserializationStrategy<ResultType>
): QueryRef<VariablesType, ResultType> =
  QueryRef(
    dataConnect = this,
    operationName = operationName,
    operationSet = operationSet,
    revision = revision,
    variablesSerializer = variablesSerializer,
    resultDeserializer = resultDeserializer
  )

inline fun <reified VariablesType, reified ResultType> FirebaseDataConnect.mutation(
  operationName: String,
  operationSet: String,
  revision: String,
): MutationRef<VariablesType, ResultType> =
  mutation(
    operationName = operationName,
    operationSet = operationSet,
    revision = revision,
    variablesSerializer = serializer(),
    resultDeserializer = serializer(),
  )

fun <VariablesType, ResultType> FirebaseDataConnect.mutation(
  operationName: String,
  operationSet: String,
  revision: String,
  variablesSerializer: SerializationStrategy<VariablesType>,
  resultDeserializer: DeserializationStrategy<ResultType>
): MutationRef<VariablesType, ResultType> =
  MutationRef(
    dataConnect = this,
    operationName = operationName,
    operationSet = operationSet,
    revision = revision,
    variablesSerializer = variablesSerializer,
    resultDeserializer = resultDeserializer
  )

open class DataConnectException internal constructor(message: String, cause: Throwable? = null) :
  Exception(message, cause)

open class NetworkTransportException internal constructor(message: String, cause: Throwable) :
  DataConnectException(message, cause)

open class GraphQLException internal constructor(message: String, val errors: List<String>) :
  DataConnectException(message)

open class ResultDecodeException internal constructor(message: String) :
  DataConnectException(message)
