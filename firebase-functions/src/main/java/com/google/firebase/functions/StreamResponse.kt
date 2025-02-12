package com.google.firebase.functions

/**
 * Represents a response from a Server-Sent Event (SSE) stream.
 *
 * The SSE stream consists of two types of responses:
 * - [Message]: Represents an intermediate event pushed from the server.
 * - [Result]: Represents the final response that signifies the stream has ended.
 */
public abstract class StreamResponse private constructor(public open val data: Any) {

  /**
   * An event message received during the stream.
   *
   * Messages are intermediate data chunks sent by the server before the final result.
   *
   * Example SSE format:
   * ```json
   * data: { "message": { "chunk": "foo" } }
   * ```
   */
  public class Message(override val data: Any) : StreamResponse(data)

  /**
   * The final response that terminates the stream.
   *
   * This result is sent as the last message in the stream and indicates that no further messages
   * will be received.
   *
   * Example SSE format:
   * ```json
   * data: { "result": { "text": "foo bar" } }
   * ```
   */
  public class Result(override val data: Any) : StreamResponse(data)
}
