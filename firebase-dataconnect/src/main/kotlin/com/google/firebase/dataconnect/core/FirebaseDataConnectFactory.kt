package com.google.firebase.dataconnect.core

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.dataconnect.*
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

  fun get(config: ConnectorConfig, settings: DataConnectSettings?): FirebaseDataConnect {
    val key =
      config.run {
        FirebaseDataConnectInstanceKey(
          service = service,
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
}

private data class FirebaseDataConnectInstanceKey(
  val connector: String,
  val location: String,
  val service: String,
) {
  override fun toString() = "service=$service, location=$location, connector=$connector"
}

private fun throwIfIncompatible(
  key: FirebaseDataConnectInstanceKey,
  instance: FirebaseDataConnect,
  settings: DataConnectSettings?
) {
  val keyStr = key.run { "service=$service, location=$location, connector=$connector" }
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
