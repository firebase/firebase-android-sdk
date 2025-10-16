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
import com.google.firebase.ai.type.InlineData
import com.google.firebase.ai.type.LiveServerMessage
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
  @RequiresPermission(RECORD_AUDIO)
  public abstract fun startAudioConversation(
    functionCallHandler: ((FunctionCallPart) -> FunctionResponsePart)?
  ): ListenableFuture<Unit>

  /**
   * Starts an audio conversation with the model, which can only be stopped using
   * [stopAudioConversation].
   * @param transcriptHandler A callback function that is invoked whenever the model receives a
   * transcript.
   */
  @RequiresPermission(RECORD_AUDIO)
  public abstract fun startAudioConversation(
    transcriptHandler: ((LiveServerMessage) -> Unit)? = null,
  ): ListenableFuture<Unit>

  /**
   * Starts an audio conversation with the model, which can only be stopped using
   * [stopAudioConversation].
   */
  @RequiresPermission(RECORD_AUDIO)
  public abstract fun startAudioConversation(): ListenableFuture<Unit>

  /**
   * Starts an audio conversation with the model, which can only be stopped using
   * [stopAudioConversation] or [close].
   *
   * @param enableInterruptions If enabled, allows the user to speak over or interrupt the model's
   * ongoing reply.
   *
   * **WARNING**: The user interruption feature relies on device-specific support, and may not be
   * consistently available.
   */
  @RequiresPermission(RECORD_AUDIO)
  public abstract fun startAudioConversation(enableInterruptions: Boolean): ListenableFuture<Unit>

  /**
   * Starts an audio conversation with the model, which can only be stopped using
   * [stopAudioConversation] or [close].
   *
   * @param enableInterruptions If enabled, allows the user to speak over or interrupt the model's
   * ongoing reply.
   *
   * @param transcriptHandler A callback function that is invoked whenever the model receives a
   * transcript.
   *
   * **WARNING**: The user interruption feature relies on device-specific support, and may not be
   * consistently available.
   */
  @RequiresPermission(RECORD_AUDIO)
  public abstract fun startAudioConversation(
    transcriptHandler: ((LiveServerMessage) -> Unit)? = null,
    enableInterruptions: Boolean
  ): ListenableFuture<Unit>

  /**
   * Starts an audio conversation with the model, which can only be stopped using
   * [stopAudioConversation] or [close].
   *
   * @param functionCallHandler A callback function that is invoked whenever the model receives a
   * function call.
   *
   * @param transcriptHandler A callback function that is invoked whenever the model receives a
   * transcript.
   *
   * @param enableInterruptions If enabled, allows the user to speak over or interrupt the model's
   * ongoing reply.
   *
   * **WARNING**: The user interruption feature relies on device-specific support, and may not be
   * consistently available.
   */
  @RequiresPermission(RECORD_AUDIO)
  public abstract fun startAudioConversation(
    functionCallHandler: ((FunctionCallPart) -> FunctionResponsePart)?,
    transcriptHandler: ((LiveServerMessage) -> Unit)? = null,
    enableInterruptions: Boolean
  ): ListenableFuture<Unit>

  /**
   * Starts an audio conversation with the model, which can only be stopped using
   * [stopAudioConversation] or [close].
   *
   * @param functionCallHandler A callback function that is invoked whenever the model receives a
   * function call.
   *
   * @param enableInterruptions If enabled, allows the user to speak over or interrupt the model's
   * ongoing reply.
   *
   * **WARNING**: The user interruption feature relies on device-specific support, and may not be
   * consistently available.
   */
  @RequiresPermission(RECORD_AUDIO)
  public abstract fun startAudioConversation(
    functionCallHandler: ((FunctionCallPart) -> FunctionResponsePart)?,
    enableInterruptions: Boolean
  ): ListenableFuture<Unit>

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
   * Sends audio data to the server in realtime. Check
   * https://ai.google.dev/api/live#bidigeneratecontentrealtimeinput for details about the realtime
   * input usage.
   * @param audio The audio data to send.
   */
  public abstract fun sendAudioRealtime(audio: InlineData): ListenableFuture<Unit>

  /**
   * Sends video data to the server in realtime. Check
   * https://ai.google.dev/api/live#bidigeneratecontentrealtimeinput for details about the realtime
   * input usage.
   * @param video The video data to send. Video MIME type could be either video or image.
   */
  public abstract fun sendVideoRealtime(video: InlineData): ListenableFuture<Unit>

  /**
   * Sends text data to the server in realtime. Check
   * https://ai.google.dev/api/live#bidigeneratecontentrealtimeinput for details about the realtime
   * input usage.
   * @param text The text data to send.
   */
  public abstract fun sendTextRealtime(text: String): ListenableFuture<Unit>

  /**
   * Streams client data to the model.
   *
   * Calling this after [startAudioConversation] will play the response audio immediately.
   *
   * @param mediaChunks The list of [MediaData] instances representing the media data to be sent.
   */
  @Deprecated("Use sendAudioRealtime, sendVideoRealtime, or sendTextRealtime instead")
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
   * @return A [Publisher] which will emit [LiveServerMessage] from the model.
   *
   * @throws [SessionAlreadyReceivingException] when the session is already receiving.
   * @see stopReceiving
   */
  public abstract fun receive(): Publisher<LiveServerMessage>

  private class FuturesImpl(private val session: LiveSession) : LiveSessionFutures() {

    override fun receive(): Publisher<LiveServerMessage> = session.receive().asPublisher()

    override fun close(): ListenableFuture<Unit> =
      SuspendToFutureAdapter.launchFuture { session.close() }

    override fun send(text: String) = SuspendToFutureAdapter.launchFuture { session.send(text) }

    override fun send(content: Content) =
      SuspendToFutureAdapter.launchFuture { session.send(content) }

    override fun sendFunctionResponse(functionList: List<FunctionResponsePart>) =
      SuspendToFutureAdapter.launchFuture { session.sendFunctionResponse(functionList) }

    override fun sendAudioRealtime(audio: InlineData): ListenableFuture<Unit> =
      SuspendToFutureAdapter.launchFuture { session.sendAudioRealtime(audio) }

    override fun sendVideoRealtime(video: InlineData): ListenableFuture<Unit> =
      SuspendToFutureAdapter.launchFuture { session.sendVideoRealtime(video) }

    override fun sendTextRealtime(text: String): ListenableFuture<Unit> =
      SuspendToFutureAdapter.launchFuture { session.sendTextRealtime(text) }

    override fun sendMediaStream(mediaChunks: List<MediaData>) =
      SuspendToFutureAdapter.launchFuture { session.sendMediaStream(mediaChunks) }

    @RequiresPermission(RECORD_AUDIO)
    override fun startAudioConversation(
      functionCallHandler: ((FunctionCallPart) -> FunctionResponsePart)?
    ) = SuspendToFutureAdapter.launchFuture { session.startAudioConversation(functionCallHandler) }

    @RequiresPermission(RECORD_AUDIO)
    override fun startAudioConversation(transcriptHandler: ((LiveServerMessage) -> Unit)?) =
      SuspendToFutureAdapter.launchFuture {
        session.startAudioConversation(transcriptHandler = transcriptHandler)
      }

    @RequiresPermission(RECORD_AUDIO)
    override fun startAudioConversation() =
      SuspendToFutureAdapter.launchFuture { session.startAudioConversation() }

    @RequiresPermission(RECORD_AUDIO)
    override fun startAudioConversation(enableInterruptions: Boolean) =
      SuspendToFutureAdapter.launchFuture {
        session.startAudioConversation(enableInterruptions = enableInterruptions)
      }

    @RequiresPermission(RECORD_AUDIO)
    override fun startAudioConversation(
      transcriptHandler: ((LiveServerMessage) -> Unit)?,
      enableInterruptions: Boolean
    ) =
      SuspendToFutureAdapter.launchFuture {
        session.startAudioConversation(
          transcriptHandler = transcriptHandler,
          enableInterruptions = enableInterruptions
        )
      }

    @RequiresPermission(RECORD_AUDIO)
    override fun startAudioConversation(
      functionCallHandler: ((FunctionCallPart) -> FunctionResponsePart)?,
      transcriptHandler: ((LiveServerMessage) -> Unit)?,
      enableInterruptions: Boolean
    ) =
      SuspendToFutureAdapter.launchFuture {
        session.startAudioConversation(
          functionCallHandler = functionCallHandler,
          transcriptHandler = transcriptHandler,
          enableInterruptions = enableInterruptions
        )
      }

    @RequiresPermission(RECORD_AUDIO)
    override fun startAudioConversation(
      functionCallHandler: ((FunctionCallPart) -> FunctionResponsePart)?,
      enableInterruptions: Boolean
    ) =
      SuspendToFutureAdapter.launchFuture {
        session.startAudioConversation(
          functionCallHandler,
          enableInterruptions = enableInterruptions
        )
      }

    override fun stopAudioConversation() =
      SuspendToFutureAdapter.launchFuture { session.stopAudioConversation() }

    override fun stopReceiving() = session.stopReceiving()
  }

  public companion object {

    /** @return a [LiveSessionFutures] created around the provided [LiveSession] */
    @JvmStatic public fun from(session: LiveSession): LiveSessionFutures = FuturesImpl(session)
  }
}
