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
import com.google.firebase.FirebaseAppLifecycleListener
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.CoroutineDispatcher

internal class FirebaseDataConnectFactory(
  private val context: Context,
  private val firebaseApp: FirebaseApp,
  private val backgroundDispatcher: CoroutineDispatcher,
  private val blockingDispatcher: CoroutineDispatcher,
) {

  private val firebaseAppLifecycleListener = FirebaseAppLifecycleListener { _, _ -> close() }
  init {
    firebaseApp.addLifecycleEventListener(firebaseAppLifecycleListener)
  }

  private data class InstanceCacheKey(
    val location: String,
    val service: String,
  )

  private val lock = ReentrantLock()
  private val instancesByCacheKey = mutableMapOf<InstanceCacheKey, FirebaseDataConnect>()
  private var closed = false

  fun get(location: String, service: String): FirebaseDataConnect {
    val key = InstanceCacheKey(location = location, service = service)
    lock.withLock {
      if (closed) {
        throw IllegalStateException("FirebaseApp has been deleted")
      }
      val cachedInstance = instancesByCacheKey[key]
      if (cachedInstance !== null) {
        return cachedInstance
      }

      val projectId = firebaseApp.options.projectId ?: "<unspecified project ID>"
      val newInstance =
        FirebaseDataConnectImpl(
          context = context,
          appName = firebaseApp.name,
          projectId = projectId,
          location = location,
          service = service,
          backgroundDispatcher = backgroundDispatcher,
          creator = this
        )
      instancesByCacheKey[key] = newInstance
      return newInstance
    }
  }

  fun remove(instance: FirebaseDataConnect) {
    lock.withLock {
      val entries = instancesByCacheKey.entries.filter { it.value === instance }
      if (entries.isEmpty()) {
        return
      } else if (entries.size == 1) {
        instancesByCacheKey.remove(entries[0].key)
      } else {
        throw IllegalStateException(
          "internal error: FirebaseDataConnect instance $instance" +
            "maps to more than one key: ${entries.map { it.key }.joinToString(", ")}"
        )
      }
    }
  }

  private fun close() {
    val instances = mutableListOf<FirebaseDataConnect>()
    lock.withLock {
      closed = true
      instances.addAll(instancesByCacheKey.values)
    }

    instances.forEach { instance -> instance.close() }

    lock.withLock {
      if (instancesByCacheKey.isNotEmpty()) {
        throw IllegalStateException(
          "instances contains ${instances.size} instances " +
            "after calling terminate() on all FirebaseDataConnect instances, " +
            "but expected 0"
        )
      }
    }
  }
}
