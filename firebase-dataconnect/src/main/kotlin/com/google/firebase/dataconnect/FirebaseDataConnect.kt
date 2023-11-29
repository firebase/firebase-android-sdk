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
import java.io.Closeable
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

  // Protects `closed`, `grpcClient`, and `queryManager`.
  private val mutex = Mutex()

  // All accesses to this variable _must_ have locked `mutex`.
  private var closed = false

  // All accesses to this variable _must_ have locked `mutex`.
  private val grpcClient =
    lazy(LazyThreadSafetyMode.NONE) {
      if (closed) throw IllegalStateException("FirebaseDataConnect instance has been closed")
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
    }

  // All accesses to this variable _must_ have locked `mutex`.
  private val queryManager =
    lazy(LazyThreadSafetyMode.NONE) {
      if (closed) throw IllegalStateException("FirebaseDataConnect instance has been closed")
      QueryManager(grpcClient.value, coroutineScope)
    }

  internal suspend fun <V, D> executeQuery(ref: QueryRef<V, D>, variables: V) =
    mutex.withLock { queryManager.value }.execute(ref, variables)

  internal suspend fun <V, D> executeMutation(ref: MutationRef<V, D>, variables: V) =
    mutex
      .withLock { grpcClient.value }
      .executeMutation(
        operationName = ref.operationName,
        variables = encodeToStruct(ref.variablesSerializer, variables)
      )
      .deserialize(ref.dataDeserializer)
      .toDataConnectResult(variables)

  private val closeCompleted = AtomicBoolean(false)

  override fun close() {
    // Short circuit: just return if the "close" operation has already completed.
    if (closeCompleted.get()) {
      return
    }

    // Set the `closed` flag to `true`, making sure to honor the requirement that `closed` is always
    // accessed by a coroutine that has acquired `mutex`. Also, grab the `grpcClient` reference (if
    // it was  initialized), since that reference _also_ may only be accessed by a coroutine that
    // has acquired `mutex`.
    val grpcClient = runBlocking {
      mutex.withLock {
        closed = true
        if (grpcClient.isInitialized()) grpcClient.value else null
      }
    }

    // Do the "close" operation. Make sure to check `closeCompleted` again, since another thread may
    // have beat us here and done the "close" operation already.
    synchronized(closeCompleted) {
      if (closeCompleted.get()) {
        return
      }

      logger.debug { "Closing FirebaseDataConnect started" }
      creator.remove(this)
      grpcClient?.close()
      coroutineScope.cancel()
      logger.debug { "Closing FirebaseDataConnect completed" }

      closeCompleted.set(true)
    }
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
