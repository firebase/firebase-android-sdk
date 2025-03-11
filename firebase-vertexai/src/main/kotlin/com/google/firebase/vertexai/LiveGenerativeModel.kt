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

import android.graphics.Bitmap
import com.google.firebase.appcheck.interop.InteropAppCheckTokenProvider
import com.google.firebase.auth.internal.InternalAuthProvider
import com.google.firebase.vertexai.common.APIController
import com.google.firebase.vertexai.common.AppCheckHeaderProvider
import com.google.firebase.vertexai.common.CountTokensRequest
import com.google.firebase.vertexai.common.GenerateContentRequest
import com.google.firebase.vertexai.type.Content
import com.google.firebase.vertexai.type.CountTokensResponse
import com.google.firebase.vertexai.type.FinishReason
import com.google.firebase.vertexai.type.FirebaseVertexAIException
import com.google.firebase.vertexai.type.GenerateContentResponse
import com.google.firebase.vertexai.type.GenerationConfig
import com.google.firebase.vertexai.type.LiveGenerationConfig
import com.google.firebase.vertexai.type.LiveSession
import com.google.firebase.vertexai.type.PromptBlockedException
import com.google.firebase.vertexai.type.RequestOptions
import com.google.firebase.vertexai.type.ResponseStoppedException
import com.google.firebase.vertexai.type.SafetySetting
import com.google.firebase.vertexai.type.SerializationException
import com.google.firebase.vertexai.type.Tool
import com.google.firebase.vertexai.type.ToolConfig
import com.google.firebase.vertexai.type.content
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.ExperimentalSerializationApi

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
    internal constructor(
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

    public suspend fun connect(): LiveSession = LiveSession()

}
