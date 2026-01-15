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

package com.google.firebase.ai

import com.google.firebase.ai.type.BlockReason
import com.google.firebase.ai.type.ContentBlockedException
import com.google.firebase.ai.type.ContentModality
import com.google.firebase.ai.type.FinishReason
import com.google.firebase.ai.type.FunctionCallPart
import com.google.firebase.ai.type.HarmCategory
import com.google.firebase.ai.type.HarmProbability
import com.google.firebase.ai.type.HarmSeverity
import com.google.firebase.ai.type.InvalidAPIKeyException
import com.google.firebase.ai.type.ModalityTokenCount
import com.google.firebase.ai.type.PromptBlockedException
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.ResponseStoppedException
import com.google.firebase.ai.type.SerializationException
import com.google.firebase.ai.type.ServerException
import com.google.firebase.ai.type.ServiceDisabledException
import com.google.firebase.ai.type.TextPart
import com.google.firebase.ai.type.UnsupportedUserLocationException
import com.google.firebase.ai.type.UrlRetrievalStatus
import com.google.firebase.ai.util.goldenVertexUnaryFile
import com.google.firebase.ai.util.shouldNotBeNullOrEmpty
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.inspectors.forAtLeastOne
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(PublicPreviewAPI::class)
@RunWith(RobolectricTestRunner::class)
internal class VertexAIUnarySnapshotTests {
  private val testTimeout = 5.seconds

  @Test
  fun `short reply`() =
    goldenVertexUnaryFile("unary-success-basic-reply-short.json") {
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
    goldenVertexUnaryFile("unary-success-basic-reply-long.json") {
      withTimeout(testTimeout) {
        val response = model.generateContent("prompt")

        response.candidates.isEmpty() shouldBe false
        response.candidates.first().finishReason shouldBe FinishReason.STOP
        response.candidates.first().content.parts.isEmpty() shouldBe false
        response.candidates.first().safetyRatings.isEmpty() shouldBe false
      }
    }

  @Test
  fun `response including an empty part is handled gracefully`() =
    goldenVertexUnaryFile("unary-success-empty-part.json") {
      withTimeout(testTimeout) {
        val response = model.generateContent("prompt")

        response.candidates.isEmpty() shouldBe false
        response.text.shouldNotBeEmpty()
        response.candidates.first().finishReason shouldBe FinishReason.STOP
        response.candidates.first().content.parts.isEmpty() shouldBe false
      }
    }

  @Test
  fun `response with detailed token-based usageMetadata`() =
    goldenVertexUnaryFile("unary-success-basic-response-long-usage-metadata.json") {
      withTimeout(testTimeout) {
        val response = model.generateContent("prompt")

        response.candidates.isEmpty() shouldBe false
        response.candidates.first().finishReason shouldBe FinishReason.STOP
        response.candidates.first().content.parts.isEmpty() shouldBe false
        response.usageMetadata shouldNotBe null
        response.usageMetadata?.apply {
          totalTokenCount shouldBe 1913
          candidatesTokenCount shouldBe 76
          promptTokensDetails?.forAtLeastOne {
            it.modality shouldBe ContentModality.IMAGE
            it.tokenCount shouldBe 1806
          }
          candidatesTokensDetails?.forAtLeastOne {
            it.modality shouldBe ContentModality.TEXT
            it.tokenCount shouldBe 76
          }
        }
      }
    }

  @Test
  fun `unknown enum in safety ratings`() =
    goldenVertexUnaryFile("unary-success-unknown-enum-safety-ratings.json") {
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
  fun `invalid safety ratings during image generation`() =
    goldenVertexUnaryFile("unary-success-image-invalid-safety-ratings.json") {
      withTimeout(testTimeout) {
        val response = model.generateContent("prompt")

        response.candidates.isEmpty() shouldBe false
      }
    }

  @Test
  fun `unknown enum in finish reason`() =
    goldenVertexUnaryFile("unary-failure-unknown-enum-finish-reason.json") {
      withTimeout(testTimeout) {
        shouldThrow<ResponseStoppedException> { model.generateContent("prompt") } should
          {
            it.response.candidates.first().finishReason shouldBe FinishReason.UNKNOWN
          }
      }
    }

  @Test
  fun `unknown enum in block reason`() =
    goldenVertexUnaryFile("unary-failure-unknown-enum-prompt-blocked.json") {
      withTimeout(testTimeout) {
        shouldThrow<PromptBlockedException> { model.generateContent("prompt") } should
          {
            it.response?.promptFeedback?.blockReason shouldBe BlockReason.UNKNOWN
          }
      }
    }

  @Test
  fun `quotes escaped`() =
    goldenVertexUnaryFile("unary-success-quote-reply.json") {
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
    goldenVertexUnaryFile("unary-success-missing-safety-ratings.json") {
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
    goldenVertexUnaryFile("unary-success-including-severity.json") {
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
  fun `function call has no arguments field`() =
    goldenVertexUnaryFile("unary-success-function-call-empty-arguments.json") {
      withTimeout(testTimeout) {
        val response = model.generateContent("prompt")
        val content = response.candidates.shouldNotBeNullOrEmpty().first().content
        content.shouldNotBeNull()
        val callPart = content.parts.shouldNotBeNullOrEmpty().first() as FunctionCallPart

        callPart.name shouldBe "current_time"
        callPart.args shouldBe emptyMap()
      }
    }

  @Test
  fun `prompt blocked for safety`() =
    goldenVertexUnaryFile("unary-failure-prompt-blocked-safety.json") {
      withTimeout(testTimeout) {
        shouldThrow<PromptBlockedException> { model.generateContent("prompt") } should
          {
            it.response?.promptFeedback?.blockReason shouldBe BlockReason.SAFETY
          }
      }
    }

  @Test
  fun `prompt blocked for safety with message`() =
    goldenVertexUnaryFile("unary-failure-prompt-blocked-safety-with-message.json") {
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
    goldenVertexUnaryFile("unary-failure-empty-content.json") {
      withTimeout(testTimeout) {
        val response = model.generateContent("prompt")
        response.candidates.shouldNotBeEmpty()
        response.candidates.first().content.parts.shouldBeEmpty()
      }
    }

  @Test
  fun `http error`() =
    goldenVertexUnaryFile("unary-failure-http-error.json", HttpStatusCode.PreconditionFailed) {
      withTimeout(testTimeout) { shouldThrow<ServerException> { model.generateContent("prompt") } }
    }

  @Test
  fun `user location error`() =
    goldenVertexUnaryFile(
      "unary-failure-unsupported-user-location.json",
      HttpStatusCode.PreconditionFailed,
    ) {
      withTimeout(testTimeout) {
        shouldThrow<UnsupportedUserLocationException> { model.generateContent("prompt") }
      }
    }

  @Test
  fun `stopped for safety`() =
    goldenVertexUnaryFile("unary-failure-finish-reason-safety.json") {
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
  fun `quota exceeded`() =
    goldenVertexUnaryFile("unary-failure-quota-exceeded.json", HttpStatusCode.BadRequest) {
      withTimeout(testTimeout) {
        shouldThrow<QuotaExceededException> { model.generateContent("prompt") }
      }
    }

  @Test
  fun `stopped for safety with no content`() =
    goldenVertexUnaryFile("unary-failure-finish-reason-safety-no-content.json") {
      withTimeout(testTimeout) {
        val exception = shouldThrow<ResponseStoppedException> { model.generateContent("prompt") }
        exception.response.candidates.first().finishReason shouldBe FinishReason.SAFETY
      }
    }

  @Test
  fun `citation returns correctly`() =
    goldenVertexUnaryFile("unary-success-citations.json") {
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
    goldenVertexUnaryFile("unary-success-citations-nolicense.json") {
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
    goldenVertexUnaryFile("unary-success-usage-metadata.json") {
      withTimeout(testTimeout) {
        val response = model.generateContent("prompt")

        response.candidates.isEmpty() shouldBe false
        response.candidates.first().finishReason shouldBe FinishReason.STOP
        response.usageMetadata shouldNotBe null
        response.usageMetadata?.totalTokenCount shouldBe 363
        response.usageMetadata?.promptTokensDetails?.isEmpty() shouldBe true
      }
    }

  @Test
  fun `response includes partial usage metadata`() =
    goldenVertexUnaryFile("unary-success-partial-usage-metadata.json") {
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
    fun `response includes implicit cached metadata`() =
        goldenVertexUnaryFile("unary-success-implicit-caching.json") {
            withTimeout(testTimeout) {
                val response = model.generateContent("prompt")

                response.candidates.isEmpty() shouldBe false
                response.candidates.first().finishReason shouldBe FinishReason.STOP
                response.usageMetadata shouldNotBe null
                response.usageMetadata?.let {
                    it.promptTokenCount shouldBe 12013
                    it.candidatesTokenCount shouldBe 15
                    it.cachedContentTokenCount shouldBe 11243
                    it.cacheTokensDetails.first().let { count ->
                        count.modality shouldBe ContentModality.TEXT
                        count.tokenCount shouldBe 11243
                    }
                }
            }
        }

  @Test
  fun `properly translates json text`() =
    goldenVertexUnaryFile("unary-success-constraint-decoding-json.json") {
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
    goldenVertexUnaryFile("unary-failure-invalid-response.json") {
      withTimeout(testTimeout) {
        shouldThrow<SerializationException> { model.generateContent("prompt") }
      }
    }

  @Test
  fun `response including an unknown part is handled gracefully`() =
    goldenVertexUnaryFile("unary-failure-malformed-content.json") {
      withTimeout(testTimeout) {
        val response = model.generateContent("prompt")
        response.candidates.shouldNotBeEmpty()
        response.candidates.first().content.parts.shouldBeEmpty()
      }
    }

  @Test
  fun `invalid api key`() =
    goldenVertexUnaryFile("unary-failure-api-key.json", HttpStatusCode.BadRequest) {
      withTimeout(testTimeout) {
        shouldThrow<InvalidAPIKeyException> { model.generateContent("prompt") }
      }
    }

  @Test
  fun `image rejected`() =
    goldenVertexUnaryFile("unary-failure-image-rejected.json", HttpStatusCode.BadRequest) {
      withTimeout(testTimeout) { shouldThrow<ServerException> { model.generateContent("prompt") } }
    }

  @Test
  fun `unknown model`() =
    goldenVertexUnaryFile("unary-failure-unknown-model.json", HttpStatusCode.NotFound) {
      withTimeout(testTimeout) { shouldThrow<ServerException> { model.generateContent("prompt") } }
    }

  @Test
  fun `service disabled`() =
    goldenVertexUnaryFile(
      "unary-failure-firebaseml-api-not-enabled.json",
      HttpStatusCode.Forbidden
    ) {
      withTimeout(testTimeout) {
        shouldThrow<ServiceDisabledException> { model.generateContent("prompt") }
      }
    }

  @Test
  fun `function call contains null param`() =
    goldenVertexUnaryFile("unary-success-function-call-null.json") {
      withTimeout(testTimeout) {
        val response = model.generateContent("prompt")
        val callPart = (response.candidates.first().content.parts.first() as FunctionCallPart)

        callPart.args["season"] shouldBe JsonPrimitive(null)
      }
    }

  @Test
  fun `function call contains json literal`() =
    goldenVertexUnaryFile("unary-success-function-call-json-literal.json") {
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
    goldenVertexUnaryFile("unary-success-function-call-complex-json-literal.json") {
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
    goldenVertexUnaryFile("unary-success-function-call-no-arguments.json") {
      withTimeout(testTimeout) {
        val response = model.generateContent("prompt")
        val callPart = response.functionCalls.shouldNotBeEmpty().first()

        callPart.name shouldBe "current_time"
        callPart.args.isEmpty() shouldBe true
      }
    }

  @Test
  fun `function call contains arguments`() =
    goldenVertexUnaryFile("unary-success-function-call-with-arguments.json") {
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
    goldenVertexUnaryFile("unary-success-function-call-parallel-calls.json") {
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
    goldenVertexUnaryFile("unary-success-function-call-mixed-content.json") {
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
    goldenVertexUnaryFile("unary-success-total-tokens.json") {
      withTimeout(testTimeout) {
        val response = model.countTokens("prompt")

        response.totalTokens shouldBe 6
        response.totalBillableCharacters shouldBe 16
        response.promptTokensDetails.isEmpty() shouldBe true
      }
    }

  @Test
  fun `countTokens with modality fields returned`() =
    goldenVertexUnaryFile("unary-success-detailed-token-response.json") {
      withTimeout(testTimeout) {
        val response = model.countTokens("prompt")

        response.totalTokens shouldBe 1837
        response.totalBillableCharacters shouldBe 117
        response.promptTokensDetails shouldNotBe null
        response.promptTokensDetails?.forAtLeastOne {
          it.modality shouldBe ContentModality.IMAGE
          it.tokenCount shouldBe 1806
        }
      }
    }

  @Test
  fun `countTokens succeeds with no billable characters`() =
    goldenVertexUnaryFile("unary-success-no-billable-characters.json") {
      withTimeout(testTimeout) {
        val response = model.countTokens("prompt")

        response.totalTokens shouldBe 258
        response.totalBillableCharacters shouldBe 0
      }
    }

  @Test
  fun `countTokens fails with model not found`() =
    goldenVertexUnaryFile("unary-failure-model-not-found.json", HttpStatusCode.NotFound) {
      withTimeout(testTimeout) { shouldThrow<ServerException> { model.countTokens("prompt") } }
    }

  @Test
  fun `generateImages should throw when all images filtered`() =
    goldenVertexUnaryFile("unary-failure-generate-images-all-filtered.json") {
      withTimeout(testTimeout) {
        shouldThrow<ContentBlockedException> { imagenModel.generateImages("prompt") }
      }
    }

  @Test
  fun `generateImages should throw when prompt blocked`() =
    goldenVertexUnaryFile(
      "unary-failure-generate-images-prompt-blocked.json",
      HttpStatusCode.BadRequest,
    ) {
      withTimeout(testTimeout) {
        shouldThrow<PromptBlockedException> { imagenModel.generateImages("prompt") }
      }
    }

  @Test
  fun `generateImages should contain safety data`() =
    goldenVertexUnaryFile("unary-success-generate-images-safety_info.json") {
      withTimeout(testTimeout) {
        val response = imagenModel.generateImages("prompt")
        // There is no public API, but if it parses then success
      }
    }

  @Test
  fun `google search grounding metadata is parsed correctly`() =
    goldenVertexUnaryFile("unary-success-google-search-grounding.json") {
      withTimeout(testTimeout) {
        val response = model.generateContent("prompt")

        response.candidates.shouldNotBeEmpty()
        val candidate = response.candidates.first()
        candidate.finishReason shouldBe FinishReason.STOP

        val groundingMetadata = candidate.groundingMetadata
        groundingMetadata.shouldNotBeNull()

        groundingMetadata.webSearchQueries.first() shouldBe "current weather in London"
        groundingMetadata.searchEntryPoint.shouldNotBeNull()
        groundingMetadata.searchEntryPoint?.renderedContent.shouldNotBeEmpty()

        groundingMetadata.groundingChunks.shouldNotBeEmpty()
        val groundingChunk = groundingMetadata.groundingChunks.first()
        groundingChunk.web.shouldNotBeNull()
        groundingChunk.web?.uri.shouldNotBeEmpty()
        groundingChunk.web?.title shouldBe "accuweather.com"
        groundingChunk.web?.domain.shouldBeNull()

        groundingMetadata.groundingSupports.shouldNotBeEmpty()
        groundingMetadata.groundingSupports.size shouldBe 3
        val groundingSupport = groundingMetadata.groundingSupports.first()
        groundingSupport.segment.shouldNotBeNull()
        groundingSupport.segment.startIndex shouldBe 0
        groundingSupport.segment.partIndex shouldBe 0
        groundingSupport.segment.endIndex shouldBe 56
        groundingSupport.segment.text shouldBe
          "The current weather in London, United Kingdom is cloudy."
        groundingSupport.groundingChunkIndices.first() shouldBe 0

        val secondGroundingSupport = groundingMetadata.groundingSupports[1]
        secondGroundingSupport.segment.shouldNotBeNull()
        secondGroundingSupport.segment.startIndex shouldBe 57
        secondGroundingSupport.segment.partIndex shouldBe 0
        secondGroundingSupport.segment.endIndex shouldBe 123
        secondGroundingSupport.segment.text shouldBe
          "The temperature is 67°F (19°C), but it feels like 75°F (24°C)."
        secondGroundingSupport.groundingChunkIndices.first() shouldBe 1
      }
    }

  @Test
  fun `url context`() =
    goldenVertexUnaryFile("unary-success-url-context.json") {
      withTimeout(testTimeout) {
        val response = model.generateContent("prompt")

        response.candidates.shouldNotBeEmpty()
        val candidate = response.candidates.first()

        val urlContextMetadata = candidate.urlContextMetadata
        urlContextMetadata.shouldNotBeNull()

        urlContextMetadata.urlMetadata.shouldNotBeEmpty()
        urlContextMetadata.urlMetadata.shouldHaveSize(1)
        urlContextMetadata.urlMetadata[0].retrievedUrl.shouldBe("https://berkshirehathaway.com")
        urlContextMetadata.urlMetadata[0].urlRetrievalStatus.shouldBe(UrlRetrievalStatus.SUCCESS)

        val groundingMetadata = candidate.groundingMetadata
        groundingMetadata.shouldNotBeNull()

        groundingMetadata.groundingChunks.shouldNotBeEmpty()
        groundingMetadata.groundingChunks.forEach { it.web.shouldNotBeNull() }
        groundingMetadata.groundingSupports.shouldHaveSize(2)

        val usageMetadata = response.usageMetadata

        usageMetadata.shouldNotBeNull()
        usageMetadata.toolUsePromptTokenCount.shouldBeGreaterThan(0)
        usageMetadata.toolUsePromptTokensDetails
          .shouldBeEmpty() // This isn't yet supported in Vertex AI
      }
    }

  @Test
  fun `url context mixed validity`() =
    goldenVertexUnaryFile("unary-success-url-context-mixed-validity.json") {
      withTimeout(testTimeout) {
        val response = model.generateContent("prompt")

        response.candidates.shouldNotBeEmpty()
        val candidate = response.candidates.first()

        val urlContextMetadata = candidate.urlContextMetadata
        urlContextMetadata.shouldNotBeNull()

        urlContextMetadata.urlMetadata.shouldNotBeEmpty()
        urlContextMetadata.urlMetadata.shouldHaveSize(3)
        urlContextMetadata.urlMetadata[2]
          .retrievedUrl
          .shouldBe("https://a-completely-non-existent-url-for-testing.org")
        urlContextMetadata.urlMetadata[2].urlRetrievalStatus.shouldBe(UrlRetrievalStatus.ERROR)
        urlContextMetadata.urlMetadata[1].retrievedUrl.shouldBe("https://ai.google.dev")
        urlContextMetadata.urlMetadata[1].urlRetrievalStatus.shouldBe(UrlRetrievalStatus.SUCCESS)

        val groundingMetadata = candidate.groundingMetadata
        groundingMetadata.shouldNotBeNull()

        groundingMetadata.groundingChunks.shouldNotBeEmpty()
        groundingMetadata.groundingChunks.forEach { it.web.shouldNotBeNull() }
        groundingMetadata.groundingSupports.shouldHaveSize(6)

        val usageMetadata = response.usageMetadata

        usageMetadata.shouldNotBeNull()
        usageMetadata.toolUsePromptTokenCount.shouldBeGreaterThan(0)
        usageMetadata.toolUsePromptTokensDetails
          .shouldBeEmpty() // This isn't yet supported in Vertex AI
      }
    }

  // This test only applies to Vertex AI, since this is a bug in the backend.
  @Test
  fun `url context missing retrievedUrl`() =
    goldenVertexUnaryFile("unary-success-url-context-missing-retrievedurl.json") {
      withTimeout(testTimeout) {
        val response = model.generateContent("prompt")

        response.candidates.shouldNotBeEmpty()
        val candidate = response.candidates.first()

        val urlContextMetadata = candidate.urlContextMetadata
        urlContextMetadata.shouldNotBeNull()

        urlContextMetadata.urlMetadata.shouldNotBeEmpty()
        urlContextMetadata.urlMetadata.shouldHaveSize(20)
        // Not all the retrievedUrls are null. Only the last 10. We only need to check one.
        urlContextMetadata.urlMetadata.last().retrievedUrl.shouldBeNull()
        urlContextMetadata.urlMetadata.last().urlRetrievalStatus.shouldNotBeNull()
      }
    }
}
