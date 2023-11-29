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

import android.annotation.SuppressLint
import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.app
import com.google.firebase.concurrent.FirebaseExecutors
import java.io.Closeable
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy

class FirebaseDataConnect
internal constructor(
  private val context: Context,
  val app: FirebaseApp,
  private val projectId: String,
  val serviceConfig: ServiceConfig,
  internal val blockingExecutor: Executor,
  internal val nonBlockingExecutor: Executor,
  private val creator: FirebaseDataConnectFactory,
  val settings: FirebaseDataConnectSettings,
) : Closeable {

  private val logger =
    Logger("FirebaseDataConnect").apply {
      debug {
        "New instance created with " +
          "app=${app.name}, projectId=$projectId, " +
          "serviceConfig=$serviceConfig, settings=$settings"
      }
    }

  internal val coroutineScope =
    CoroutineScope(
      SupervisorJob() +
        nonBlockingExecutor.asCoroutineDispatcher() +
        CoroutineName("FirebaseDataConnect") +
        CoroutineExceptionHandler { coroutineContext, throwable ->
          logger.warn(throwable) { "uncaught exception from a coroutine" }
        }
    )

  // Dispatcher used to access `this.closed` and `this.grpcClient`.
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
        serviceId = serviceConfig.serviceId,
        location = serviceConfig.location,
        revision = serviceConfig.revision,
        operationSet = serviceConfig.operationSet,
        hostName = settings.hostName,
        port = settings.port,
        sslEnabled = settings.sslEnabled,
        executor = blockingExecutor,
        creatorLoggerId = logger.id,
      )
      .also { logger.debug { "DataConnectGrpcClient initialization complete: $it" } }
  }

  // This reference MUST only be set or dereferenced from code running on `sequentialDispatcher`.
  private val queryManager: QueryManager by lazy {
    if (closed) {
      throw IllegalStateException("instance has been closed")
    }
    QueryManager(grpcClient, coroutineScope)
  }

  internal suspend fun <V, D> executeQuery(
    ref: QueryRef<V, D>,
    variables: V
  ): DataConnectResult<V, D> =
    withContext(sequentialDispatcher) { queryManager }.execute(ref, variables)

  internal suspend fun <V, D> executeMutation(
    ref: MutationRef<V, D>,
    variables: V
  ): DataConnectResult<V, D> =
    withContext(sequentialDispatcher) { grpcClient }
      .executeMutation(
        operationName = ref.operationName,
        variables = encodeToStruct(ref.variablesSerializer, variables)
      )
      .deserialize(ref.dataDeserializer)
      .toDataConnectResult(variables)

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

  override fun toString() =
    "FirebaseDataConnect{" +
      "app=${app.name}, projectId=$projectId, " +
      "location=${serviceConfig.location}, serviceId=${serviceConfig.serviceId} " +
      "operationSet=${serviceConfig.operationSet}, revision=${serviceConfig.revision}" +
      "}"

  class ServiceConfig(serviceId: String, location: String, operationSet: String, revision: String) {
    private val impl =
      Impl(
        serviceId = serviceId,
        location = location,
        operationSet = operationSet,
        revision = revision
      )

    val serviceId: String
      get() = impl.serviceId
    val location: String
      get() = impl.location
    val operationSet: String
      get() = impl.operationSet
    val revision: String
      get() = impl.revision

    private data class Impl(
      val serviceId: String,
      val location: String,
      val operationSet: String,
      val revision: String
    )

    override fun equals(other: Any?) =
      (other as? ServiceConfig)?.let { other.impl == impl } ?: false

    override fun hashCode() = impl.hashCode()

    override fun toString() =
      "ServiceConfig(serviceId=$serviceId, location=$location,operationSet=$operationSet, revision=$revision)"
  }

  companion object {
    @SuppressLint("FirebaseUseExplicitDependencies")
    fun getInstance(
      app: FirebaseApp,
      serviceConfig: ServiceConfig,
      settings: FirebaseDataConnectSettings? = null,
    ): FirebaseDataConnect =
      app.get(FirebaseDataConnectFactory::class.java).run {
        get(serviceConfig = serviceConfig, settings = settings)
      }

    fun getInstance(
      serviceConfig: ServiceConfig,
      settings: FirebaseDataConnectSettings? = null
    ): FirebaseDataConnect =
      getInstance(app = Firebase.app, serviceConfig = serviceConfig, settings = settings)
  }
}

fun <VariablesType, DataType> FirebaseDataConnect.query(
  operationName: String,
  variablesSerializer: SerializationStrategy<VariablesType>,
  dataDeserializer: DeserializationStrategy<DataType>
): QueryRef<VariablesType, DataType> =
  QueryRef(
    dataConnect = this,
    operationName = operationName,
    variablesSerializer = variablesSerializer,
    dataDeserializer = dataDeserializer
  )

fun <VariablesType, DataType> FirebaseDataConnect.mutation(
  operationName: String,
  variablesSerializer: SerializationStrategy<VariablesType>,
  dataDeserializer: DeserializationStrategy<DataType>
): MutationRef<VariablesType, DataType> =
  MutationRef(
    dataConnect = this,
    operationName = operationName,
    variablesSerializer = variablesSerializer,
    dataDeserializer = dataDeserializer
  )

open class DataConnectException internal constructor(message: String, cause: Throwable? = null) :
  Exception(message, cause)

open class NetworkTransportException internal constructor(message: String, cause: Throwable) :
  DataConnectException(message, cause)

open class GraphQLException internal constructor(message: String, val errors: List<String>) :
  DataConnectException(message)
