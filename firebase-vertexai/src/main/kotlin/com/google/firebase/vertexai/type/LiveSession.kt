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
import kotlinx.coroutines.runBlocking
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
  private var startedReceiving = false
  private var receiveChannel: Channel<Frame> = Channel()

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
  internal data class ToolResponseSetup(
    val toolResponse: ToolResponse
  )
  @Serializable
  internal data class ToolResponse(
     val functionResponses: List<FunctionResponsePart.Internal.FunctionResponse>
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
    println("Started Receiving")
    isRecording = true
    audioHelper = AudioHelper()
    audioHelper!!.setupAudioTrack()
    val scope = CoroutineScope(Dispatchers.Default)
    CoroutineScope(Dispatchers.IO).launch {
      audioHelper!!.startRecording().collect {
        if (!isRecording) {
          cancel()
        }
        println(it)
        audioQueue.add(it)
      }
    }

    scope.launch {
      val minBufferSize =
        AudioTrack.getMinBufferSize(
          24000,
          AudioFormat.CHANNEL_OUT_MONO,
          AudioFormat.ENCODING_PCM_16BIT
        )
      var bytesRead = 0
      var recordedData = ByteArray(minBufferSize * 2)
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
    scope.launch {
      receive(listOf(ContentModality.AUDIO)).collect {
        if (!isRecording) {
          cancel()
        }
        if (it.status == Status.INTERRUPTED) {
          while (!playBackQueue.isEmpty()) playBackQueue.poll()
        } else if(it.status == Status.NORMAL) {
          playBackQueue.add(it.data!!.parts[0].asInlineDataPartOrNull()!!.inlineData)
        }
      }
    }
    CoroutineScope(Dispatchers.IO).launch {
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
    stopReceiving()
    isRecording = false
    if (audioHelper != null) {
      while (!playBackQueue.isEmpty()) playBackQueue.poll()
      while (!audioQueue.isEmpty()) audioQueue.poll()
      audioHelper!!.release()
      audioHelper = null
    }
  }

  public fun stopReceiving() {
    if(!startedReceiving) {
      return
    }
    receiveChannel.cancel()
    receiveChannel = Channel()
    startedReceiving = false
  }

  public class SessionAlreadyReceivingException: Exception("This session is already receiving. Please call stopReceiving() before calling this again.")

  public suspend fun receive(
    outputModalities: List<ContentModality>
  ): Flow<LiveContentResponse> {
    if(startedReceiving) {
      throw SessionAlreadyReceivingException()
    }

    val flowReceive = session!!.incoming.receiveAsFlow()
    CoroutineScope(Dispatchers.IO).launch {
      flowReceive.collect {
        receiveChannel.send(it)
      }
    }
    return flow {
      startedReceiving = true
      while (true) {
        val message = receiveChannel.receive()
        val receivedBytes = (message as Frame.Binary).readBytes()
        val receivedJson = receivedBytes.toString(Charsets.UTF_8)
        if (receivedJson.contains("interrupted")) {
          emit(LiveContentResponse(null, Status.INTERRUPTED, null))
          continue
        }
        if(receivedJson.contains("turnComplete")) {
          emit(LiveContentResponse(null, Status.TURNCOMPLETE, null))
          continue
        }
        try {
          val functionContent = Json.decodeFromString<ToolCallSetup>(receivedJson)
          emit(LiveContentResponse(null, Status.NORMAL, functionContent.toolCall.functionCalls.map { FunctionCallPart(it.name, it.args!!) }))
          continue
        } catch (_: Exception){ }
        try {

          val serverContent = Json.decodeFromString<ServerContentSetup>(receivedJson)
          val data = serverContent.serverContent.modelTurn.toPublic()
          if (outputModalities.contains(ContentModality.AUDIO)) {
            if (data.parts[0].asInlineDataPartOrNull()?.mimeType?.equals("audio/pcm") == true) {
              emit(LiveContentResponse(data, Status.NORMAL, null))
            }
          }
          if (outputModalities.contains(ContentModality.TEXT)) {
            if (data.parts[0] is TextPart) {
              emit(LiveContentResponse(data, Status.NORMAL, null))
            }
          }
        } catch (e: Exception) {
          println("Exception: $e.message")
        }
      }
    }
  }

  public suspend fun sendFunctionResponse(
     functionList: List<FunctionResponsePart>
  ) {
    val jsonString = Json.encodeToString(ToolResponseSetup(ToolResponse(functionList.map{it.toInternalFunctionCall()})))
    session?.send(Frame.Text(jsonString))
  }

  public suspend fun sendMediaStream(
    mediaChunks: List<MediaData>,
  ) {
    val jsonString =
      Json.encodeToString(MediaStreamingSetup(MediaChunks(mediaChunks.map { it.toInternal() })))
    println(jsonString)
    session?.send(Frame.Text(jsonString))
  }
  /*
  ChunkSize: fits two letters [hel, lo]



   */

  public suspend fun send(content: Content){
    val jsonString =
      Json.encodeToString(
        ClientContentSetup(
          ClientContent(listOf(content.toInternal()), true)
        )
      )
    println(jsonString)
    session?.send(Frame.Text(jsonString))
  }
  public suspend fun send(text: String){
     send(Content.Builder().text(text).build())

  }

  public suspend fun close() {
    session?.close()
  }
}
