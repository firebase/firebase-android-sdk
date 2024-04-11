package com.google.firebase.dataconnect.testutil

import com.google.firebase.inject.Deferred

/** An implementation of {@link Deferred} whose provider never becomes available. */
class UnavailableDeferred<T> : Deferred<T> {
  override fun whenAvailable(handler: Deferred.DeferredHandler<T>) {}
}
