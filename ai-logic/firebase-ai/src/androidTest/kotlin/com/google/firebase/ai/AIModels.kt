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
package com.google.firebase.ai

import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.GenerationConfig
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.LiveGenerationConfig
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.Tool

@OptIn(PublicPreviewAPI::class)
class AIModels {

  companion object {
    private val API_KEY: String = ""
    private val APP_ID: String = ""
    private val PROJECT_ID: String = "fireescape-integ-tests"
    // General purpose models
    var app: FirebaseApp? = null
    val vertexAIFlashModel: GenerativeModel by lazy {
      getGenerativeModel(GenerativeBackend.vertexAI(), "gemini-2.5-flash")
    }
    val vertexAIFlashLiteModel: GenerativeModel by lazy {
      getGenerativeModel(GenerativeBackend.vertexAI(), "gemini-2.5-flash-lite")
    }
    val googleAIFlashModel: GenerativeModel by lazy {
      getGenerativeModel(GenerativeBackend.googleAI(), "gemini-2.5-flash")
    }
    val googleAIFlashLiteModel: GenerativeModel by lazy {
      getGenerativeModel(GenerativeBackend.googleAI(), "gemini-2.5-flash-lite")
    }
    val vertexAITemplateModel: TemplateGenerativeModel by lazy {
      FirebaseAI.getInstance(app(), GenerativeBackend.vertexAI()).templateGenerativeModel()
    }
    val googleAITemplateModel: TemplateGenerativeModel by lazy {
      FirebaseAI.getInstance(app(), GenerativeBackend.googleAI()).templateGenerativeModel()
    }

    /** Returns a list of general purpose models to test */
    fun getModels(): List<GenerativeModel> {
      return listOf(
        vertexAIFlashModel,
        vertexAIFlashLiteModel,
        googleAIFlashModel,
        googleAIFlashLiteModel
      )
    }

    fun getGenerativeModel(
      backend: GenerativeBackend,
      modelName: String = "gemini-2.5-flash",
      config: GenerationConfig? = null
    ): GenerativeModel {
      return FirebaseAI.getInstance(app(), backend)
        .generativeModel(modelName = modelName, generationConfig = config)
    }

    fun getGenerativeModels(
      modelName: String = "gemini-2.5-flash",
      config: GenerationConfig? = null
    ): List<GenerativeModel> {
      return listOf(
        getGenerativeModel(GenerativeBackend.vertexAI(), modelName, config),
        getGenerativeModel(GenerativeBackend.googleAI(), modelName, config),
      )
    }

    fun getTemplateModels(): List<TemplateGenerativeModel> {
      return listOf(vertexAITemplateModel, googleAITemplateModel)
    }

    fun app(): FirebaseApp {
      if (app == null) {
        setup()
      }
      return app!!
    }

    fun setup() {
      val context = InstrumentationRegistry.getInstrumentation().context
      app = FirebaseApp.initializeApp(context)
    }

    fun getGoogleLiveModel(
      modelName: String? = null,
      config: LiveGenerationConfig? = null,
      systemInstruction: Content? = null,
      tools: List<Tool>? = null
    ): LiveGenerativeModel {
      return FirebaseAI.getInstance(app(), GenerativeBackend.googleAI())
        .liveModel(
          modelName = modelName ?: "gemini-3.1-flash-live-preview",
          generationConfig = config,
          systemInstruction = systemInstruction,
          tools = tools
        )
    }

    fun getVertexLiveModel(
      modelName: String? = null,
      config: LiveGenerationConfig? = null
    ): LiveGenerativeModel {
      return FirebaseAI.getInstance(app(), GenerativeBackend.vertexAI())
        .liveModel(
          modelName = modelName ?: "gemini-live-2.5-flash-native-audio",
          generationConfig = config,
        )
    }

    fun getAllLiveModels(
      modelName: String? = null,
      config: LiveGenerationConfig? = null
    ): List<LiveGenerativeModel> {
      return listOf(getGoogleLiveModel(modelName, config), getVertexLiveModel(modelName, config))
    }
  }
}
