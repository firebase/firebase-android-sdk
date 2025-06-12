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
import com.google.firebase.annotations.concurrent.Blocking
import com.google.firebase.appcheck.interop.InteropAppCheckTokenProvider
import com.google.firebase.auth.internal.InternalAuthProvider
import com.google.firebase.vertexai.common.APIController
import com.google.firebase.vertexai.common.AppCheckHeaderProvider
import com.google.firebase.vertexai.common.JSON
import com.google.firebase.vertexai.type.Content
import com.google.firebase.vertexai.type.LiveClientSetupMessage
import com.google.firebase.vertexai.type.LiveGenerationConfig
import com.google.firebase.vertexai.type.LiveSession
import com.google.firebase.vertexai.type.PublicPreviewAPI
import com.google.firebase.vertexai.type.RequestOptions
import com.google.firebase.vertexai.type.ServiceConnectionHandshakeFailedException
import com.google.firebase.vertexai.type.Tool
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Represents a multimodal model (like Gemini) capable of real-time content generation based on
 * various input types, supporting bidirectional streaming.
 */
@PublicPreviewAPI
@Deprecated(
  """The Vertex AI in Firebase SDK (firebase-vertexai) has been replaced with the FirebaseAI SDK (firebase-ai) to accommodate the evolving set of supported features and services.
For migration details, see the migration guide: https://firebase.google.com/docs/vertex-ai/migrate-to-latest-sdk"""
)
public class LiveGenerativeModel
internal constructor(
  private val modelName: String,
  @Blocking private val blockingDispatcher: CoroutineContext,
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
    blockingDispatcher: CoroutineContext,
    config: LiveGenerationConfig? = null,
    tools: List<Tool>? = null,
    systemInstruction: Content? = null,
    location: String = "us-central1",
    requestOptions: RequestOptions = RequestOptions(),
    appCheckTokenProvider: InteropAppCheckTokenProvider? = null,
    internalAuthProvider: InternalAuthProvider? = null,
  ) : this(
    modelName,
    blockingDispatcher,
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
   * Start a [LiveSession] with the server for bidirectional streaming.
   *
   * @return A [LiveSession] that you can use to stream messages to and from the server.
   * @throws [ServiceConnectionHandshakeFailedException] If the client was not able to establish a
   * connection with the server.
   */
  @OptIn(ExperimentalSerializationApi::class)
  public suspend fun connect(): LiveSession {
    val clientMessage =
      LiveClientSetupMessage(
          modelName,
          config?.toInternal(),
          tools?.map { it.toInternal() },
          systemInstruction?.toInternal()
        )
        .toInternal()
    val data: String = Json.encodeToString(clientMessage)
    try {
      val webSession = controller.getWebSocketSession(location)
      webSession.send(Frame.Text(data))
      val receivedJsonStr = webSession.incoming.receive().readBytes().toString(Charsets.UTF_8)
      val receivedJson = JSON.parseToJsonElement(receivedJsonStr)

      return if (receivedJson is JsonObject && "setupComplete" in receivedJson) {
        LiveSession(session = webSession, blockingDispatcher = blockingDispatcher)
      } else {
        webSession.close()
        throw ServiceConnectionHandshakeFailedException("Unable to connect to the server")
      }
    } catch (e: ClosedReceiveChannelException) {
      throw ServiceConnectionHandshakeFailedException("Channel was closed by the server", e)
    }
  }

  private companion object {
    private val TAG = LiveGenerativeModel::class.java.simpleName
  }
}
