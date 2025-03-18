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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

public class LiveSession
internal constructor(
  private val session: ClientWebSocketSession?,
  private var isRecording: Boolean,
  private var audioHelper: AudioHelper? = null
) {

  private val audioQueue = ConcurrentLinkedQueue<ByteArray>()
  private val playBackQueue = ConcurrentLinkedQueue<ByteArray>()

  @Serializable
  internal data class ClientContent(
    @SerialName("turns") val turns: List<Content.Internal>,
    @SerialName("turn_complete") val turnComplete: Boolean
  )

  @Serializable
  internal data class ClientContentSetup(
    @SerialName("client_content") val clientContent: ClientContent
  )

  @Serializable
  internal data class ServerContentSetup(
    @SerialName("serverContent") val serverContent: ServerContent
  )

  @Serializable
  internal data class ServerContent(@SerialName("modelTurn") val modelTurn: Content.Internal)

  @Serializable internal data class MediaChunks(val mediaChunks: List<MediaData.Internal>)

  @Serializable internal data class MediaStreamingSetup(val realtimeInput: MediaChunks)

  @Serializable internal data class ToolCallSetup(val toolCall: ToolCall)

  @Serializable internal data class ToolCall(val functionCalls: List<FunctionCallPart.Internal.FunctionCall>)

  public suspend fun startAudioConversation() {
    if (isRecording) {
      return
    }
    isRecording = true
    audioHelper = AudioHelper()
    audioHelper!!.setupAudioTrack()

    CoroutineScope(Dispatchers.Default).launch {
      audioHelper!!.startRecording().collect {
        if (!isRecording) {
          cancel()
        }
        audioQueue.add(it)
      }
    }
    val minBufferSize =
      AudioTrack.getMinBufferSize(
        24000,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
      )
    var bytesRead = 0
    var recordedData = ByteArray(minBufferSize * 2)
    CoroutineScope(Dispatchers.Default).launch {
      while (true) {
        if (!isRecording) {
          break
        }
        val byteArr = audioQueue.poll()
        if (byteArr != null) {
          bytesRead += byteArr.size
          recordedData += byteArr
          if (bytesRead >= minBufferSize) {
            sendMediaStream(listOf(MediaData("audio/pcm", recordedData)))
            bytesRead = 0
            recordedData = byteArrayOf()
          }
        } else {
          continue
        }
      }
    }
    CoroutineScope(Dispatchers.Default).launch {
      receive(listOf(ContentModality.AUDIO)).collect {
        if (!isRecording) {
          cancel()
        }
        if (it.interrupted == true) {
          while (!playBackQueue.isEmpty()) playBackQueue.poll()
        } else {
          playBackQueue.add(it.data!!.parts[0].asInlineDataPartOrNull()!!.inlineData)
        }
      }
    }
    CoroutineScope(Dispatchers.Default).launch {
      while (true) {
        if (!isRecording) {
          break
        }
        val x = playBackQueue.poll()
        if (x != null) {
          audioHelper!!.playAudio(x)
        }
      }
    }
  }

  public fun stopAudioConversation() {
    isRecording = false
    if (audioHelper != null) {
      while (!playBackQueue.isEmpty()) playBackQueue.poll()
      while (!audioQueue.isEmpty()) audioQueue.poll()
      audioHelper!!.release()
      audioHelper = null
    }
  }

  public suspend fun receive(
    outputModalities: List<ContentModality>
  ): Flow<LiveContentResponse> {
    return flow {
      while (true) {
        val message = session!!.incoming.receive()
        val receivedBytes = (message as Frame.Binary).readBytes()
        val receivedJson = receivedBytes.toString(Charsets.UTF_8)
        try {
          val functionContent = Json.decodeFromString<ToolCallSetup>(receivedJson)
//          val y = functionContent.toolCall.functionCalls.map { it.toPublic() as FunctionCallPart }
//          emit(LiveContentResponse(null,false, y))
          //emit(LiveContentResponse(null, functionContent.toolCall.functionCalls.map { it.toPublic() as FunctionCallPart })))
          break
        } catch (_: Exception){ }

        try {
          if (receivedJson.contains("interrupted")) {
            emit(LiveContentResponse(null, true, null))
            continue
          }
          val serverContent = Json.decodeFromString<ServerContentSetup>(receivedJson)
          val data = serverContent.serverContent.modelTurn.toPublic()
          if (outputModalities.contains(ContentModality.AUDIO)) {
            if (data.parts[0].asInlineDataPartOrNull()?.mimeType?.equals("audio/pcm") == true) {
              emit(LiveContentResponse(data, false, null))
            }
          }
          if (outputModalities.contains(ContentModality.TEXT)) {
            if (data.parts[0] is TextPart) {
              emit(LiveContentResponse(data, false, null))
            }
          }
        } catch (e: Exception) {
          println("Exception: $e.message")
        }
      }
    }
  }

  public suspend fun sendMediaStream(
    mediaChunks: List<MediaData>,
  ) {
    val jsonString =
      Json.encodeToString(MediaStreamingSetup(MediaChunks(mediaChunks.map { it.toInternal() })))
    session?.send(Frame.Text(jsonString))
  }
  /*
  ChunkSize: fits two letters [hel, lo]



   */

  public fun send(content: Content, outputModalities: List<ContentModality>): Flow<LiveContentResponse> {
    return flow {
      val jsonString =
        Json.encodeToString(
          ClientContentSetup(
            ClientContent(listOf(content.toInternal()), true)
          )
        )
      session?.send(Frame.Text(jsonString))
      while (true) {
        try {
          val message = session?.incoming?.receive() ?: continue
          val receivedBytes = (message as Frame.Binary).readBytes()
          val receivedJson = receivedBytes.toString(Charsets.UTF_8)
          println(receivedBytes)
          try {
            val functionContent = Json.decodeFromString<ToolCallSetup>(receivedJson)
            emit(LiveContentResponse(null, false, functionContent.toolCall.functionCalls.map { FunctionCallPart(it.name, it.args!!) }))
//            val y = functionContent.toolCall.functionCalls.map { it.toPublic() as FunctionCallPart }
//            emit(LiveContentResponse(null, false, y))
            //emit(LiveContentResponse(null, functionContent.toolCall.functionCalls.map { it.toPublic() as FunctionCallPart })))
            break
          } catch (e: Exception){
            println(e.message)
          }
          if (receivedJson.contains("turnComplete")) {
            break
          }
          val serverContent = Json.decodeFromString<ServerContentSetup>(receivedJson)
          val data = serverContent.serverContent.modelTurn.toPublic()

          if (outputModalities.contains(ContentModality.AUDIO)) {
            if (data.parts[0].asInlineDataPartOrNull()?.mimeType?.equals("audio/pcm") == true) {
              emit(LiveContentResponse(data, false, listOf()))
            }
          }
          if (outputModalities.contains(ContentModality.TEXT)) {
            if (data.parts[0] is TextPart) {
              emit(LiveContentResponse(data, false, null))
            }
          }
        } catch (e: Exception) {
          println(e.message)
        }
      }
    }
  }
  public fun send(text: String, outputModalities: List<ContentModality>): Flow<LiveContentResponse> {
    return send(Content.Builder().text(text).build(), outputModalities)

  }

  public suspend fun close() {
    session?.close()
  }
}
