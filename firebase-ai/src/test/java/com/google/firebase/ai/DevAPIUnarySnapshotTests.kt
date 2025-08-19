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

import com.google.firebase.ai.type.FinishReason
import com.google.firebase.ai.type.InvalidAPIKeyException
import com.google.firebase.ai.type.ResponseStoppedException
import com.google.firebase.ai.type.ServerException
import com.google.firebase.ai.util.goldenDevAPIUnaryFile
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.http.HttpStatusCode
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.withTimeout
import org.junit.Test

internal class DevAPIUnarySnapshotTests {
  private val testTimeout = 5.seconds

  @Test
  fun `short reply`() =
    goldenDevAPIUnaryFile("unary-success-basic-reply-short.json") {
      withTimeout(testTimeout) {
        val response = model.generateContent("prompt")

        response.candidates.isEmpty() shouldBe false
        response.candidates.first().finishReason shouldBe FinishReason.STOP
        response.candidates.first().content.parts.isEmpty() shouldBe false
      }
    }

  @Test
  fun `long reply`() =
    goldenDevAPIUnaryFile("unary-success-basic-reply-long.json") {
      withTimeout(testTimeout) {
        val response = model.generateContent("prompt")

        response.candidates.isEmpty() shouldBe false
        response.candidates.first().finishReason shouldBe FinishReason.STOP
        response.candidates.first().content.parts.isEmpty() shouldBe false
      }
    }

  @Test
  fun `citation returns correctly`() =
    goldenDevAPIUnaryFile("unary-success-citations.json") {
      withTimeout(testTimeout) {
        val response = model.generateContent("prompt")

        response.candidates.isEmpty() shouldBe false
        response.candidates.first().citationMetadata?.citations?.size shouldBe 4
        response.candidates.first().citationMetadata?.citations?.forEach {
          it.startIndex shouldNotBe null
          it.endIndex shouldNotBe null
        }
      }
    }

  @Test
  fun `response blocked for safety`() =
    goldenDevAPIUnaryFile("unary-failure-finish-reason-safety.txt") {
      withTimeout(testTimeout) {
        shouldThrow<ResponseStoppedException> { model.generateContent("prompt") } should
          {
            it.response.candidates[0].finishReason shouldBe FinishReason.SAFETY
          }
      }
    }

  @Test
  fun `invalid api key`() =
    goldenDevAPIUnaryFile("unary-failure-api-key.json", HttpStatusCode.BadRequest) {
      withTimeout(testTimeout) {
        shouldThrow<InvalidAPIKeyException> { model.generateContent("prompt") }
      }
    }
  @Test
  fun `unknown model`() =
    goldenDevAPIUnaryFile("unary-failure-unknown-model.json", HttpStatusCode.NotFound) {
      withTimeout(testTimeout) { shouldThrow<ServerException> { model.generateContent("prompt") } }
    }

  // This test case can be removed once b/422779395 is
  // fixed.
  @Test
  fun `google search grounding empty grounding chunks`() =
    goldenDevAPIUnaryFile("unary-success-google-search-grounding-empty-grounding-chunks.json") {
      withTimeout(testTimeout) {
        val response = model.generateContent("prompt")

        response.candidates.shouldNotBeEmpty()
        val candidate = response.candidates.first()
        val groundingMetadata = candidate.groundingMetadata
        groundingMetadata.shouldNotBeNull()

        groundingMetadata.groundingChunks.shouldNotBeEmpty()
        groundingMetadata.groundingChunks.forEach { it.web.shouldBeNull() }
      }
    }

  @Test
  fun `thinking function call and though signtaure`() =
    goldenDevAPIUnaryFile("unary-success-thinking-function-call-thought-summary-signature.json") {
      withTimeout(testTimeout) {
        val response = model.generateContent("prompt")

        response.candidates.isNotEmpty()
        response.thoughtSummary.shouldNotBeNull()
        response.thoughtSummary?.isNotEmpty()
        // There's no text in the response
        response.text.shouldBeNull()
        response.candidates.first().finishReason shouldBe FinishReason.STOP
        response.candidates.first().content.parts.isEmpty() shouldBe false
      }
    }
}
