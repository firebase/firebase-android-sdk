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
import com.google.firebase.vertexai.type.FunctionCallPart
import com.google.firebase.vertexai.type.HarmCategory
import com.google.firebase.vertexai.type.HarmProbability
import com.google.firebase.vertexai.type.HarmSeverity
import com.google.firebase.vertexai.type.InvalidAPIKeyException
import com.google.firebase.vertexai.type.PromptBlockedException
import com.google.firebase.vertexai.type.ResponseStoppedException
import com.google.firebase.vertexai.type.SerializationException
import com.google.firebase.vertexai.type.ServerException
import com.google.firebase.vertexai.type.ServiceDisabledException
import com.google.firebase.vertexai.type.TextPart
import com.google.firebase.vertexai.type.UnsupportedUserLocationException
import com.google.firebase.vertexai.util.goldenUnaryFile
import com.google.firebase.vertexai.util.shouldNotBeNullOrEmpty
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeEmpty
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.HttpStatusCode
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.junit.Test

internal class UnarySnapshotTests {
  private val testTimeout = 5.seconds

  @Test
  fun `short reply`() =
    goldenUnaryFile("success-basic-reply-short.json") {
      withTimeout(testTimeout) {
        val response = model.generateContent("prompt")

        response.candidates.isEmpty() shouldBe false
        response.candidates.first().finishReason shouldBe FinishReason.STOP
        response.candidates.first().content.parts.isEmpty() shouldBe false
        response.candidates.first().safetyRatings.isEmpty() shouldBe false
      }
    }

  @Test
  fun `long reply`() =
    goldenUnaryFile("success-basic-reply-long.json") {
      withTimeout(testTimeout) {
        val response = model.generateContent("prompt")

        response.candidates.isEmpty() shouldBe false
        response.candidates.first().finishReason shouldBe FinishReason.STOP
        response.candidates.first().content.parts.isEmpty() shouldBe false
        response.candidates.first().safetyRatings.isEmpty() shouldBe false
      }
    }

  @Test
  fun `unknown enum`() =
    goldenUnaryFile("success-unknown-enum.json") {
      withTimeout(testTimeout) {
        val response = model.generateContent("prompt")

        response.candidates.isEmpty() shouldBe false
        val candidate = response.candidates.first()
        candidate.safetyRatings.any { it.category == HarmCategory.UNKNOWN } shouldBe true
      }
    }

  @Test
  fun `safetyRatings including severity`() =
    goldenUnaryFile("success-including-severity.json") {
      withTimeout(testTimeout) {
        val response = model.generateContent("prompt")

        response.candidates.isEmpty() shouldBe false
        response.candidates.first().safetyRatings.isEmpty() shouldBe false
        response.candidates.first().safetyRatings.all {
          it.probability == HarmProbability.NEGLIGIBLE
        } shouldBe true
        response.candidates.first().safetyRatings.all {
          it.severity == HarmSeverity.NEGLIGIBLE
        } shouldBe true
        response.candidates.first().safetyRatings.all { it.severityScore != null } shouldBe true
      }
    }

  @Test
  fun `prompt blocked for safety`() =
    goldenUnaryFile("failure-prompt-blocked-safety.json") {
      withTimeout(testTimeout) {
        shouldThrow<PromptBlockedException> { model.generateContent("prompt") } should
          {
            it.response.promptFeedback?.blockReason shouldBe BlockReason.SAFETY
          }
      }
    }

  @Test
  fun `empty content`() =
    goldenUnaryFile("failure-empty-content.json") {
      withTimeout(testTimeout) {
        shouldThrow<SerializationException> { model.generateContent("prompt") }
      }
    }

  @Test
  fun `http error`() =
    goldenUnaryFile("failure-http-error.json", HttpStatusCode.PreconditionFailed) {
      withTimeout(testTimeout) { shouldThrow<ServerException> { model.generateContent("prompt") } }
    }

  @Test
  fun `user location error`() =
    goldenUnaryFile("failure-unsupported-user-location.json", HttpStatusCode.PreconditionFailed) {
      withTimeout(testTimeout) {
        shouldThrow<UnsupportedUserLocationException> { model.generateContent("prompt") }
      }
    }

  @Test
  fun `stopped for safety`() =
    goldenUnaryFile("failure-finish-reason-safety.json") {
      withTimeout(testTimeout) {
        val exception = shouldThrow<ResponseStoppedException> { model.generateContent("prompt") }
        exception.response.candidates.first().finishReason shouldBe FinishReason.SAFETY
      }
    }

  @Test
  fun `citation returns correctly`() =
    goldenUnaryFile("success-citations.json") {
      withTimeout(testTimeout) {
        val response = model.generateContent("prompt")

        response.candidates.isEmpty() shouldBe false
        response.candidates.first().citationMetadata.size shouldBe 3
      }
    }

  @Test
  fun `citation returns correctly with missing license and startIndex`() =
    goldenUnaryFile("success-citations-nolicense.json") {
      withTimeout(testTimeout) {
        val response = model.generateContent("prompt")

        response.candidates.isEmpty() shouldBe false
        response.candidates.first().citationMetadata.isEmpty() shouldBe false
        // Verify the values in the citation source
        with(response.candidates.first().citationMetadata.first()) {
          license shouldBe null
          startIndex shouldBe 0
        }
      }
    }

  @Test
  fun `response includes usage metadata`() =
    goldenUnaryFile("success-usage-metadata.json") {
      withTimeout(testTimeout) {
        val response = model.generateContent("prompt")

        response.candidates.isEmpty() shouldBe false
        response.candidates.first().finishReason shouldBe FinishReason.STOP
        response.usageMetadata shouldNotBe null
        response.usageMetadata?.totalTokenCount shouldBe 363
      }
    }

  @Test
  fun `response includes partial usage metadata`() =
    goldenUnaryFile("success-partial-usage-metadata.json") {
      withTimeout(testTimeout) {
        val response = model.generateContent("prompt")

        response.candidates.isEmpty() shouldBe false
        response.candidates.first().finishReason shouldBe FinishReason.STOP
        response.usageMetadata shouldNotBe null
        response.usageMetadata?.promptTokenCount shouldBe 6
        response.usageMetadata?.totalTokenCount shouldBe 0
      }
    }

  @Test
  fun `properly translates json text`() =
    goldenUnaryFile("success-constraint-decoding-json.json") {
      val response = model.generateContent("prompt")

      response.candidates.isEmpty() shouldBe false
      with(response.candidates.first().content.parts.first().shouldBeInstanceOf<TextPart>()) {
        shouldNotBeNull()
        val jsonArr = JSONArray(text)
        jsonArr.length() shouldBe 3
        for (i in 0 until jsonArr.length()) {
          with(jsonArr.getJSONObject(i)) {
            shouldNotBeNull()
            getString("name").shouldNotBeEmpty()
            getJSONArray("colors").length() shouldBe 5
          }
        }
      }
    }

  @Test
  fun `invalid response`() =
    goldenUnaryFile("failure-invalid-response.json") {
      withTimeout(testTimeout) {
        shouldThrow<SerializationException> { model.generateContent("prompt") }
      }
    }

  @Test
  fun `malformed content`() =
    goldenUnaryFile("failure-malformed-content.json") {
      withTimeout(testTimeout) {
        shouldThrow<SerializationException> { model.generateContent("prompt") }
      }
    }

  @Test
  fun `invalid api key`() =
    goldenUnaryFile("failure-api-key.json", HttpStatusCode.BadRequest) {
      withTimeout(testTimeout) {
        shouldThrow<InvalidAPIKeyException> { model.generateContent("prompt") }
      }
    }

  @Test
  fun `image rejected`() =
    goldenUnaryFile("failure-image-rejected.json", HttpStatusCode.BadRequest) {
      withTimeout(testTimeout) { shouldThrow<ServerException> { model.generateContent("prompt") } }
    }

  @Test
  fun `unknown model`() =
    goldenUnaryFile("failure-unknown-model.json", HttpStatusCode.NotFound) {
      withTimeout(testTimeout) { shouldThrow<ServerException> { model.generateContent("prompt") } }
    }

  @Test
  fun `service disabled`() =
    goldenUnaryFile("failure-service-disabled.json", HttpStatusCode.Forbidden) {
      withTimeout(testTimeout) {
        shouldThrow<ServiceDisabledException> { model.generateContent("prompt") }
      }
    }

  @Test
  fun `function call contains null param`() =
    goldenUnaryFile("success-function-call-null.json") {
      withTimeout(testTimeout) {
        val response = model.generateContent("prompt")
        val callPart = (response.candidates.first().content.parts.first() as FunctionCallPart)

        callPart.args["season"] shouldBe null
      }
    }

  @Test
  fun `function call contains json literal`() =
    goldenUnaryFile("success-function-call-json-literal.json") {
      withTimeout(testTimeout) {
        val response = model.generateContent("prompt")
        val content = response.candidates.shouldNotBeNullOrEmpty().first().content
        val callPart =
          content.let {
            it.shouldNotBeNull()
            it.parts.shouldNotBeEmpty()
            it.parts.first().shouldBeInstanceOf<FunctionCallPart>()
          }

        callPart.args["current"] shouldBe "true"
      }
    }

  @Test
  fun `countTokens fails with model not found`() =
    goldenUnaryFile("failure-model-not-found.json", HttpStatusCode.NotFound) {
      withTimeout(testTimeout) { shouldThrow<ServerException> { model.countTokens("prompt") } }
    }
}
