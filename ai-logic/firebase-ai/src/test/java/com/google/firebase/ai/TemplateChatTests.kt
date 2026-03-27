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

import com.google.firebase.ai.type.Candidate
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.FinishReason
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.Part
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.TextPart
import com.google.firebase.ai.type.content
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(PublicPreviewAPI::class)
@RunWith(RobolectricTestRunner::class)
class TemplateChatTests {
  private val model = mockk<TemplateGenerativeModel>()
  private val templateId = "test-template"
  private val inputs = mapOf("key" to "value")

  private lateinit var chat: TemplateChat

  @Before
  fun setup() {
    chat = TemplateChat(model, templateId, inputs)
  }

  @Test
  fun `sendMessage(Content) adds prompt and response to history`() = runTest {
    val prompt = content("user") { text("hello") }
    val responseContent = content("model") { text("hi") }
    val response = createResponse(responseContent)

    coEvery { model.generateContentWithHistory(templateId, inputs, any()) } returns response

    chat.sendMessage(prompt)

    chat.history shouldHaveSize 2
    chat.history[0] shouldBeEquivalentTo prompt
    chat.history[1] shouldBeEquivalentTo responseContent
  }

  @Test
  fun `sendMessageStream(Content) adds prompt and aggregated responses to history`() = runTest {
    val prompt = content("user") { text("hello") }
    val response1 = createResponse(content("model") { text("hi ") })
    val response2 = createResponse(content("model") { text("there") })

    every { model.generateContentWithHistoryStream(templateId, inputs, any()) } returns
      flowOf(response1, response2)

    val flow = chat.sendMessageStream(prompt)
    flow.toList()

    chat.history shouldHaveSize 2
    chat.history[0] shouldBeEquivalentTo prompt
    chat.history[1].parts shouldHaveSize 2
    chat.history[1].parts[0].shouldBeInstanceOf<TextPart>().text shouldBe "hi "
    chat.history[1].parts[1].shouldBeInstanceOf<TextPart>().text shouldBe "there"
  }

  private fun createResponse(content: Content): GenerateContentResponse {
    return GenerateContentResponse.Internal(
        listOf(Candidate.Internal(content.toInternal(), finishReason = FinishReason.Internal.STOP))
      )
      .toPublic()
  }

  private infix fun Content.shouldBeEquivalentTo(other: Content) {
    this.role shouldBe other.role
    this.parts shouldHaveSize other.parts.size
    this.parts.zip(other.parts).forEach { (a, b) -> a.shouldBeEquivalentTo(b) }
  }

  private fun Part.shouldBeEquivalentTo(other: Part) {
    this::class shouldBe other::class
    if (this is TextPart && other is TextPart) {
      this.text shouldBe other.text
    }
  }
}
