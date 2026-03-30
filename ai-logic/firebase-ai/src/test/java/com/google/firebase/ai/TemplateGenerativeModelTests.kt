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

import com.google.firebase.ai.common.APIController
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

@OptIn(PublicPreviewAPI::class, kotlinx.serialization.ExperimentalSerializationApi::class)
@RunWith(RobolectricTestRunner::class)
class TemplateGenerativeModelTests {
  private val controller = mockk<APIController>()
  private val templateUri = "https://example.com/templates/"
  private lateinit var model: TemplateGenerativeModel
  private val templateId = "test-template"
  private val inputs = mapOf("key" to "value")

  @Before
  fun setup() {
    model = TemplateGenerativeModel(templateUri, controller)
  }

  @Test
  fun `generateContentWithHistory calls controller correctly`() = runTest {
    val history = listOf(content("user") { text("hello") })
    val responseContent = content("model") { text("hi") }
    val responseInternal = createResponseInternal(responseContent)

    coEvery { controller.templateGenerateContent(any(), any()) } returns responseInternal

    val actualResponse = model.generateContentWithHistory(templateId, inputs, history)

    actualResponse.candidates.first().content shouldBeEquivalentTo responseContent
  }

  @Test
  fun `generateContentWithHistoryStream calls controller correctly`() = runTest {
    val history = listOf(content("user") { text("hello") })
    val response1 = createResponseInternal(content("model") { text("hi ") })
    val response2 = createResponseInternal(content("model") { text("there") })

    every { controller.templateGenerateContentStream(any(), any()) } returns
      flowOf(response1, response2)

    val flow = model.generateContentWithHistoryStream(templateId, inputs, history)
    val responses = flow.toList()

    responses shouldHaveSize 2
    responses[0].candidates.first().content.parts[0].shouldBeInstanceOf<TextPart>().text shouldBe
      "hi "
    responses[1].candidates.first().content.parts[0].shouldBeInstanceOf<TextPart>().text shouldBe
      "there"
  }

  private fun createResponseInternal(content: Content): GenerateContentResponse.Internal {
    return GenerateContentResponse.Internal(
      listOf(Candidate.Internal(content.toInternal(), finishReason = FinishReason.Internal.STOP))
    )
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
