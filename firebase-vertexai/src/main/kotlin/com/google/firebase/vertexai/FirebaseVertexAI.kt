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

class FirebaseVertexAI(
  private val firebaseApp: FirebaseApp,
) {

  fun generativeModel(
    modelName: String,
    location: String = "us-central1",
    generationConfig: GenerationConfig? = null,
    safetySettings: List<SafetySetting>? = null,
    requestOptions: RequestOptions = RequestOptions(apiVersion = "v2beta")
  ) =
    GenerativeModel(
      "projects/${firebaseApp.options.projectId}/locations/${location}/publishers/google/models/${modelName}",
      firebaseApp.options.apiKey,
      generationConfig,
      safetySettings,
      requestOptions
    )

  companion object {
    val instance: FirebaseVertexAI
      get() = Firebase.app[FirebaseVertexAI::class.java]
  }
}

val Firebase.vertexAI: FirebaseVertexAI
  get() = FirebaseVertexAI.instance
