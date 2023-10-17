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
) {
  private val logger = LoggerImpl("FirebaseDataConnect", Logger.Level.DEBUG)

  private val lock = ReentrantReadWriteLock()
  private var settingsFrozen = false
  private var terminated = false

  var settings: FirebaseDataConnectSettings = FirebaseDataConnectSettings.defaultInstance
    get() {
      lock.read {
        return field
      }
    }
    set(value) {
      lock.write {
        if (terminated) {
          throw IllegalStateException("instance has been terminated")
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
      if (terminated) {
        throw IllegalStateException("instance has been terminated")
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
        )
        .also { logger.debug { "DataConnectGrpcClient initialization complete: $it" } }
    }
  }

  fun executeQuery(operationName: String, variables: Map<String, Any?>): Struct =
    grpcClint.executeQuery(operationName, variables)

  fun executeMutation(operationName: String, variables: Map<String, Any?>): Struct =
    grpcClint.executeMutation(operationName, variables)

  fun terminate() {
    logger.debug { "terminate() called" }
    lock.write {
      grpcClint.close()
      terminated = true
      creator.remove(this)
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
