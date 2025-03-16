package com.google.firebase.vertexai.type

import android.media.AudioFormat
import android.media.AudioTrack
import io.ktor.client.plugins.websocket.ClientWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
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
import java.util.concurrent.ConcurrentLinkedQueue

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

  public suspend fun startAudioConversation() {
    if(isRecording) { return }
    isRecording = true
    audioHelper = AudioHelper()
    audioHelper!!.setupAudioTrack()

    CoroutineScope(Dispatchers.Default).launch {
      audioHelper!!.startRecording().collect {
        if(!isRecording) {
          cancel()
        }
        audioQueue.add(it)
      }
    }
    val minBufferSize = AudioTrack.getMinBufferSize(24000,  AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
    var bytesRead = 0
    var recordedData = ByteArray(minBufferSize * 2)
    CoroutineScope(Dispatchers.Default).launch {
      while(true) {
        if(!isRecording) {
          break
        }
        val byteArr = audioQueue.poll()
        if(byteArr!=null) {
          bytesRead += byteArr.size
          recordedData += byteArr
          if (bytesRead >= minBufferSize) {
            sendMediaStream(
              listOf(MediaData("audio/pcm", recordedData)),
              listOf(ContentModality.AUDIO)
            )
            bytesRead = 0
            recordedData = byteArrayOf()
          }
        } else {
          continue
        }
      }
    }
    CoroutineScope(Dispatchers.Default).launch {
      receiveMediaStream().collect {
        if(!isRecording) {
          cancel()
        }
        if(it.interrupted) {
          while(!playBackQueue.isEmpty()) playBackQueue.poll()
        } else {
          playBackQueue.add(it.data!!.parts[0].asInlineDataPartOrNull()!!.inlineData)
        }
      }
    }
    CoroutineScope(Dispatchers.Default).launch {
      while(true) {
        if(!isRecording) {
          break
        }
        val x = playBackQueue.poll()
        if(x!=null) {
          audioHelper!!.playAudio(x)
        }
      }
    }
  }

  public fun stopAudioConversation() {
    isRecording = false
    if(audioHelper!=null) {
      while(!playBackQueue.isEmpty()) playBackQueue.poll()
      while(!audioQueue.isEmpty()) audioQueue.poll()
      audioHelper!!.release()
      audioHelper = null
    }
  }

  public suspend fun receiveMediaStream(): Flow<StreamOutput> {
    return flow {
      while (true) {
        val message = session!!.incoming.receive()
        val receivedBytes =
          (message as Frame.Binary).readBytes()
        val receivedJson = receivedBytes.toString(Charsets.UTF_8)
        try {
          if (receivedJson.contains("interrupted")) {
            emit(StreamOutput(true, null))
            continue
          }
          val serverContent =
            Json.decodeFromString<ServerContentSetup>(
              receivedJson
            )
          val audioData =
            serverContent.serverContent.modelTurn.toPublic()
          emit(StreamOutput(false, audioData))


        } catch (e: Exception) {
          println("Exception: $e.message")
        }
      }
    }
  }


  public suspend fun sendMediaStream(
    mediaChunks: List<MediaData>,
    outputModalities: List<ContentModality>
  ) {
    val jsonString = Json.encodeToString(MediaStreamingSetup(MediaChunks(mediaChunks.map { it.toInternal() })))
    session?.send(Frame.Text(jsonString))
  }

  public fun send(text: String, outputModalities: List<ContentModality>): Flow<Content> {
    return flow {
      val jsonString =
        Json.encodeToString(
          ClientContentSetup(
            ClientContent(listOf(Content.Builder().text(text).build().toInternal()), true)
          )
        )

      session?.send(Frame.Text(jsonString))
      while (true) {
        try {
          val message = session?.incoming?.receive() ?: continue
          val receivedBytes = (message as Frame.Binary).readBytes()
          val receivedJson = receivedBytes.toString(Charsets.UTF_8)

          if (receivedJson.contains("turnComplete")) {
            break
          }
          val serverContent = Json.decodeFromString<ServerContentSetup>(receivedJson)
          val audioData = serverContent.serverContent.modelTurn.toPublic()
          emit(audioData)
        } catch (_: Exception) {}
      }
    }
  }

  public suspend fun close() {
    session?.close()
  }
}
