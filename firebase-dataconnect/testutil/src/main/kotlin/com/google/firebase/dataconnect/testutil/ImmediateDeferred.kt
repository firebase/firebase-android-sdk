package com.google.firebase.dataconnect.testutil

import com.google.firebase.inject.Deferred
import com.google.firebase.inject.Provider

/** An implementation of {@link Deferred} whose provider is always available. */
class ImmediateDeferred<T> constructor(instance: T) : Deferred<T> {

  private val provider = Provider { instance }

  override fun whenAvailable(handler: Deferred.DeferredHandler<T>) {
    handler.handle(provider)
  }
}
