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

import com.google.firebase.ai.type.LatLng
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.RetrievalConfig
import com.google.firebase.ai.type.TemplateTool
import com.google.firebase.ai.type.TemplateToolConfig
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.string.shouldContainIgnoringCase
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

@OptIn(PublicPreviewAPI::class)
class TemplateIntegrationTests {
  /*
   * Template used in these tests
   *
   * model: "<MODEL_NAME>"
   * input:
   *   schema:
   *     customerName: string, the name of the customer
   *     topic: string, problem to solve
   * config:
   *   temperature: 0.1
   *   topK: 10
   *   topP: 0.8
   *
   * -----
   *
   * Repeat back with "{{customerName}} - {{topic}}" and just that
   */
  private val templateId = "test-template"

  private val customerName = "John Doe"

  private val topic = "Firebase"
  private val inputs = mapOf("customerName" to customerName, "topic" to topic)

  @Before
  fun setup() {
    AIModels.setup()
  }

  @Test
  fun testTemplateGenerateContent_googleAI(): Unit = runBlocking {
    val response = AIModels.googleAITemplateModel.generateContent("$templateId-google-ai", inputs)

    response.candidates.shouldNotBeEmpty()
    response.text shouldContainIgnoringCase customerName
    response.text shouldContainIgnoringCase topic
  }

  @Test
  fun testTemplateGenerateContent_vertexAI(): Unit = runBlocking {
    val response = AIModels.vertexAITemplateModel.generateContent("$templateId-vertex-ai", inputs)

    response.candidates.shouldNotBeEmpty()
    response.text shouldContainIgnoringCase customerName
    response.text shouldContainIgnoringCase topic
  }

  @Test
  fun testTemplateGenerateContentStream_googleAI(): Unit = runBlocking {
    val responses =
      AIModels.googleAITemplateModel.generateContentStream("$templateId-google-ai", inputs).toList()
    responses
      .joinToString { it.text ?: "" }
      .lowercase()
      .replace(",", "")
      .replace("  ", " ") // Model sometimes doubles spacing
      .let {
        it shouldContainIgnoringCase customerName
        it shouldContainIgnoringCase topic
      }
  }

  @Test
  fun testTemplateGenerateContentStream_vertexAI(): Unit = runBlocking {
    val responses =
      AIModels.vertexAITemplateModel.generateContentStream("$templateId-vertex-ai", inputs).toList()
    responses
      .joinToString { it.text ?: "" }
      .lowercase()
      .replace(",", "")
      .replace("  ", " ") // Model sometimes doubles spacing
      .let {
        it shouldContainIgnoringCase customerName
        it shouldContainIgnoringCase topic
      }
  }

  @Test
  fun testTemplateGroundingCity_googleAI(): Unit = runBlocking {
    val model =
      FirebaseAI.getInstance(AIModels.app())
        .templateGenerativeModel(
          tools = listOf(TemplateTool.googleMaps()),
          toolConfig =
            TemplateToolConfig(
              RetrievalConfig(
                latLng = LatLng(latitude = 30.2672, longitude = -97.7431),
                languageCode = "en_US",
              )
            )
        )
    val responses =
      model
        .generateContentStream("maps-test-template-google-ai", mapOf("landmark" to "city"))
        .toList()
    responses
      .joinToString { it.text ?: "" }
      .lowercase()
      .replace(",", "")
      .replace("  ", " ") // Model sometimes doubles spacing
      .let { it shouldContainIgnoringCase "new york" }
  }
}
