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

import android.media.AudioFormat
import android.media.AudioTrack
import io.ktor.client.plugins.websocket.ClientWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull

/** Represents a live WebSocket session capable of streaming content to and from the server. */
public class LiveSession
internal constructor(
  private val session: ClientWebSocketSession?,
  private var isRecording: Boolean,
  private var audioHelper: AudioHelper? = null
) {

  private val audioQueue = ConcurrentLinkedQueue<ByteArray>()
  private val playBackQueue = ConcurrentLinkedQueue<ByteArray>()
  private var startedReceiving = false
  private var receiveChannel: Channel<Frame> = Channel()
  private var functionCallChannel: Channel<List<FunctionCallPart>> = Channel()

  private companion object {
    val MIN_BUFFER_SIZE =
      AudioTrack.getMinBufferSize(
        24000,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
      )
  }

  internal class ClientContentSetup(val turns: List<Content.Internal>, val turnComplete: Boolean) {
    @Serializable
    internal class Internal(@SerialName("client_content") val clientContent: ClientContent) {
      @Serializable
      internal data class ClientContent(
        val turns: List<Content.Internal>,
        @SerialName("turn_complete") val turnComplete: Boolean
      )
    }

    fun toInternal() = Internal(Internal.ClientContent(turns, turnComplete))
  }

  internal class ToolResponseSetup(
    val functionResponses: List<FunctionResponsePart.Internal.FunctionResponse>
  ) {

    @Serializable
    internal data class Internal(val toolResponse: ToolResponse) {
      @Serializable
      internal data class ToolResponse(
        val functionResponses: List<FunctionResponsePart.Internal.FunctionResponse>
      )
    }

    fun toInternal() = Internal(Internal.ToolResponse(functionResponses))
  }

  internal class ServerContentSetup(val modelTurn: Content.Internal) {
    @Serializable
    internal class Internal(@SerialName("serverContent") val serverContent: ServerContent) {
      @Serializable
      internal data class ServerContent(@SerialName("modelTurn") val modelTurn: Content.Internal)
    }

    fun toInternal() = Internal(Internal.ServerContent(modelTurn))
  }

  internal class MediaStreamingSetup(val mediaChunks: List<MediaData.Internal>) {
    @Serializable
    internal class Internal(val realtimeInput: MediaChunks) {
      @Serializable internal data class MediaChunks(val mediaChunks: List<MediaData.Internal>)
    }
    fun toInternal() = Internal(Internal.MediaChunks(mediaChunks))
  }

  internal data class ToolCallSetup(
    val functionCalls: List<FunctionCallPart.Internal.FunctionCall>
  ) {

    @Serializable
    internal class Internal(val toolCall: ToolCall) {

      @Serializable
      internal data class ToolCall(val functionCalls: List<FunctionCallPart.Internal.FunctionCall>)
    }

    fun toInternal(): Internal {
      return Internal(Internal.ToolCall(functionCalls))
    }
  }

  /**
   * Receives all function call responses from the server for the audio conversation feature.
   *
   * @return A [Flow] which will emit list of [FunctionCallPart] as they are returned by the model.
   */
  public fun receiveAudioConversationFunctionCalls(): Flow<List<FunctionCallPart>> {
    return functionCallChannel.receiveAsFlow()
  }

  private fun fillRecordedAudioQueue() {
    CoroutineScope(Dispatchers.IO).launch {
      audioHelper!!.startRecording().collect {
        if (!isRecording) {
          cancel()
        }
        audioQueue.add(it)
      }
    }
  }

  private suspend fun sendAudioDataToServer() {
    var offset = 0
    val audioBuffer = ByteArray(MIN_BUFFER_SIZE * 2)
    while (isRecording) {
      val receivedAudio = audioQueue.poll() ?: continue
      receivedAudio.copyInto(audioBuffer, offset)
      offset += receivedAudio.size
      if (offset >= MIN_BUFFER_SIZE) {
        sendMediaStream(listOf(MediaData("audio/pcm", audioBuffer)))
        audioBuffer.fill(0)
        offset = 0
      }
    }
  }

  private fun fillServerResponseAudioQueue() {
    CoroutineScope(Dispatchers.Default).launch {
      receive(listOf(ContentModality.AUDIO)).collect {
        if (!isRecording) {
          cancel()
        }
        when (it.status) {
          LiveContentResponse.Status.INTERRUPTED ->
            while (!playBackQueue.isEmpty()) playBackQueue.poll()
          LiveContentResponse.Status.NORMAL ->
            if (!it.functionCalls.isNullOrEmpty()) {
              functionCallChannel.send(it.functionCalls)
            } else {
              val audioData = it.data?.parts?.get(0)?.asInlineDataPartOrNull()?.inlineData
              if (audioData != null) {
                playBackQueue.add(audioData)
              }
            }
        }
      }
    }
  }

  private fun playServerResponseAudio() {
    CoroutineScope(Dispatchers.IO).launch {
      while (isRecording) {
        val x = playBackQueue.poll() ?: continue
        audioHelper?.playAudio(x)
      }
    }
  }

  /**
   * Starts an audio conversation with the Gemini server, which can only be stopped using
   * stopAudioConversation.
   */
  public suspend fun startAudioConversation() {
    if (isRecording) {
      return
    }
    functionCallChannel = Channel()
    isRecording = true
    audioHelper = AudioHelper()
    audioHelper!!.setupAudioTrack()
    val scope = CoroutineScope(Dispatchers.Default)
    fillRecordedAudioQueue()
    CoroutineScope(Dispatchers.Default).launch { sendAudioDataToServer() }
    fillServerResponseAudioQueue()
    playServerResponseAudio()
    delay(1000)
  }

  /** Stops the audio conversation with the Gemini Server. */
  public fun stopAudioConversation() {
    stopReceiving()
    isRecording = false
    audioHelper?.let {
      while (playBackQueue.isNotEmpty()) playBackQueue.poll()
      while (audioQueue.isNotEmpty()) audioQueue.poll()
      it.release()
    }
    audioHelper = null
  }

  /**
   * Stops receiving from the server. If this function is called during an ongoing audio
   * conversation, the server's response will not be received, and no audio will be played.
   */
  public fun stopReceiving() {
    if (!startedReceiving) {
      return
    }
    receiveChannel.cancel()
    receiveChannel = Channel()
    startedReceiving = false
  }

  /**
   * Receives responses from the server for both streaming and standard requests.
   *
   * @param outputModalities The list of output formats to receive from the server.
   *
   * @return A [Flow] which will emit [LiveContentResponse] as and when it receives it
   *
   * @throws [SessionAlreadyReceivingException] when the session is already receiving.
   */
  public suspend fun receive(outputModalities: List<ContentModality>): Flow<LiveContentResponse> {
    if (startedReceiving) {
      throw SessionAlreadyReceivingException()
    }

    val flowReceive = session!!.incoming.receiveAsFlow()
    CoroutineScope(Dispatchers.IO).launch { flowReceive.collect { receiveChannel.send(it) } }
    return flow {
      startedReceiving = true
      while (true) {
        val message = receiveChannel.receive()
        val receivedBytes = (message as Frame.Binary).readBytes()
        val receivedJson = receivedBytes.toString(Charsets.UTF_8)
        if (receivedJson.contains("interrupted")) {
          emit(LiveContentResponse(null, LiveContentResponse.Status.INTERRUPTED, null))
          continue
        }
        if (receivedJson.contains("turnComplete")) {
          emit(LiveContentResponse(null, LiveContentResponse.Status.TURN_COMPLETE, null))
          continue
        }
        try {
          val functionContent = Json.decodeFromString<ToolCallSetup.Internal>(receivedJson)
          emit(
            LiveContentResponse(
              null,
              LiveContentResponse.Status.NORMAL,
              functionContent.toolCall.functionCalls.map {
                FunctionCallPart(it.name, it.args.orEmpty().mapValues { x -> x.value ?: JsonNull })
              }
            )
          )
          continue
        } catch (_: Exception) {}
        try {

          val serverContent = Json.decodeFromString<ServerContentSetup.Internal>(receivedJson)
          val data = serverContent.serverContent.modelTurn.toPublic()
          if (outputModalities.contains(ContentModality.AUDIO)) {
            if (data.parts[0].asInlineDataPartOrNull()?.mimeType?.equals("audio/pcm") == true) {
              emit(LiveContentResponse(data, LiveContentResponse.Status.NORMAL, null))
            }
          }
          if (outputModalities.contains(ContentModality.TEXT)) {
            if (data.parts[0] is TextPart) {
              emit(LiveContentResponse(data, LiveContentResponse.Status.NORMAL, null))
            }
          }
        } catch (_: Exception) {}
      }
    }
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
        ToolResponseSetup(functionList.map { it.toInternalFunctionCall() }).toInternal()
      )
    session?.send(Frame.Text(jsonString))
  }

  /**
   * Streams client data to the server.
   *
   * @param mediaChunks The list of [MediaData] instances representing the media data to be sent.
   */
  public suspend fun sendMediaStream(
    mediaChunks: List<MediaData>,
  ) {
    val jsonString =
      Json.encodeToString(MediaStreamingSetup(mediaChunks.map { it.toInternal() }).toInternal())
    session?.send(Frame.Text(jsonString))
  }

  /**
   * Sends data to the server
   *
   * @param content Client [Content] to be sent to the server.
   */
  public suspend fun send(content: Content) {
    val jsonString =
      Json.encodeToString(ClientContentSetup(listOf(content.toInternal()), true).toInternal())
    session?.send(Frame.Text(jsonString))
  }

  /**
   * Sends text to the server
   *
   * @param text Text to be sent to the server.
   */
  public suspend fun send(text: String) {
    send(Content.Builder().text(text).build())
  }

  /** Closes the client session. */
  public suspend fun close() {
    session?.close()
  }
}
