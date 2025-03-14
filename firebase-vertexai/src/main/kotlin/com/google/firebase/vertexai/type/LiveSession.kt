package com.google.firebase.vertexai.type

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Base64
import io.ktor.client.plugins.websocket.ClientWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
    val minBufferSize = AudioTrack.getMinBufferSize(24000,  AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
    var bytesRead = 0
    val chunkSize = minBufferSize
    var recordedData = ByteArray(2*chunkSize)
    audioHelper!!.setupAudioTrack()

    audioHelper!!.startRecording().collect {
      x ->
      run {
        bytesRead += x.size
        recordedData += x
        if(bytesRead>=0) {
           println("BytesRead:")
           println(Base64.encodeToString(recordedData, Base64.NO_WRAP))
           sendMediaStream(listOf(MediaData("audio/pcm", x)), listOf(ContentModality.AUDIO)).collect {
             y ->
             run {
               val audioData = y.parts[0].asInlineDataPartOrNull()!!.inlineData
               audioHelper!!.playAudio(audioData)
             }
           }
          recordedData = byteArrayOf()
          bytesRead = 0
        }
      }

    }

  }

  public fun stopAudioConversation() {
    isRecording = false
    if(audioHelper!=null) {
      audioHelper!!.release()
    }

  }
  public fun sendMediaStream(
    mediaChunks: List<MediaData>,
    outputModalities: List<ContentModality>
  ): Flow<Content> {
    return flow {
      val jsonString = Json.encodeToString(MediaStreamingSetup(MediaChunks(mediaChunks.map { it.toInternal() })))
      println("JsonString: $jsonString")
      session?.send(Frame.Text(jsonString))
      while (true) {
        try {
          val message = session?.incoming?.receive() ?: continue
          val receivedBytes = (message as Frame.Binary).readBytes()
          val receivedJson = receivedBytes.toString(Charsets.UTF_8)
          println("Receivedjson: $receivedJson")
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
