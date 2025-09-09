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
import com.google.firebase.ai.type.PromptBlockedException
import com.google.firebase.ai.type.ResponseStoppedException
import com.google.firebase.ai.type.ServerException
import com.google.firebase.ai.util.goldenDevAPIStreamingFile
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeout
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
