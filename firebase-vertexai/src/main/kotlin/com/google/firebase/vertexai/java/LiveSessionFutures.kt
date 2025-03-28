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

package com.google.firebase.vertexai.java

import androidx.concurrent.futures.SuspendToFutureAdapter
import com.google.common.util.concurrent.ListenableFuture
import com.google.firebase.vertexai.GenerativeModel
import com.google.firebase.vertexai.type.Content
import com.google.firebase.vertexai.type.FunctionCallPart
import com.google.firebase.vertexai.type.FunctionResponsePart
import com.google.firebase.vertexai.type.LiveContentResponse
import com.google.firebase.vertexai.type.LiveSession
import com.google.firebase.vertexai.type.MediaData
import com.google.firebase.vertexai.type.PublicPreviewAPI
import com.google.firebase.vertexai.type.SessionAlreadyReceivingException
import kotlinx.coroutines.reactive.asPublisher
import org.reactivestreams.Publisher

/**
 * Wrapper class providing Java compatible methods for [LiveSession].
 *
 * @see [LiveSession]
 */
@PublicPreviewAPI
public abstract class LiveSessionFutures internal constructor() {

  /**
   * Starts an audio conversation with the Gemini server, which can only be stopped using
   * stopAudioConversation.
   *
   * @param functionCallHandler A callback function to map function calls from the server to their
   * response parts.
   */
  public abstract fun startAudioConversation(
    functionCallHandler: ((FunctionCallPart) -> FunctionResponsePart)?
  ): ListenableFuture<Unit>

  /** Stops the audio conversation with the Gemini Server. */
  public abstract fun stopAudioConversation(): ListenableFuture<Unit>

  /** Stop receiving from the server. */
  public abstract fun stopReceiving()

  /**
   * Sends the function response from the client to the server.
   *
   * @param functionList The list of [FunctionResponsePart] instances indicating the function
   * response from the client.
   */
  public abstract fun sendFunctionResponse(
    functionList: List<FunctionResponsePart>
  ): ListenableFuture<Unit>

  /**
   * Streams client data to the server.
   *
   * @param mediaChunks The list of [MediaData] instances representing the media data to be sent.
   */
  public abstract fun sendMediaStream(mediaChunks: List<MediaData>): ListenableFuture<Unit>

  /**
   * Sends [data][Content] to the server.
   *
   * @param content Client [Content] to be sent to the server.
   */
  public abstract fun send(content: Content): ListenableFuture<Unit>

  /**
   * Sends text to the server
   *
   * @param text Text to be sent to the server.
   */
  public abstract fun send(text: String): ListenableFuture<Unit>

  /** Closes the client session. */
  public abstract fun close(): ListenableFuture<Unit>

  /**
   * Receives responses from the server for both streaming and standard requests.
   *
   * @return A [Publisher] which will emit [LiveContentResponse] as and when it receives it
   *
   * @throws [SessionAlreadyReceivingException] when the session is already receiving.
   */
  public abstract fun receive(): Publisher<LiveContentResponse>

  private class FuturesImpl(private val session: LiveSession) : LiveSessionFutures() {

    override fun receive(): Publisher<LiveContentResponse> = session.receive().asPublisher()

    override fun close(): ListenableFuture<Unit> =
      SuspendToFutureAdapter.launchFuture { session.close() }

    override fun send(text: String) = SuspendToFutureAdapter.launchFuture { session.send(text) }

    override fun send(content: Content) =
      SuspendToFutureAdapter.launchFuture { session.send(content) }

    override fun sendFunctionResponse(functionList: List<FunctionResponsePart>) =
      SuspendToFutureAdapter.launchFuture { session.sendFunctionResponse(functionList) }

    override fun sendMediaStream(mediaChunks: List<MediaData>) =
      SuspendToFutureAdapter.launchFuture { session.sendMediaStream(mediaChunks) }

    override fun startAudioConversation(
      functionCallHandler: ((FunctionCallPart) -> FunctionResponsePart)?
    ) = SuspendToFutureAdapter.launchFuture { session.startAudioConversation(functionCallHandler) }

    override fun stopAudioConversation() =
      SuspendToFutureAdapter.launchFuture { session.stopAudioConversation() }

    override fun stopReceiving() = session.stopReceiving()
  }

  public companion object {

    /** @return a [GenerativeModelFutures] created around the provided [GenerativeModel] */
    @JvmStatic public fun from(session: LiveSession): LiveSessionFutures = FuturesImpl(session)
  }
}
