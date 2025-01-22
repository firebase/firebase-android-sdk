package com.google.firebase.functions

public fun interface StreamListener {
  /** Called when a new event is received. */
  public fun onNext(message: Any)
}
