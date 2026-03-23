/*
 * Copyright 2026 Google LLC
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

import com.google.firebase.ai.AIModels.Companion.app
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.LatLng
import com.google.firebase.ai.type.RetrievalConfig
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.ToolConfig
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.Test

class GroundingTests {

  @Test
  fun groundingTests_canRecognizeAreas(): Unit = runBlocking {
    val model = setupModel(config = ToolConfig())
    val response = model.generateContent("Where is a good place to grab a coffee near Alameda, CA?")

    response.candidates.isEmpty() shouldBe false
    response.candidates[0].groundingMetadata?.groundingChunks?.any { it.maps != null } shouldBe true
  }

  @Test
  fun groundingTests_canRecognizeLatLng(): Unit = runBlocking {
    val model =
      setupModel(
        config =
          ToolConfig(
            retrievalConfig =
              RetrievalConfig(
                latLng = LatLng(latitude = 30.2672, longitude = -97.7431),
                languageCode = "en_US",
              ),
          )
      )
    val response = model.generateContent("Find bookstores in my area.")

    response.candidates.isEmpty() shouldBe false
    response.candidates[0].groundingMetadata?.groundingChunks?.any { it.maps != null } shouldBe true
  }

  companion object {

    @JvmStatic
    fun setupModel(config: ToolConfig): GenerativeModel {
      val model =
        FirebaseAI.getInstance(app(), GenerativeBackend.vertexAI())
          .generativeModel(
            modelName = "gemini-2.5-flash",
            toolConfig = config,
            tools = listOf(Tool.googleMaps()),
          )
      return model
    }
  }
}
