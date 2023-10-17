package com.google.firebase.sessions

import com.google.firebase.Firebase
import com.google.firebase.app
import kotlin.coroutines.CoroutineContext

/** Container for injecting dispatchers. */
class Dispatchers
internal constructor(
  internal val blockingDispatcher: CoroutineContext,
  internal val backgroundDispatcher: CoroutineContext,
) {

  companion object {
    val instance: Dispatchers
      get() = Firebase.app.get(Dispatchers::class.java)
  }
}
