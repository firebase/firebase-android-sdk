package com.google.firebase.functions

/** A response from a Server-Sent Event stream. */
public sealed class StreamResponse(public open val data: Any) {

  /** Called when a new event is received. */
  public class Message(override val data: Any) : StreamResponse(data)

  /** Called when the stream is closed. */
  public class Result(override val data: Any) : StreamResponse(data)
}
