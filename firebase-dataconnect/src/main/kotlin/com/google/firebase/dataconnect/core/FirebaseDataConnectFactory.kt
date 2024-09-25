/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.dataconnect.core

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.interop.InteropAppCheckTokenProvider
import com.google.firebase.auth.internal.InternalAuthProvider
import com.google.firebase.dataconnect.*
import com.google.firebase.inject.Deferred
import java.util.concurrent.Executor
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class FirebaseDataConnectFactory(
  private val context: Context,
  private val firebaseApp: FirebaseApp,
  private val blockingExecutor: Executor,
  private val nonBlockingExecutor: Executor,
  private val deferredAuthProvider: Deferred<InternalAuthProvider>,
  private val deferredAppCheckProvider: Deferred<InteropAppCheckTokenProvider>,
) {

  init {
    firebaseApp.addLifecycleEventListener { _, _ -> close() }
  }

  private val lock = ReentrantLock()
  private val instances = mutableMapOf<FirebaseDataConnectInstanceKey, FirebaseDataConnect>()
  private var closed = false

  fun get(config: ConnectorConfig, settings: DataConnectSettings?): FirebaseDataConnect {
    val key =
      config.run {
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

      val newInstance = FirebaseDataConnect.newInstance(config, settings)
      instances[key] = newInstance
      return newInstance
    }
  }

  private fun FirebaseDataConnect.Companion.newInstance(
    config: ConnectorConfig,
    settings: DataConnectSettings?
  ) =
    FirebaseDataConnectImpl(
      context = context,
      app = firebaseApp,
      projectId = firebaseApp.options.projectId ?: "<unspecified project ID>",
      config = config,
      blockingExecutor = blockingExecutor,
      nonBlockingExecutor = nonBlockingExecutor,
      deferredAuthProvider = deferredAuthProvider,
      deferredAppCheckProvider = deferredAppCheckProvider,
      creator = this@FirebaseDataConnectFactory,
      settings = settings ?: DataConnectSettings(),
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

  private companion object {
    private fun throwIfIncompatible(
      key: FirebaseDataConnectInstanceKey,
      instance: FirebaseDataConnect,
      settings: DataConnectSettings?
    ) {
      val keyStr = key.run { "serviceId=$serviceId, location=$location, connector=$connector" }
      if (settings !== null && instance.settings != settings) {
        throw IllegalArgumentException(
          "The settings of the FirebaseDataConnect instance with [$keyStr] is " +
            "'${instance.settings}', which is different from the given settings: $settings; " +
            "to get a FirebaseDataConnect with [$keyStr] but different settings, first call " +
            "close() on the existing FirebaseDataConnect instance, then call getInstance() " +
            "again with the desired settings. Alternately, call getInstance() with null " +
            "settings to use whatever settings are configured in the existing " +
            "FirebaseDataConnect instance."
        )
      }
    }
  }
}

private data class FirebaseDataConnectInstanceKey(
  val connector: String,
  val location: String,
  val serviceId: String,
) {
  override fun toString() = "serviceId=$serviceId, location=$location, connector=$connector"
}
