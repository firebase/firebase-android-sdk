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

import com.google.firebase.vertexai.common.JSON
import com.google.firebase.vertexai.common.util.doBlocking
import com.google.firebase.vertexai.type.Candidate
import com.google.firebase.vertexai.type.Content
import com.google.firebase.vertexai.type.ContentModality
import com.google.firebase.vertexai.type.GenerateContentResponse
import com.google.firebase.vertexai.type.LiveSession
import com.google.firebase.vertexai.type.SpeechConfig
import com.google.firebase.vertexai.type.TextPart
import com.google.firebase.vertexai.type.Voices
import com.google.firebase.vertexai.type.liveGenerationConfig
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class LiveModelTesting {
  private val TEST_CLIENT_ID = "test"
  private val API_KEY = "AIzaSyBBuSQWjk5VJJnHwOfJxw6M7XXjpnW_j-s"

  @Test
  fun `test basic setup`() = doBlocking {
    val liveGenerationConfig = liveGenerationConfig {
      speechConfig = SpeechConfig(voice = Voices.CHARON)
      responseModalities = listOf(ContentModality.AUDIO, ContentModality.TEXT)
    }
    val generativeModel =
      LiveGenerativeModel(
        modelName =
          "projects/vertexaiinfirebase-test/locations/us-central1/publishers/google/models/gemini-2.0-flash-exp",
        apiKey = API_KEY,
        config = liveGenerationConfig
      )

    val session: LiveSession? = generativeModel.connect()
    if (session != null) {
      println("Session is good")
    }
    try {
      session?.startAudioConversation()
    } catch (e: Exception) {
      println("Received Exception: ${e.message}")
      session?.close()
    }
  }

  @OptIn(ExperimentalSerializationApi::class)
  private fun generateContentResponseAsJsonString(text: String): String {
    return JSON.encodeToString(
      GenerateContentResponse.Internal(
        listOf(Candidate.Internal(Content.Internal(parts = listOf(TextPart.Internal(text)))))
      )
    )
  }
}
