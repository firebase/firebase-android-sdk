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
import java.io.Closeable
import java.util.concurrent.Executor
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.*
import kotlinx.coroutines.*

class FirebaseDataConnect
internal constructor(
  private val context: Context,
  val app: FirebaseApp,
  private val projectId: String,
  val location: String,
  val service: String,
  internal val blockingExecutor: Executor,
  internal val nonBlockingExecutor: Executor,
  private val creator: FirebaseDataConnectFactory
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

  private val lock = ReentrantReadWriteLock()
  private var settingsFrozen = false
  private var closed = false

  var settings: FirebaseDataConnectSettings = FirebaseDataConnectSettings.defaults
    get() {
      lock.read {
        return field
      }
    }
    set(value) {
      lock.write {
        if (closed) {
          throw IllegalStateException("instance has been closed")
        }
        if (settingsFrozen) {
          throw IllegalStateException("settings cannot be modified after they are used")
        }
        field = value
      }
      logger.debug { "Settings changed to $value" }
    }

  fun updateSettings(block: FirebaseDataConnectSettings.Builder.() -> Unit) {
    settings = settings.builder.build(block)
  }

  private val grpcClient: DataConnectGrpcClient by lazy {
    logger.debug { "DataConnectGrpcClient initialization started" }
    lock.write {
      if (closed) {
        throw IllegalStateException("instance has been closed")
      }
      settingsFrozen = true

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
  }

  internal suspend fun <V, R> executeQuery(ref: QueryRef<V, R>, variables: V): R =
    ref.codec.decodeResult(
      grpcClient.executeQuery(
        operationName = ref.operationName,
        operationSet = ref.operationSet,
        revision = ref.revision,
        variables = ref.codec.encodeVariables(variables)
      )
    )

  internal suspend fun <V, R> executeMutation(ref: MutationRef<V, R>, variables: V): R =
    ref.codec.decodeResult(
      grpcClient.executeMutation(
        operationName = ref.operationName,
        operationSet = ref.operationSet,
        revision = ref.revision,
        variables = ref.codec.encodeVariables(variables)
      )
    )

  override fun close() {
    logger.debug { "close() called" }
    lock.write {
      coroutineScope.cancel()
      try {
        grpcClient.close()
      } finally {
        closed = true
        creator.remove(this)
      }
    }
  }

  override fun toString(): String {
    return "FirebaseDataConnect" +
      "{app=${app.name}, projectId=$projectId, location=$location, service=$service}"
  }

  companion object {
    fun getInstance(location: String, service: String): FirebaseDataConnect =
      getInstance(Firebase.app, location, service)

    fun getInstance(app: FirebaseApp, location: String, service: String): FirebaseDataConnect =
      app.get(FirebaseDataConnectFactory::class.java).run { get(location, service) }
  }
}

open class DataConnectException internal constructor(message: String, cause: Throwable? = null) :
  Exception(message, cause)

open class NetworkTransportException internal constructor(message: String, cause: Throwable) :
  DataConnectException(message, cause)

open class GraphQLException internal constructor(message: String, val errors: List<String>) :
  DataConnectException(message)

open class ResultDecodeException internal constructor(message: String) :
  DataConnectException(message)
