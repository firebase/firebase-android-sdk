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

package com.google.firebase.vertexai

import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.interop.InteropAppCheckTokenProvider
import com.google.firebase.auth.internal.InternalAuthProvider
import com.google.firebase.vertexai.common.APIController
import com.google.firebase.vertexai.common.AppCheckHeaderProvider
import com.google.firebase.vertexai.type.BidiGenerateContentClientMessage
import com.google.firebase.vertexai.type.BidiGenerateContentSetup
import com.google.firebase.vertexai.type.Content
import com.google.firebase.vertexai.type.LiveGenerationConfig
import com.google.firebase.vertexai.type.LiveSession
import com.google.firebase.vertexai.type.RequestOptions
import com.google.firebase.vertexai.type.Tool
import com.google.firebase.vertexai.type.ToolConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.readBytes
import java.nio.channels.ClosedChannelException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Represents a multimodal model (like Gemini) capable of real-time content generation based on
 * various input types, supporting bidirectional streaming.
 */
public class LiveGenerativeModel
internal constructor(
  private val modelName: String,
  private val config: LiveGenerationConfig? = null,
  private val tools: List<Tool>? = null,
  private val toolConfig: ToolConfig? = null,
  private val systemInstruction: Content? = null,
  private val location: String,
  private val controller: APIController
) {
  internal constructor(
    modelName: String,
    apiKey: String,
    firebaseApp: FirebaseApp,
    config: LiveGenerationConfig? = null,
    tools: List<Tool>? = null,
    toolConfig: ToolConfig? = null,
    systemInstruction: Content? = null,
    location: String = "us-central1",
    requestOptions: RequestOptions = RequestOptions(),
    appCheckTokenProvider: InteropAppCheckTokenProvider? = null,
    internalAuthProvider: InternalAuthProvider? = null,
  ) : this(
    modelName,
    config,
    tools,
    toolConfig,
    systemInstruction,
    location,
    APIController(
      apiKey,
      modelName,
      requestOptions,
      "gl-kotlin/${KotlinVersion.CURRENT} fire/${BuildConfig.VERSION_NAME}",
      firebaseApp,
      AppCheckHeaderProvider(TAG, appCheckTokenProvider, internalAuthProvider),
    ),
  )

  /**
   * Returns a LiveSession object using which you could send/receive messages from the server
   * @return LiveSession object created. Returns null if the object cannot be created.
   * @throws [ClosedChannelException] if channel was closed before creating a websocket connection.
   */
  public suspend fun connect(): LiveSession? {
    val client = HttpClient(OkHttp) { install(WebSockets) }

    val bidiEndPoint = this.controller.getBidiEndpoint(location)
    val setup =
      BidiGenerateContentSetup(
        this.modelName,
        this.config?.toInternal(),
        this.tools?.map { it.toInternal() },
        this.systemInstruction?.toInternal()
      )
    val data: String = Json.encodeToString(BidiGenerateContentClientMessage(setup))
    val webSession = client.webSocketSession(bidiEndPoint)
    webSession.send(Frame.Text(data))
    var shouldReturn = false
    webSession.let {
      val serverMessage = it.incoming.receive()
      val receivedBytes = (serverMessage as Frame.Binary).readBytes()
      val receivedJson = receivedBytes.toString(Charsets.UTF_8)
      // TODO: Try to decode the json instead of string matching.
      if ("setupComplete" in receivedJson) {
        shouldReturn = true
      }
    }
    return if (shouldReturn) {
      LiveSession(session = webSession, isRecording = false)
    } else {
      null
    }
  }

  private companion object {
    private val TAG = LiveGenerativeModel::class.java.simpleName
  }
}
