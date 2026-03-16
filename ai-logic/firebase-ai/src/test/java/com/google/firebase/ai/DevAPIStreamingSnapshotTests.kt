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

import com.google.firebase.ai.type.BlockReason
import com.google.firebase.ai.type.FinishReason
import com.google.firebase.ai.type.FunctionCallPart
import com.google.firebase.ai.type.PromptBlockedException
import com.google.firebase.ai.type.ResponseStoppedException
import com.google.firebase.ai.type.ServerException
import com.google.firebase.ai.type.content
import com.google.firebase.ai.util.ResponseInfo
import com.google.firebase.ai.util.goldenDevAPIStreamingFile
import com.google.firebase.ai.util.goldenDevAPIStreamingFiles
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class DevAPIStreamingSnapshotTests {
  private val testTimeout = 5.seconds

  @Test
  fun `short reply`() =
    goldenDevAPIStreamingFile("streaming-success-basic-reply-short.txt") {
      val responses = model.generateContentStream("prompt")

      withTimeout(testTimeout) {
        val responseList = responses.toList()
        responseList.isEmpty() shouldBe false
        responseList.last().candidates.first().apply {
          finishReason shouldBe FinishReason.STOP
          content.parts.isEmpty() shouldBe false
        }
      }
    }

  @Test
  fun `long reply`() =
    goldenDevAPIStreamingFile("streaming-success-basic-reply-long.txt") {
      val responses = model.generateContentStream("prompt")

      withTimeout(testTimeout) {
        val responseList = responses.toList()
        responseList.isEmpty() shouldBe false
        responseList.last().candidates.first().apply {
          finishReason shouldBe FinishReason.STOP
          content.parts.isEmpty() shouldBe false
        }
      }
    }

  @Test
  fun `reply with a single empty part`() =
    goldenDevAPIStreamingFile("streaming-success-empty-parts.txt") {
      val responses = model.generateContentStream("prompt")

      withTimeout(testTimeout) {
        val responseList = responses.toList()
        responseList.isEmpty() shouldBe false
        // Second to last response has no parts
        responseList[5].candidates.first().content.parts.shouldBeEmpty()
        responseList.last().candidates.first().apply {
          finishReason shouldBe FinishReason.STOP
          content.parts.isEmpty() shouldBe false
        }
      }
    }

  @Test
  fun `success call with thought summary and signature`() =
    goldenDevAPIStreamingFile(
      "streaming-success-thinking-function-call-thought-summary-signature.txt"
    ) {
      val responses = model.generateContentStream("prompt")

      withTimeout(testTimeout) {
        val responseList = responses.toList()
        responseList.isEmpty() shouldBe false
        val functionCallResponse = responseList.find { it.functionCalls.isNotEmpty() }
        functionCallResponse.shouldNotBeNull()
        functionCallResponse.functionCalls.first().let {
          it.thoughtSignature.shouldNotBeNull()
          it.thoughtSignature.shouldStartWith("CiIBVKhc7vB")
        }
      }
    }

  @Test
  fun `chat call with history including thought summary and signature`() {
    var capturedRequest: HttpRequestData? = null
    goldenDevAPIStreamingFiles(
      responses =
        listOf(
            "streaming-success-thinking-function-call-thought-summary-signature.txt",
            "streaming-success-thinking-function-call-thought-summary-signature.txt"
          )
          .map { ResponseInfo(it) },
      requestHandler = { capturedRequest = it }
    ) {
      val chat = model.startChat()
      val firstPrompt = content { text("first prompt") }
      val secondPrompt = content { text("second prompt") }
      val responses = chat.sendMessageStream(firstPrompt)

      withTimeout(testTimeout) {
        val responseList = responses.toList()
        responseList.shouldNotBeEmpty()

        chat.history.let { history ->
          history.contains(firstPrompt)
          val functionCallPart =
            history.flatMap { it.parts }.first { it is FunctionCallPart } as FunctionCallPart
          functionCallPart.let {
            it.thoughtSignature.shouldNotBeNull()
            it.thoughtSignature.shouldStartWith("CiIBVKhc7vB")
          }
        }

        // Reset the request so we can be sure we capture the latest version
        capturedRequest = null

        // We don't care about the response, only the request
        val unused = chat.sendMessageStream(secondPrompt).toList()

        // Make sure the history contains all prompts seen so far
        chat.history.contains(firstPrompt)
        chat.history.contains(secondPrompt)

        // Put the captured request into a `val` to enable smart casting
        val request = capturedRequest
        request.shouldNotBeNull()
        val bodyAsString = request.body.toByteArray().decodeToString()
        bodyAsString.shouldNotBeNull()

        val rootElement = Json.parseToJsonElement(bodyAsString).jsonObject

        // Traverse the tree: contents -> parts -> thoughtSignature
        val contents = rootElement["contents"]?.jsonArray

        val signature =
          contents?.firstNotNullOfOrNull { content ->
            content.jsonObject["parts"]?.jsonArray?.firstNotNullOfOrNull { part ->
              // resulting value is a JsonPrimitive, so we use .content to get the string
              part.jsonObject["thoughtSignature"]?.jsonPrimitive?.content
            }
          }

        signature.shouldNotBeNull()
        signature.shouldStartWith("CiIBVKhc7vB")
      }
    }
  }

  @Test
  fun `prompt blocked for safety`() =
    goldenDevAPIStreamingFile("streaming-failure-prompt-blocked-safety.txt") {
      val responses = model.generateContentStream("prompt")

      withTimeout(testTimeout) {
        val exception = shouldThrow<PromptBlockedException> { responses.collect() }
        exception.response?.promptFeedback?.blockReason shouldBe BlockReason.SAFETY
      }
    }

  @Test
  fun `citation parsed correctly`() =
    goldenDevAPIStreamingFile("streaming-success-citations.txt") {
      val responses = model.generateContentStream("prompt")

      withTimeout(testTimeout) {
        val responseList = responses.toList()
        responseList.any {
          it.candidates.any { it.citationMetadata?.citations?.isNotEmpty() ?: false }
        } shouldBe true
      }
    }

  @Test
  fun `stopped for recitation`() =
    goldenDevAPIStreamingFile("streaming-failure-recitation-no-content.txt") {
      val responses = model.generateContentStream("prompt")

      withTimeout(testTimeout) {
        val exception = shouldThrow<ResponseStoppedException> { responses.collect() }
        exception.response.candidates.first().finishReason shouldBe FinishReason.RECITATION
      }
    }

  @Test
  fun `image rejected`() =
    goldenDevAPIStreamingFile("streaming-failure-image-rejected.txt", HttpStatusCode.BadRequest) {
      val responses = model.generateContentStream("prompt")

      withTimeout(testTimeout) { shouldThrow<ServerException> { responses.collect() } }
    }
}
