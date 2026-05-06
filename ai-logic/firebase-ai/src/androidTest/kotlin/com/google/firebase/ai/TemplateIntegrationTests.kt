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

import com.google.firebase.ai.AIModels.Companion.getTemplateModels
import com.google.firebase.ai.type.PublicPreviewAPI
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.string.shouldContainIgnoringCase
import io.kotest.matchers.string.shouldNotBeEmpty
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
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

  @Test
  fun testTemplateGenerateContent() {
    for (model in getTemplateModels()) {
      runBlocking {
        val response = model.generateContent(templateId, inputs)

        response.candidates.shouldNotBeEmpty()
        response.text shouldContainIgnoringCase customerName
        response.text shouldContainIgnoringCase topic
      }
    }
  }

  @Test
  fun testTemplateGenerateContentStream() {
    for (model in getTemplateModels()) {
      runBlocking {
        val responses = model.generateContentStream(templateId, inputs).toList()
        responses
          .joinToString { it.text ?: "" }
          .lowercase()
          .let {
            it shouldContainIgnoringCase customerName
            it shouldContainIgnoringCase topic
          }
      }
    }
  }
}
