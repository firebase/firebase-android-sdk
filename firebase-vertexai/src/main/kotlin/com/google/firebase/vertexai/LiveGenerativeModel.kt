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
import com.google.firebase.vertexai.type.Content
import com.google.firebase.vertexai.type.GeminiConnectionHandshakeFailed
import com.google.firebase.vertexai.type.LiveGenerationConfig
import com.google.firebase.vertexai.type.LiveSession
import com.google.firebase.vertexai.type.RequestOptions
import com.google.firebase.vertexai.type.Tool
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
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
    systemInstruction: Content? = null,
    location: String = "us-central1",
    requestOptions: RequestOptions = RequestOptions(),
    appCheckTokenProvider: InteropAppCheckTokenProvider? = null,
    internalAuthProvider: InternalAuthProvider? = null,
  ) : this(
    modelName,
    config,
    tools,
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
   * @throws [BidiServerHandshakeFailed] if the handshake with the server failed.
   */
  public suspend fun connect(): LiveSession {
    val clientMessage =
      BidiGenerateContentClientMessage(
          modelName,
          config?.toInternal(),
          tools?.map { it.toInternal() },
          systemInstruction?.toInternal()
        )
        .toInternal()
    val data: String = Json.encodeToString(clientMessage)
    val webSession = controller.getWebSocketSession(location)
    webSession.send(Frame.Text(data))
    val receivedJson = webSession.incoming.receive().readBytes().toString(Charsets.UTF_8)
    // TODO: Try to decode the json instead of string matching.
    return if (receivedJson.contains("setupComplete")) {
      LiveSession(session = webSession, isRecording = false)
    } else {
      webSession.close()
      throw GeminiConnectionHandshakeFailed()
    }
  }

  private companion object {
    private val TAG = LiveGenerativeModel::class.java.simpleName
  }
}
