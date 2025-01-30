package com.google.firebase.functions

/** Listener for events from a Server-Sent Events stream. */
public interface StreamListener {

  /** Called when a new event is received. */
  public fun onNext(message: Any)
}
