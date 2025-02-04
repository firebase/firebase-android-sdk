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

import com.google.firebase.vertexai.common.PublicPreviewAPI
import com.google.firebase.vertexai.type.BlockReason
import com.google.firebase.vertexai.type.ContentBlockedException
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
import io.kotest.inspectors.forAtLeastOne
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeEmpty
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.HttpStatusCode
import java.util.Calendar
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONArray
import org.junit.Test

@OptIn(PublicPreviewAPI::class)
internal class UnarySnapshotTests {
  private val testTimeout = 5.seconds

  @Test
  fun `short reply`() =
    goldenUnaryFile("unary-success-basic-reply-short.json") {
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
    goldenUnaryFile("unary-success-basic-reply-long.json") {
      withTimeout(testTimeout) {
        val response = model.generateContent("prompt")

        response.candidates.isEmpty() shouldBe false
        response.candidates.first().finishReason shouldBe FinishReason.STOP
        response.candidates.first().content.parts.isEmpty() shouldBe false
        response.candidates.first().safetyRatings.isEmpty() shouldBe false
      }
    }

  @Test
  fun `unknown enum in safety ratings`() =
    goldenUnaryFile("unary-success-unknown-enum-safety-ratings.json") {
      withTimeout(testTimeout) {
        val response = model.generateContent("prompt")

        response.candidates.isEmpty() shouldBe false
        val candidate = response.candidates.first()
        candidate.safetyRatings.any { it.category == HarmCategory.UNKNOWN } shouldBe true
        response.promptFeedback?.safetyRatings?.any { it.category == HarmCategory.UNKNOWN } shouldBe
          true
      }
    }

  @Test
  fun `unknown enum in finish reason`() =
    goldenUnaryFile("unary-failure-unknown-enum-finish-reason.json") {
      withTimeout(testTimeout) {
        shouldThrow<ResponseStoppedException> { model.generateContent("prompt") } should
          {
            it.response.candidates.first().finishReason shouldBe FinishReason.UNKNOWN
          }
      }
    }

  @Test
  fun `unknown enum in block reason`() =
    goldenUnaryFile("unary-failure-unknown-enum-prompt-blocked.json") {
      withTimeout(testTimeout) {
        shouldThrow<PromptBlockedException> { model.generateContent("prompt") } should
          {
            it.response?.promptFeedback?.blockReason shouldBe BlockReason.UNKNOWN
          }
      }
    }

  @Test
  fun `quotes escaped`() =
    goldenUnaryFile("unary-success-quote-reply.json") {
      withTimeout(testTimeout) {
        val response = model.generateContent("prompt")

        response.candidates.isEmpty() shouldBe false
        response.candidates.first().content.parts.isEmpty() shouldBe false
        val part = response.candidates.first().content.parts.first() as TextPart
        part.text shouldContain "\""
      }
    }

  @Test
  fun `safetyRatings missing`() =
    goldenUnaryFile("unary-success-missing-safety-ratings.json") {
      withTimeout(testTimeout) {
        val response = model.generateContent("prompt")

        response.candidates.isEmpty() shouldBe false
        response.candidates.first().content.parts.isEmpty() shouldBe false
        response.candidates.first().safetyRatings.isEmpty() shouldBe true
        response.promptFeedback?.safetyRatings?.isEmpty() shouldBe true
      }
    }

  @Test
  fun `safetyRatings including severity`() =
    goldenUnaryFile("unary-success-including-severity.json") {
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
    goldenUnaryFile("unary-failure-prompt-blocked-safety.json") {
      withTimeout(testTimeout) {
        shouldThrow<PromptBlockedException> { model.generateContent("prompt") } should
          {
            it.response?.promptFeedback?.blockReason shouldBe BlockReason.SAFETY
          }
      }
    }

  @Test
  fun `prompt blocked for safety with message`() =
    goldenUnaryFile("unary-failure-prompt-blocked-safety-with-message.json") {
      withTimeout(testTimeout) {
        shouldThrow<PromptBlockedException> { model.generateContent("prompt") } should
          {
            it.response?.promptFeedback?.blockReason shouldBe BlockReason.SAFETY
            it.response?.promptFeedback?.blockReasonMessage shouldContain "Reasons"
          }
      }
    }

  @Test
  fun `empty content`() =
    goldenUnaryFile("unary-failure-empty-content.json") {
      withTimeout(testTimeout) {
        shouldThrow<SerializationException> { model.generateContent("prompt") }
      }
    }

  @Test
  fun `http error`() =
    goldenUnaryFile("unary-failure-http-error.json", HttpStatusCode.PreconditionFailed) {
      withTimeout(testTimeout) { shouldThrow<ServerException> { model.generateContent("prompt") } }
    }

  @Test
  fun `user location error`() =
    goldenUnaryFile(
      "unary-failure-unsupported-user-location.json",
      HttpStatusCode.PreconditionFailed,
    ) {
      withTimeout(testTimeout) {
        shouldThrow<UnsupportedUserLocationException> { model.generateContent("prompt") }
      }
    }

  @Test
  fun `stopped for safety`() =
    goldenUnaryFile("unary-failure-finish-reason-safety.json") {
      withTimeout(testTimeout) {
        val exception = shouldThrow<ResponseStoppedException> { model.generateContent("prompt") }
        exception.response.candidates.first().finishReason shouldBe FinishReason.SAFETY
        exception.response.candidates.first().safetyRatings.forAtLeastOne {
          it.category shouldBe HarmCategory.HARASSMENT
          it.probability shouldBe HarmProbability.LOW
          it.severity shouldBe HarmSeverity.LOW
        }
      }
    }

  @Test
  fun `stopped for safety with no content`() =
    goldenUnaryFile("unary-failure-finish-reason-safety-no-content.json") {
      withTimeout(testTimeout) {
        val exception = shouldThrow<ResponseStoppedException> { model.generateContent("prompt") }
        exception.response.candidates.first().finishReason shouldBe FinishReason.SAFETY
      }
    }

  @Test
  fun `citation returns correctly`() =
    goldenUnaryFile("unary-success-citations.json") {
      withTimeout(testTimeout) {
        val response = model.generateContent("prompt")

        response.candidates.isEmpty() shouldBe false
        response.candidates.first().citationMetadata?.citations?.size shouldBe 3
        response.candidates.first().citationMetadata?.citations?.forAtLeastOne {
          it.publicationDate?.get(Calendar.YEAR) shouldBe 2019
          it.publicationDate?.get(Calendar.DAY_OF_MONTH) shouldBe 10
        }
      }
    }

  @Test
  fun `citation returns correctly with missing license and startIndex`() =
    goldenUnaryFile("unary-success-citations-nolicense.json") {
      withTimeout(testTimeout) {
        val response = model.generateContent("prompt")

        response.candidates.isEmpty() shouldBe false
        response.candidates.first().citationMetadata?.citations?.isEmpty() shouldBe false
        // Verify the values in the citation source
        val firstCitation = response.candidates.first().citationMetadata?.citations?.first()
        if (firstCitation != null) {
          with(firstCitation) {
            license shouldBe null
            startIndex shouldBe 0
          }
        }
      }
    }

  @Test
  fun `response includes usage metadata`() =
    goldenUnaryFile("unary-success-usage-metadata.json") {
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
    goldenUnaryFile("unary-success-partial-usage-metadata.json") {
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
    goldenUnaryFile("unary-success-constraint-decoding-json.json") {
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
    goldenUnaryFile("unary-failure-invalid-response.json") {
      withTimeout(testTimeout) {
        shouldThrow<SerializationException> { model.generateContent("prompt") }
      }
    }

  @Test
  fun `malformed content`() =
    goldenUnaryFile("unary-failure-malformed-content.json") {
      withTimeout(testTimeout) {
        shouldThrow<SerializationException> { model.generateContent("prompt") }
      }
    }

  @Test
  fun `invalid api key`() =
    goldenUnaryFile("unary-failure-api-key.json", HttpStatusCode.BadRequest) {
      withTimeout(testTimeout) {
        shouldThrow<InvalidAPIKeyException> { model.generateContent("prompt") }
      }
    }

  @Test
  fun `image rejected`() =
    goldenUnaryFile("unary-failure-image-rejected.json", HttpStatusCode.BadRequest) {
      withTimeout(testTimeout) { shouldThrow<ServerException> { model.generateContent("prompt") } }
    }

  @Test
  fun `unknown model`() =
    goldenUnaryFile("unary-failure-unknown-model.json", HttpStatusCode.NotFound) {
      withTimeout(testTimeout) { shouldThrow<ServerException> { model.generateContent("prompt") } }
    }

  @Test
  fun `service disabled`() =
    goldenUnaryFile("unary-failure-firebaseml-api-not-enabled.json", HttpStatusCode.Forbidden) {
      withTimeout(testTimeout) {
        shouldThrow<ServiceDisabledException> { model.generateContent("prompt") }
      }
    }

  @Test
  fun `function call contains null param`() =
    goldenUnaryFile("unary-success-function-call-null.json") {
      withTimeout(testTimeout) {
        val response = model.generateContent("prompt")
        val callPart = (response.candidates.first().content.parts.first() as FunctionCallPart)

        callPart.args["season"] shouldBe JsonPrimitive(null)
      }
    }

  @Test
  fun `function call contains json literal`() =
    goldenUnaryFile("unary-success-function-call-json-literal.json") {
      withTimeout(testTimeout) {
        val response = model.generateContent("prompt")
        val content = response.candidates.shouldNotBeNullOrEmpty().first().content
        val callPart =
          content.let {
            it.shouldNotBeNull()
            it.parts.shouldNotBeEmpty()
            it.parts.first().shouldBeInstanceOf<FunctionCallPart>()
          }

        callPart.args["current"] shouldBe JsonPrimitive(true)
      }
    }

  @Test
  fun `function call with complex json literal parses correctly`() =
    goldenUnaryFile("unary-success-function-call-complex-json-literal.json") {
      withTimeout(testTimeout) {
        val response = model.generateContent("prompt")
        val content = response.candidates.shouldNotBeNullOrEmpty().first().content
        val callPart =
          content.let {
            it.shouldNotBeNull()
            it.parts.shouldNotBeEmpty()
            it.parts.first().shouldBeInstanceOf<FunctionCallPart>()
          }

        callPart.args["current"] shouldBe JsonPrimitive(true)
        callPart.args["testObject"]!!.jsonObject["testProperty"]!!.jsonPrimitive.content shouldBe
          "string property"
      }
    }

  @Test
  fun `function call contains no arguments`() =
    goldenUnaryFile("unary-success-function-call-no-arguments.json") {
      withTimeout(testTimeout) {
        val response = model.generateContent("prompt")
        val callPart = response.functionCalls.shouldNotBeEmpty().first()

        callPart.name shouldBe "current_time"
        callPart.args.isEmpty() shouldBe true
      }
    }

  @Test
  fun `function call contains arguments`() =
    goldenUnaryFile("unary-success-function-call-with-arguments.json") {
      withTimeout(testTimeout) {
        val response = model.generateContent("prompt")
        val callPart = response.functionCalls.shouldNotBeEmpty().first()

        callPart.name shouldBe "sum"
        callPart.args["x"] shouldBe JsonPrimitive(4)
        callPart.args["y"] shouldBe JsonPrimitive(5)
      }
    }

  @Test
  fun `function call with parallel calls`() =
    goldenUnaryFile("unary-success-function-call-parallel-calls.json") {
      withTimeout(testTimeout) {
        val response = model.generateContent("prompt")
        val callList = response.functionCalls

        callList.size shouldBe 3
        callList.forEach {
          it.name shouldBe "sum"
          it.args.size shouldBe 2
        }
      }
    }

  @Test
  fun `function call with mixed content`() =
    goldenUnaryFile("unary-success-function-call-mixed-content.json") {
      withTimeout(testTimeout) {
        val response = model.generateContent("prompt")
        val callList = response.functionCalls

        response.text shouldBe "The sum of [1, 2, 3] is"
        callList.size shouldBe 2
        callList.forEach { it.args.size shouldBe 2 }
      }
    }

  @Test
  fun `countTokens succeeds`() =
    goldenUnaryFile("unary-success-total-tokens.json") {
      withTimeout(testTimeout) {
        val response = model.countTokens("prompt")

        response.totalTokens shouldBe 6
        response.totalBillableCharacters shouldBe 16
      }
    }

  @Test
  fun `countTokens succeeds with no billable characters`() =
    goldenUnaryFile("unary-success-no-billable-characters.json") {
      withTimeout(testTimeout) {
        val response = model.countTokens("prompt")

        response.totalTokens shouldBe 258
        response.totalBillableCharacters shouldBe 0
      }
    }

  @Test
  fun `countTokens fails with model not found`() =
    goldenUnaryFile("unary-failure-model-not-found.json", HttpStatusCode.NotFound) {
      withTimeout(testTimeout) { shouldThrow<ServerException> { model.countTokens("prompt") } }
    }

  @Test
  fun `generateImages should throw when all images filtered`() =
    goldenUnaryFile("unary-failure-generate-images-all-filtered.json") {
      withTimeout(testTimeout) {
        shouldThrow<ContentBlockedException> { imagenModel.generateImages("prompt") }
      }
    }

  @Test
  fun `generateImages should return when some images are filtered -- gcs`() =
    goldenUnaryFile("unary-failure-generate-images-gcs-some-filtered.json") {
      withTimeout(testTimeout) {
        imagenModel.generateImages("prompt", "gcsBucket").images.isEmpty() shouldBe false
      }
    }

  @Test
  fun `generateImages should throw when prompt blocked`() =
    goldenUnaryFile(
      "unary-failure-generate-images-prompt-blocked.json",
      HttpStatusCode.BadRequest,
    ) {
      withTimeout(testTimeout) {
        shouldThrow<PromptBlockedException> { imagenModel.generateImages("prompt") }
      }
    }

  @Test
  fun `generateImages gcs should succeed`() =
    goldenUnaryFile("unary-success-generate-images-gcs.json") {
      withTimeout(testTimeout) {
        imagenModel.generateImages("prompt", "gcsBucket").images.isEmpty() shouldBe false
      }
    }
}
