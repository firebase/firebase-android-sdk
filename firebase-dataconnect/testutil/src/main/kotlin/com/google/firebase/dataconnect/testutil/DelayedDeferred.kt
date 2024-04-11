package com.google.firebase.dataconnect.testutil

import com.google.firebase.inject.Deferred
import com.google.firebase.inject.Provider
import kotlinx.coroutines.*

/**
 * An implementation of {@link Deferred} whose provider is initially unavailable, then becomes
 * available when {@link #setInstance} is invoked.
 */
class DelayedDeferred<T> : Deferred<T> {

  @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
  private val singleThreadDispatcher = newSingleThreadContext("UnavailableDeferredThread")

  private val handlers: MutableList<Deferred.DeferredHandler<T>> = mutableListOf()

  private lateinit var provider: Provider<T>

  override fun whenAvailable(handler: Deferred.DeferredHandler<T>) {
    runBlocking {
      withContext(singleThreadDispatcher) {
        if (this@DelayedDeferred::provider.isInitialized) {
          handler.handle(provider)
        } else {
          handlers.add(handler)
        }
      }
    }
  }

  fun setInstance(instance: T) {
    runBlocking {
      withContext(singleThreadDispatcher) {
        if (this@DelayedDeferred::provider.isInitialized) {
          throw IllegalStateException("setInstance() has already been invoked")
        }

        provider = Provider { instance }
        handlers.forEach { it.handle(provider) }
      }
    }
  }
}
