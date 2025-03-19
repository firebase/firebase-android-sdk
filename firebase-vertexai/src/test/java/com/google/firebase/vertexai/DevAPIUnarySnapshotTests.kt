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

import com.google.firebase.vertexai.type.BlockReason
import com.google.firebase.vertexai.type.FinishReason
import com.google.firebase.vertexai.type.InvalidAPIKeyException
import com.google.firebase.vertexai.type.PromptBlockedException
import com.google.firebase.vertexai.type.ResponseStoppedException
import com.google.firebase.vertexai.type.ServerException
import com.google.firebase.vertexai.type.TextPart
import com.google.firebase.vertexai.util.goldenDevAPIUnaryFile
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.inspectors.forAtLeastOne
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.withTimeout
import org.junit.Test
import java.util.Calendar
import kotlin.time.Duration.Companion.seconds

internal class DevAPIUnarySnapshotTests {
  private val testTimeout = 5.seconds

  @Test
  fun `short reply`() =
    goldenDevAPIUnaryFile("unary-success-basic-reply-short.txt") {
      withTimeout(testTimeout) {
        val response = model.generateContent("prompt")

        response.candidates.isEmpty() shouldBe false
        response.candidates.first().finishReason shouldBe FinishReason.STOP
        response.candidates.first().content.parts.isEmpty() shouldBe false
      }
    }

  @Test
  fun `long reply`() =
    goldenDevAPIUnaryFile("unary-success-basic-reply-long.txt") {
      withTimeout(testTimeout) {
        val response = model.generateContent("prompt")

        response.candidates.isEmpty() shouldBe false
        response.candidates.first().finishReason shouldBe FinishReason.STOP
        response.candidates.first().content.parts.isEmpty() shouldBe false
      }
    }

  @Test
  fun `quotes escaped`() =
    goldenDevAPIUnaryFile("unary-success-quote-reply.txt") {
      withTimeout(testTimeout) {
        val response = model.generateContent("prompt")

        response.candidates.isEmpty() shouldBe false
        response.candidates.first().content.parts.isEmpty() shouldBe false
        val part = response.candidates.first().content.parts.first() as TextPart
        part.text shouldContain "\""
      }
    }


  @Test
  fun `prompt blocked for safety`() =
    goldenDevAPIUnaryFile("unary-failure-prompt-blocked-safety.txt") {
      withTimeout(testTimeout) {
        shouldThrow<ResponseStoppedException> { model.generateContent("prompt") } should
          {
            it.response.candidates[0].finishReason shouldBe FinishReason.MAX_TOKENS
          }
      }
    }

  @Test
  fun `response blocked for safety`() =
    goldenDevAPIUnaryFile("unary-failure-finish-reason-safety.txt") {
      withTimeout(testTimeout) {
        shouldThrow<ResponseStoppedException> { model.generateContent("prompt") } should
                {
                  it.response.candidates[0].finishReason shouldBe FinishReason.MAX_TOKENS
                }
      }
    }

  @Test
  fun `citation returns correctly`() =
    goldenDevAPIUnaryFile("unary-success-citations.txt") {
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
  fun `invalid api key`() =
    goldenDevAPIUnaryFile("unary-failure-api-key.txt", HttpStatusCode.BadRequest) {
      withTimeout(testTimeout) {
        shouldThrow<InvalidAPIKeyException> { model.generateContent("prompt") }
      }
    }
  @Test
  fun `unknown model`() =
    goldenDevAPIUnaryFile("unary-failure-unknown-model.txt", HttpStatusCode.NotFound) {
      withTimeout(testTimeout) { shouldThrow<ServerException> { model.generateContent("prompt") } }
    }
}
