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

import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.app
import com.google.firebase.vertexai.type.GenerationConfig
import com.google.firebase.vertexai.type.RequestOptions
import com.google.firebase.vertexai.type.SafetySetting
import com.google.firebase.vertexai.type.Tool

/** Entry point for all Firebase Vertex AI functionality. */
class FirebaseVertexAI(
  private val firebaseApp: FirebaseApp,
) {

  /**
   * A facilitator for a given multimodal model (eg; Gemini).
   *
   * @param modelName name of the model in the backend
   * @param safetySettings the safety bounds to use during alongside prompts during content
   * generation
   * @param requestOptions configuration options to utilize during backend communication
   */
  fun generativeModel(
    modelName: String,
    generationConfig: GenerationConfig? = null,
    safetySettings: List<SafetySetting>? = null,
    tools: List<Tool>? = null,
    requestOptions: RequestOptions = RequestOptions(),
  ) =
    GenerativeModel(
      "projects/${firebaseApp.options.projectId}/locations/${LOCATION}/publishers/google/models/${modelName}",
      firebaseApp.options.apiKey,
      generationConfig,
      safetySettings,
      tools,
      requestOptions,
    )

  companion object {
    val instance: FirebaseVertexAI
      get() = Firebase.app[FirebaseVertexAI::class.java]

    fun getInstance(app: FirebaseApp): FirebaseVertexAI = app[FirebaseVertexAI::class.java]

    private val LOCATION = "us-central1"
  }
}

/** Returns the [FirebaseVertexAI] instance of the default [FirebaseApp]. */
val Firebase.vertexAI: FirebaseVertexAI
  get() = FirebaseVertexAI.instance

/** Returns the [FirebaseVertexAI] instance of a given [FirebaseApp]. */
fun Firebase.vertexAI(app: FirebaseApp): FirebaseVertexAI = FirebaseVertexAI.getInstance(app)
