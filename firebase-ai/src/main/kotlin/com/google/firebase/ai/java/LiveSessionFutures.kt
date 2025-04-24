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

package com.google.firebase.ai.java

import android.Manifest.permission.RECORD_AUDIO
import androidx.annotation.RequiresPermission
import androidx.concurrent.futures.SuspendToFutureAdapter
import com.google.common.util.concurrent.ListenableFuture
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.FunctionCallPart
import com.google.firebase.ai.type.FunctionResponsePart
import com.google.firebase.ai.type.LiveContentResponse
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.MediaData
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.SessionAlreadyReceivingException
import io.ktor.websocket.close
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
   * Starts an audio conversation with the model, which can only be stopped using
   * [stopAudioConversation] or [close].
   *
   * @param functionCallHandler A callback function that is invoked whenever the model receives a
   * function call.
   */
  public abstract fun startAudioConversation(
    functionCallHandler: ((FunctionCallPart) -> FunctionResponsePart)?
  ): ListenableFuture<Unit>

  /**
   * Starts an audio conversation with the model, which can only be stopped using
   * [stopAudioConversation].
   */
  @RequiresPermission(RECORD_AUDIO)
  public abstract fun startAudioConversation(): ListenableFuture<Unit>

  /**
   * Stops the audio conversation with the Gemini Server.
   *
   * This only needs to be called after a previous call to [startAudioConversation].
   *
   * If there is no audio conversation currently active, this function does nothing.
   */
  @RequiresPermission(RECORD_AUDIO)
  public abstract fun stopAudioConversation(): ListenableFuture<Unit>

  /**
   * Stops receiving from the model.
   *
   * If this function is called during an ongoing audio conversation, the model's response will not
   * be received, and no audio will be played; the live session object will no longer receive data
   * from the server.
   *
   * To resume receiving data, you must either handle it directly using [receive], or indirectly by
   * using [startAudioConversation].
   *
   * @see close
   */
  // TODO(b/410059569): Remove when fixed
  public abstract fun stopReceiving()

  /**
   * Sends function calling responses to the model.
   *
   * @param functionList The list of [FunctionResponsePart] instances indicating the function
   * response from the client.
   */
  public abstract fun sendFunctionResponse(
    functionList: List<FunctionResponsePart>
  ): ListenableFuture<Unit>

  /**
   * Streams client data to the model.
   *
   * Calling this after [startAudioConversation] will play the response audio immediately.
   *
   * @param mediaChunks The list of [MediaData] instances representing the media data to be sent.
   */
  public abstract fun sendMediaStream(mediaChunks: List<MediaData>): ListenableFuture<Unit>

  /**
   * Sends [data][Content] to the model.
   *
   * Calling this after [startAudioConversation] will play the response audio immediately.
   *
   * @param content Client [Content] to be sent to the model.
   */
  public abstract fun send(content: Content): ListenableFuture<Unit>

  /**
   * Sends text to the model.
   *
   * Calling this after [startAudioConversation] will play the response audio immediately.
   *
   * @param text Text to be sent to the model.
   */
  public abstract fun send(text: String): ListenableFuture<Unit>

  /**
   * Closes the client session.
   *
   * Once a [LiveSession] is closed, it can not be reopened; you'll need to start a new
   * [LiveSession].
   *
   * @see stopReceiving
   */
  public abstract fun close(): ListenableFuture<Unit>

  /**
   * Receives responses from the model for both streaming and standard requests.
   *
   * Call [close] to stop receiving responses from the model.
   *
   * @return A [Publisher] which will emit [LiveContentResponse] from the model.
   *
   * @throws [SessionAlreadyReceivingException] when the session is already receiving.
   * @see stopReceiving
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

    @RequiresPermission(RECORD_AUDIO)
    override fun startAudioConversation(
      functionCallHandler: ((FunctionCallPart) -> FunctionResponsePart)?
    ) = SuspendToFutureAdapter.launchFuture { session.startAudioConversation(functionCallHandler) }

    @RequiresPermission(RECORD_AUDIO)
    override fun startAudioConversation() =
      SuspendToFutureAdapter.launchFuture { session.startAudioConversation() }

    override fun stopAudioConversation() =
      SuspendToFutureAdapter.launchFuture { session.stopAudioConversation() }

    override fun stopReceiving() = session.stopReceiving()
  }

  public companion object {

    /** @return a [LiveSessionFutures] created around the provided [LiveSession] */
    @JvmStatic public fun from(session: LiveSession): LiveSessionFutures = FuturesImpl(session)
  }
}
