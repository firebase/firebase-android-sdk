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
import com.google.firebase.ai.type.content
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContainIgnoringCase
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Test

@OptIn(PublicPreviewAPI::class)
class ChatTemplateIntegrationTests {
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
   * {{role "system"}}
   * You're a customer service agent, but you've been trained to only respond in very short
   * sentences. Be succinct, and to the point. No more than 5 words per response
   *
   * {{role "user"}}
   *
   * Hello, {{customerName}}
   *
   * Let's talk about {{topic}}
   * {{history}}
   */
  private val templateId = "chat-test-template"

  private val customerName = "John Doe"

  private val topic = "Firebase"
  private val inputs = mapOf("customerName" to customerName, "topic" to topic)

  @Test
  fun testTemplateChat_sendMessage() {
    for (model in getTemplateModels()) {
      runBlocking {
        val chat = model.startChat(templateId, inputs)
        val response = chat.sendMessage("which number is higher, one or ten?")

        response.candidates.isNotEmpty() shouldBe true
        response.text shouldContainIgnoringCase "ten"

        chat.history.size shouldBe 2
      }
    }
  }

  @Test
  fun testTemplateChat_sendMessageStream() {
    for (model in getTemplateModels()) {
      runBlocking {
        val chat = model.startChat(templateId, inputs)
        val responses = chat.sendMessageStream("which number is higher, one or ten?").toList()
        responses.isNotEmpty() shouldBe true
        responses.joinToString { it.text ?: "" } shouldContainIgnoringCase "ten"
        chat.history.size shouldBe 2
      }
    }
  }

  @Test
  fun testTemplateChat_withHistory() {
    for (model in getTemplateModels()) {
      runBlocking {
        val history =
          listOf(
            content("user") { text("which number is higher, one or ten?") },
            content("model") { text("Ten.") }
          )
        val chat = model.startChat(templateId, inputs, history)
        chat.history.size shouldBe 2
        val response =
          chat.sendMessage(
            "Please concatenate them both, first the smaller one, then the bigger one."
          )

        response.candidates.isNotEmpty() shouldBe true
        response.text shouldContainIgnoringCase "oneten"

        chat.history.size shouldBe 4
      }
    }
  }
}
