package com.google.firebase.functions

/** Listener for events from a Server-Sent Events stream. */
public interface SSETaskListener {

  /** Called when a new event is received. */
  public fun onNext(message: Any)

  /** Called when an error occurs. */
  public fun onError(exception: FirebaseFunctionsException)

  /** Called when the stream is closed. */
  public fun onComplete(result: Any)
}
