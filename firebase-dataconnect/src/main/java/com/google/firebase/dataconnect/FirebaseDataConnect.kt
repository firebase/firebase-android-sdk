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
import com.google.protobuf.Struct
import java.io.Closeable
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class FirebaseDataConnect
internal constructor(
  private val context: Context,
  private val appName: String,
  internal val projectId: String,
  internal val location: String,
  internal val service: String,
  private val creator: FirebaseDataConnectFactory
) : Closeable {
  private val logger = Logger("FirebaseDataConnect")

  init {
    logger.debug {
      "New instance created with " +
        "appName=$appName, projectId=$projectId, location=$location, service=$service"
    }
  }

  private val lock = ReentrantReadWriteLock()
  private var settingsFrozen = false
  private var closed = false

  var settings: FirebaseDataConnectSettings = FirebaseDataConnectSettings.defaultInstance
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

  suspend fun executeQuery(
    revision: String,
    operationSet: String,
    operationName: String,
    variables: Map<String, Any?>
  ): Struct =
    grpcClint.executeQuery(
      revision = revision,
      operationSet = operationSet,
      operationName = operationName,
      variables = variables
    )

  suspend fun executeMutation(
    revision: String,
    operationSet: String,
    operationName: String,
    variables: Map<String, Any?>
  ): Struct =
    grpcClint.executeMutation(
      revision = revision,
      operationSet = operationSet,
      operationName = operationName,
      variables = variables
    )

  override fun close() {
    logger.debug { "close() called" }
    lock.write {
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

  companion object {
    fun getInstance(location: String, service: String): FirebaseDataConnect =
      getInstance(Firebase.app, location, service)

    fun getInstance(app: FirebaseApp, location: String, service: String): FirebaseDataConnect =
      app.get(FirebaseDataConnectFactory::class.java).run { get(location, service) }
  }
}
