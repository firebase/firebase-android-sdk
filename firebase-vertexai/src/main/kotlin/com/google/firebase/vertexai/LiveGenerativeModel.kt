/*
 * Copyright 2024 Google LLC
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

import com.google.firebase.appcheck.interop.InteropAppCheckTokenProvider
import com.google.firebase.auth.internal.InternalAuthProvider
import com.google.firebase.vertexai.common.APIController
import com.google.firebase.vertexai.common.AppCheckHeaderProvider
import com.google.firebase.vertexai.type.Content
import com.google.firebase.vertexai.type.LiveGenerationConfig
import com.google.firebase.vertexai.type.LiveSession
import com.google.firebase.vertexai.type.RequestOptions
import com.google.firebase.vertexai.type.Tool
import com.google.firebase.vertexai.type.ToolConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.readBytes
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Represents a multimodal model (like Gemini), capable of generating content based on various input
 * types.
 */
public class LiveGenerativeModel
internal constructor(
  private val modelName: String,
  private val config: LiveGenerationConfig? = null,
  private val tools: List<Tool>? = null,
  private val toolConfig: ToolConfig? = null,
  private val systemInstruction: Content? = null,
  private val controller: APIController,
) {
  public constructor(
    modelName: String,
    apiKey: String,
    config: LiveGenerationConfig? = null,
    tools: List<Tool>? = null,
    toolConfig: ToolConfig? = null,
    systemInstruction: Content? = null,
    requestOptions: RequestOptions = RequestOptions(),
    appCheckTokenProvider: InteropAppCheckTokenProvider? = null,
    internalAuthProvider: InternalAuthProvider? = null,
  ) : this(
    modelName,
    config,
    tools,
    toolConfig,
    systemInstruction,
    APIController(
      apiKey,
      modelName,
      requestOptions,
      "gl-kotlin/${KotlinVersion.CURRENT} fire/${BuildConfig.VERSION_NAME}",
      AppCheckHeaderProvider(TAG, appCheckTokenProvider, internalAuthProvider),
    ),
  )

  @Serializable
  internal data class BidiGenerateContentSetup(
    val model: String,
    val generation_config: LiveGenerationConfig.Internal?
  )

  @Serializable
  internal data class BidiGenerateContentClientMessage(val setup: BidiGenerateContentSetup)

  public suspend fun connect(): LiveSession? {
    val client = HttpClient(CIO) { install(WebSockets) }

    val roundedUrl = this.controller.getBidiEndpoint()
    val setup = BidiGenerateContentSetup(this.modelName, this.config?.toInternal())
    val data: String = Json.encodeToString(BidiGenerateContentClientMessage(setup))
    val webSession = client.webSocketSession(roundedUrl)
    webSession.send(Frame.Text(data))
    var shouldContinue = false
    webSession.let {
      val serverMessage = it.incoming.receive()
      val receivedBytes = (serverMessage as Frame.Binary).readBytes()
      val receivedJson = receivedBytes.toString(Charsets.UTF_8)
      if ("setupComplete" in receivedJson) {
        shouldContinue = true
      }
    }
    if (shouldContinue) {
      return LiveSession(session = webSession, isRecording = false)
    } else {
      return null
    }
  }

  private companion object {
    private val TAG = LiveGenerativeModel::class.java.simpleName
  }
}
