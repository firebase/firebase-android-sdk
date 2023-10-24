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
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope

internal class FirebaseDataConnectImpl(
  private val context: Context,
  private val appName: String,
  internal val projectId: String,
  internal val location: String,
  internal val service: String,
  private val backgroundDispatcher: CoroutineDispatcher,
  private val creator: FirebaseDataConnectFactory
) : FirebaseDataConnect {

  private val logger = Logger("FirebaseDataConnect")

  init {
    logger.debug {
      "New instance created with " +
        "appName=$appName, projectId=$projectId, location=$location, service=$service"
    }
  }

  val coroutineScope = CoroutineScope(backgroundDispatcher)

  private val lock = ReentrantReadWriteLock()
  private var settingsFrozen = false
  private var closed = false

  override var settings: FirebaseDataConnectSettings = FirebaseDataConnectSettings.defaultInstance
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

  private val grpcClint: DataConnectGrpcClient by lazy {
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
          creatorLoggerId = logger.id,
        )
        .also { logger.debug { "DataConnectGrpcClient initialization complete: $it" } }
    }
  }

  override suspend fun executeQuery(ref: QueryRef, variables: Map<String, Any?>) =
    grpcClint.executeQuery(
      revision = ref.revision,
      operationSet = ref.operationSet,
      operationName = ref.operationName,
      variables = variables
    )

  override fun subscribeQuery(ref: QueryRef, variables: Map<String, Any?>) =
    QuerySubscriptionImpl(this, ref, variables)

  override suspend fun executeMutation(ref: MutationRef, variables: Map<String, Any?>) =
    grpcClint.executeMutation(
      revision = ref.revision,
      operationSet = ref.operationSet,
      operationName = ref.operationName,
      variables = variables
    )

  override fun close() {
    logger.debug { "close() called" }
    lock.write {
      coroutineScope.cancel()
      try {
        grpcClint.close()
      } finally {
        closed = true
        creator.remove(this)
      }
    }
  }

  override fun toString(): String {
    return "FirebaseDataConnect" +
      "{appName=$appName, projectId=$projectId, location=$location, service=$service}"
  }
}
