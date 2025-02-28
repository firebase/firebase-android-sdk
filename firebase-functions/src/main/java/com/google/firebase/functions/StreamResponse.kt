/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.functions

/**
 * Represents a response from a Server-Sent Event (SSE) stream.
 *
 * The SSE stream consists of two types of responses:
 * - [Message]: Represents an intermediate event pushed from the server.
 * - [Result]: Represents the final response that signifies the stream has ended.
 */
public abstract class StreamResponse private constructor() {

  /**
   * An event message received during the stream.
   *
   * Messages are intermediate data chunks sent by the server while processing a request.
   *
   * Example SSE format:
   * ```json
   * data: { "message": { "chunk": "foo" } }
   * ```
   *
   * @property message the intermediate data received from the server.
   */
  public class Message(public val message: HttpsCallableResult) : StreamResponse()

  /**
   * The final result of the computation, marking the end of the stream.
   *
   * Unlike [Message], which represents intermediate data chunks, [Result] contains the complete
   * computation output. If clients only care about the final result, they can process this type
   * alone and ignore intermediate messages.
   *
   * Example SSE format:
   * ```json
   * data: { "result": { "text": "foo bar" } }
   * ```
   *
   * @property result the final computed result received from the server.
   */
  public class Result(public val result: HttpsCallableResult) : StreamResponse()
}
