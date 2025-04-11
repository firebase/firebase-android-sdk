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

package com.google.firebase.vertexai.type

import android.Manifest.permission.RECORD_AUDIO
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import androidx.annotation.RequiresPermission
import com.google.firebase.annotations.concurrent.Blocking
import com.google.firebase.vertexai.common.JSON
import com.google.firebase.vertexai.common.util.accumulateUntil
import io.ktor.client.plugins.websocket.ClientWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

/** Represents a live WebSocket session capable of streaming content to and from the server. */
@PublicPreviewAPI
@OptIn(ExperimentalSerializationApi::class)
public class LiveSession
internal constructor(
  private val session: ClientWebSocketSession,
  @Blocking private val backgroundDispatcher: CoroutineContext,
  private var audioHelper: AudioHelper? = null
) {
  /**
   * Coroutine scope that we batch data on for [startAudioConversation].
   *
   * Makes it easy to stop all the work with [stopAudioConversation] by just cancelling the scope.
   */
  private var scope = CancelledCoroutineScope

  /**
   * Playback audio data sent from the model.
   *
   * Effectively, this is what the model is saying.
   */
  private val playBackQueue = ConcurrentLinkedQueue<ByteArray>()

  /**
   * Toggled whenever [receive] and [stopReceiving] are called.
   *
   * Used to ensure only one flow is consuming the playback at once.
   */
  private val startedReceiving = AtomicBoolean(false)

  /**
   * Starts an audio conversation with the Gemini server, which can only be stopped using
   * [stopAudioConversation].
   *
   * @param functionCallHandler A callback function that is invoked whenever the server receives a
   * function call.
   */
  @RequiresPermission(RECORD_AUDIO)
  public suspend fun startAudioConversation(
    functionCallHandler: ((FunctionCallPart) -> FunctionResponsePart)? = null
  ) {
    if (scope.isActive) {
      Log.w(
        TAG,
        "startAudioConversation called after the recording has already started. " +
          "Call stopAudioConversation to close the previous connection."
      )
      return
    }

    scope = CoroutineScope(backgroundDispatcher + childJob())
    audioHelper = AudioHelper.build()

    recordUserAudio()
    processModelResponses(functionCallHandler)
    listenForModelPlayback()
  }

  /**
   * Stops the audio conversation with the Gemini Server. This needs to be called only after calling
   * [startAudioConversation]
   */
  public fun stopAudioConversation() {
    if (!startedReceiving.getAndSet(false)) return

    scope.cancel()
    playBackQueue.clear()

    audioHelper?.release()
    audioHelper = null
  }

  /**
   * Receives responses from the model for both streaming and standard requests.
   *
   * Call [close] to stop receiving responses from the model.
   *
   * @return A [Flow] which will emit [LiveContentResponse] as and when it receives it
   *
   * @throws [SessionAlreadyReceivingException] when the session is already receiving.
   */
  public fun receive(): Flow<LiveContentResponse> {
    if (startedReceiving.getAndSet(true)) {
      throw SessionAlreadyReceivingException()
    }

    // TODO(b/410059569): Remove when fixed
    return flow {
        while (true) {
          val response = session.incoming.tryReceive()
          if (response.isClosed || !startedReceiving.get()) break

          val frame = response.getOrNull()
          frame?.let { frameToLiveContentResponse(it) }?.let { emit(it) }

          yield()
        }
      }
      .onCompletion { stopAudioConversation() }

    // TODO(b/410059569): Add back when fixed
    //    return session.incoming.receiveAsFlow().transform { frame ->
    //      val response = frameToLiveContentResponse(frame)
    //      response?.let { emit(it) }
    //    }.onCompletion {
    //      stopAudioConversation()
    //    }
  }

  /**
   * Stops receiving from the model.
   *
   * If this function is called during an ongoing audio conversation, the model's response will not
   * be received, and no audio will be played; the live session object will no longer receive data
   * from the server.
   *
   * To resume receiving data, you must either handle it directly using [receive], or indirectly by
   * using [startAudioConversation].
   */
  // TODO(b/410059569): Remove when fixed
  public fun stopReceiving() {
    if (!startedReceiving.getAndSet(false)) return

    scope.cancel()
    playBackQueue.clear()

    audioHelper?.release()
    audioHelper = null
  }

  /**
   * Sends the function calling responses to the server.
   *
   * @param functionList The list of [FunctionResponsePart] instances indicating the function
   * response from the client.
   */
  public suspend fun sendFunctionResponse(functionList: List<FunctionResponsePart>) {
    val jsonString =
      Json.encodeToString(
        LiveToolResponseSetup(functionList.map { it.toInternalFunctionCall() }).toInternal()
      )
    session.send(Frame.Text(jsonString))
  }

  /**
   * Streams client data to the server. Calling this after [startAudioConversation] will play the
   * response audio immediately.
   *
   * @param mediaChunks The list of [InlineDataPart] instances representing the media data to be
   * sent.
   */
  public suspend fun sendMediaStream(
    mediaChunks: List<InlineDataPart>,
  ) {
    val jsonString =
      Json.encodeToString(
        LiveClientRealtimeInputSetup(
            mediaChunks.map { (it.toInternal() as InlineDataPart.Internal).inlineData }
          )
          .toInternal()
      )
    session.send(Frame.Text(jsonString))
  }

  /**
   * Sends data to the server. Calling this after [startAudioConversation] will play the response
   * audio immediately.
   *
   * @param content Client [Content] to be sent to the server.
   */
  public suspend fun send(content: Content) {
    val jsonString =
      Json.encodeToString(LiveClientContentSetup(listOf(content.toInternal()), true).toInternal())
    session.send(Frame.Text(jsonString))
  }

  /**
   * Sends text to the server. Calling this after [startAudioConversation] will play the response
   * audio immediately.
   *
   * @param text Text to be sent to the server.
   */
  public suspend fun send(text: String) {
    send(Content.Builder().text(text).build())
  }

  /** Closes the client session. */
  public suspend fun close() {
    session.close()
    stopAudioConversation()
  }

  /** Listen to the user's microphone and send the data to the model. */
  private fun recordUserAudio() {
    // Buffer the recording so we can keep recording while data is sent to the server
    audioHelper
      ?.listenToRecording()
      ?.buffer(UNLIMITED)
      ?.accumulateUntil(MIN_BUFFER_SIZE)
      ?.onEach { sendMediaStream(listOf(InlineDataPart(it, "audio/pcm"))) }
      ?.launchIn(scope)
  }

  /**
   * Processes responses from the model during an audio conversation.
   *
   * Audio messages are added to [playBackQueue].
   *
   * Launched asynchronously on [scope].
   *
   * @param functionCallHandler A callback function that is invoked whenever the server receives a
   * function call.
   */
  private fun processModelResponses(
    functionCallHandler: ((FunctionCallPart) -> FunctionResponsePart)?
  ) {
    receive()
      .transform {
        if (it.status == LiveContentResponse.Status.INTERRUPTED) {
          playBackQueue.clear()
        } else {
          emit(it)
        }
      }
      .onEach {
        if (!it.functionCalls.isNullOrEmpty()) {
          if (functionCallHandler != null) {
            // It's fine to suspend here since you can't have a function call running concurrently
            // with an audio response
            sendFunctionResponse(it.functionCalls.map(functionCallHandler).toList())
          } else {
            Log.w(
              TAG,
              "Function calls were present in the response, but a functionCallHandler was not provided."
            )
          }
        }

        val audioParts = it.data?.parts?.filterIsInstance<InlineDataPart>().orEmpty()
        for (part in audioParts) {
          playBackQueue.add(part.inlineData)
        }
      }
      .launchIn(scope)
  }

  /**
   * Listens for playback data from the model and plays the audio.
   *
   * Polls [playBackQueue] for data, and calls [AudioHelper.playAudio] when data is received.
   *
   * Launched asynchronously on [scope].
   */
  private fun listenForModelPlayback() {
    scope.launch {
      while (isActive) {
        val playbackData = playBackQueue.poll()
        if (playbackData == null) {
          // The model playback queue is complete, so we can continue recording
          // TODO(b/408223520): Conditionally resume when param is added
          audioHelper?.resumeRecording()
          yield()
        } else {
          /**
           * We pause the recording while the model is speaking to avoid interrupting it because of
           * no echo cancellation
           */
          // TODO(b/408223520): Conditionally pause when param is added
          audioHelper?.pauseRecording()

          audioHelper?.playAudio(playbackData)
        }
      }
    }
  }

  /**
   * Converts a [Frame] from the model to a valid [LiveContentResponse], if possible.
   *
   * @return The corresponding [LiveContentResponse] or null if it couldn't be converted.
   */
  private fun frameToLiveContentResponse(frame: Frame): LiveContentResponse? {
    val jsonMessage = Json.parseToJsonElement(frame.readBytes().toString(Charsets.UTF_8))

    if (jsonMessage !is JsonObject) {
      Log.w(TAG, "Server response was not a JsonObject: $jsonMessage")
      return null
    }

    return when {
      "toolCall" in jsonMessage -> {
        val functionContent = JSON.decodeFromJsonElement<LiveServerToolCall.Internal>(jsonMessage)
        LiveContentResponse(
          null,
          LiveContentResponse.Status.NORMAL,
          functionContent.toolCall.functionCalls.map {
            FunctionCallPart(it.name, it.args.orEmpty().mapValues { x -> x.value ?: JsonNull })
          }
        )
      }
      "serverContent" in jsonMessage -> {
        val serverContent =
          JSON.decodeFromJsonElement<LiveServerContentSetup.Internal>(jsonMessage).serverContent
        val status =
          when {
            serverContent.turnComplete == true -> LiveContentResponse.Status.TURN_COMPLETE
            serverContent.interrupted == true -> LiveContentResponse.Status.INTERRUPTED
            else -> LiveContentResponse.Status.NORMAL
          }
        LiveContentResponse(serverContent.modelTurn?.toPublic(), status, null)
      }
      else -> {
        Log.w(TAG, "Failed to decode the server response: $jsonMessage")
        null
      }
    }
  }

  /**
   * Incremental update of the current conversation delivered from the client.
   *
   * Effectively, a message from the client to the model.
   */
  internal class LiveClientContentSetup(
    val turns: List<Content.Internal>,
    val turnComplete: Boolean
  ) {
    @Serializable
    internal class Internal(val clientContent: LiveClientContent) {
      @Serializable
      internal data class LiveClientContent(
        val turns: List<Content.Internal>,
        val turnComplete: Boolean
      )
    }

    fun toInternal() = Internal(Internal.LiveClientContent(turns, turnComplete))
  }

  /**
   * Incremental server update generated by the model in response to client messages.
   *
   * Effectively, a message from the model to the client.
   */
  internal class LiveServerContentSetup(
    val modelTurn: Content.Internal?,
    val turnComplete: Boolean?,
    val interrupted: Boolean?
  ) {
    @Serializable
    internal class Internal(val serverContent: LiveServerContent) {
      @Serializable
      internal data class LiveServerContent(
        val modelTurn: Content.Internal?,
        val turnComplete: Boolean?,
        val interrupted: Boolean?
      )
    }

    fun toInternal() = Internal(Internal.LiveServerContent(modelTurn, turnComplete, interrupted))
  }

  /**
   * Request for the client to execute the provided function calls and return the responses with the
   * matched `id`s.
   */
  internal data class LiveServerToolCall(
    val functionCalls: List<FunctionCallPart.Internal.FunctionCall>
  ) {
    @Serializable
    internal class Internal(val toolCall: LiveServerToolCall) {
      @Serializable
      internal data class LiveServerToolCall(
        val functionCalls: List<FunctionCallPart.Internal.FunctionCall>
      )
    }

    fun toInternal(): Internal {
      return Internal(Internal.LiveServerToolCall(functionCalls))
    }
  }

  /** Client generated responses to a [LiveServerToolCall]. */
  internal class LiveToolResponseSetup(
    val functionResponses: List<FunctionResponsePart.Internal.FunctionResponse>
  ) {
    @Serializable
    internal data class Internal(val toolResponse: LiveToolResponse) {
      @Serializable
      internal data class LiveToolResponse(
        val functionResponses: List<FunctionResponsePart.Internal.FunctionResponse>
      )
    }

    fun toInternal() = Internal(Internal.LiveToolResponse(functionResponses))
  }

  /**
   * User input that is sent to the model in real time.
   *
   * End of turn is derived from user activity (eg; end of speech).
   */
  internal class LiveClientRealtimeInputSetup(
    val mediaChunks: List<InlineDataPart.Internal.InlineData>
  ) {
    @Serializable
    internal class Internal(val realtimeInput: LiveClientRealtimeInput) {
      @Serializable
      internal data class LiveClientRealtimeInput(
        val mediaChunks: List<InlineDataPart.Internal.InlineData>
      )
    }
    fun toInternal() = Internal(Internal.LiveClientRealtimeInput(mediaChunks))
  }

  private companion object {
    val TAG = LiveSession::class.java.simpleName
    val MIN_BUFFER_SIZE =
      AudioTrack.getMinBufferSize(
        24000,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
      )
  }
}

internal suspend inline fun childJob() = Job(currentCoroutineContext()[Job] ?: Job())

internal val CancelledCoroutineScope = CoroutineScope(EmptyCoroutineContext).apply { cancel() }
