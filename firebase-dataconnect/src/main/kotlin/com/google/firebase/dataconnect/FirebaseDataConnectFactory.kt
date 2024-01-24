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
import com.google.firebase.FirebaseApp
import java.util.concurrent.Executor
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class FirebaseDataConnectFactory(
  private val context: Context,
  private val firebaseApp: FirebaseApp,
  private val blockingExecutor: Executor,
  private val nonBlockingExecutor: Executor,
) {

  init {
    firebaseApp.addLifecycleEventListener { _, _ -> close() }
  }

  private val lock = ReentrantLock()
  private val instances = mutableMapOf<FirebaseDataConnectInstanceKey, FirebaseDataConnect>()
  private var closed = false

  fun get(
    serviceConfig: FirebaseDataConnect.ServiceConfig,
    settings: FirebaseDataConnectSettings?
  ): FirebaseDataConnect {
    val key =
      serviceConfig.run {
        FirebaseDataConnectInstanceKey(
          serviceId = serviceId,
          location = location,
          connector = connector
        )
      }

    lock.withLock {
      if (closed) {
        throw IllegalStateException("FirebaseApp has been deleted")
      }

      val cachedInstance = instances[key]
      if (cachedInstance !== null) {
        throwIfIncompatible(key, cachedInstance, settings)
        return cachedInstance
      }

      val newInstance = FirebaseDataConnect.newInstance(serviceConfig, settings)
      instances[key] = newInstance
      return newInstance
    }
  }

  private fun FirebaseDataConnect.Companion.newInstance(
    serviceConfig: FirebaseDataConnect.ServiceConfig,
    settings: FirebaseDataConnectSettings?
  ) =
    FirebaseDataConnect(
      context = context,
      app = firebaseApp,
      projectId = firebaseApp.options.projectId ?: "<unspecified project ID>",
      serviceConfig = serviceConfig,
      blockingExecutor = blockingExecutor,
      nonBlockingExecutor = nonBlockingExecutor,
      creator = this@FirebaseDataConnectFactory,
      settings = settings ?: FirebaseDataConnectSettings.defaults,
    )

  fun remove(instance: FirebaseDataConnect) {
    lock.withLock {
      val keysForInstance = instances.entries.filter { it.value === instance }.map { it.key }

      when (keysForInstance.size) {
        0 -> {}
        1 -> instances.remove(keysForInstance[0])
        else ->
          throw IllegalStateException(
            "internal error: FirebaseDataConnect instance $instance " +
              "maps to ${keysForInstance.size} keys, but expected at most 1: " +
              keysForInstance.joinToString(", ")
          )
      }
    }
  }

  private fun close() {
    val instanceList =
      lock.withLock {
        closed = true
        instances.values.toList()
      }

    instanceList.forEach(FirebaseDataConnect::close)

    lock.withLock {
      if (instances.isNotEmpty()) {
        throw IllegalStateException(
          "internal error: 'instances' contains ${instances.size} elements " +
            "after calling close() on all FirebaseDataConnect instances, " +
            "but expected 0"
        )
      }
    }
  }
}

private data class FirebaseDataConnectInstanceKey(
  val serviceId: String,
  val location: String,
  val connector: String,
) {
  override fun toString() = "serviceId=$serviceId, location=$location, connector=$connector"
}

private fun throwIfIncompatible(
  key: FirebaseDataConnectInstanceKey,
  instance: FirebaseDataConnect,
  settings: FirebaseDataConnectSettings?
) {
  val keyStr = key.run { "serviceId=$serviceId, location=$location, connector=$connector" }
  if (settings !== null && instance.settings != settings) {
    throw IllegalArgumentException(
      "The settings of the FirebaseDataConnect instance with [$keyStr] is " +
        "'${instance.settings}', which is different from the given settings: $settings; " +
        "to get a FirebaseDataConnect with [$keyStr] but different settings, first call " +
        "close() on the existing FirebaseDataConnect instance, then call getInstance() again " +
        "with the desired settings. Alternately, call getInstance() with null settings to " +
        "use whatever settings are configured in the existing FirebaseDataConnect instance."
    )
  }
}
