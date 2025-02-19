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

package com.google.firebase.vertexai.common

import com.google.firebase.vertexai.common.util.goldenUnaryFile
import com.google.firebase.vertexai.common.util.shouldNotBeNullOrEmpty
import com.google.firebase.vertexai.type.BlockReason
import com.google.firebase.vertexai.type.FinishReason
import com.google.firebase.vertexai.type.FunctionCallPart
import com.google.firebase.vertexai.type.HarmCategory
import com.google.firebase.vertexai.type.HarmProbability
import com.google.firebase.vertexai.type.HarmSeverity
import com.google.firebase.vertexai.type.TextPart
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.HttpStatusCode
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test

@Serializable internal data class MountainColors(val name: String, val colors: List<String>)

internal class UnarySnapshotTests {
  private val testTimeout = 5.seconds

  @OptIn(ExperimentalSerializationApi::class)
  @Test
  fun `short reply`() =
    goldenUnaryFile("success-basic-reply-short.json") {
      withTimeout(testTimeout) {
        val response = apiController.generateContent(textGenerateContentRequest("prompt"))

        response.candidates?.isEmpty() shouldBe false
        response.candidates?.first()?.finishReason shouldBe FinishReason.Internal.STOP
        response.candidates?.first()?.content?.parts?.isEmpty() shouldBe false
        response.candidates?.first()?.safetyRatings?.isEmpty() shouldBe false
      }
    }

  @OptIn(ExperimentalSerializationApi::class)
  @Test
  fun `long reply`() =
    goldenUnaryFile("success-basic-reply-long.json") {
      withTimeout(testTimeout) {
        val response = apiController.generateContent(textGenerateContentRequest("prompt"))

        response.candidates?.isEmpty() shouldBe false
        response.candidates?.first()?.finishReason shouldBe FinishReason.Internal.STOP
        response.candidates?.first()?.content?.parts?.isEmpty() shouldBe false
        response.candidates?.first()?.safetyRatings?.isEmpty() shouldBe false
      }
    }

  @Test
  fun `unknown enum`() =
    goldenUnaryFile("success-unknown-enum.json") {
      withTimeout(testTimeout) {
        val response = apiController.generateContent(textGenerateContentRequest("prompt"))

        response.candidates?.isNullOrEmpty() shouldBe false
        val candidate = response.candidates?.first()
        candidate?.safetyRatings?.any { it.category == HarmCategory.Internal.UNKNOWN } shouldBe true
      }
    }

  @Test
  fun `safetyRatings including severity`() =
    goldenUnaryFile("success-including-severity.json") {
      withTimeout(testTimeout) {
        val response = apiController.generateContent(textGenerateContentRequest("prompt"))

        response.candidates?.isEmpty() shouldBe false
        response.candidates?.first()?.safetyRatings?.isEmpty() shouldBe false
        response.candidates?.first()?.safetyRatings?.all {
          it.probability == HarmProbability.Internal.NEGLIGIBLE
        } shouldBe true
        response.candidates?.first()?.safetyRatings?.all { it.probabilityScore != null } shouldBe
          true
        response.candidates?.first()?.safetyRatings?.all {
          it.severity == HarmSeverity.Internal.NEGLIGIBLE
        } shouldBe true
        response.candidates?.first()?.safetyRatings?.all { it.severityScore != null } shouldBe true
      }
    }

  @Test
  fun `prompt blocked for safety`() =
    goldenUnaryFile("failure-prompt-blocked-safety.json") {
      withTimeout(testTimeout) {
        shouldThrow<PromptBlockedException> {
          apiController.generateContent(textGenerateContentRequest("prompt"))
        } should { it.response?.promptFeedback?.blockReason shouldBe BlockReason.Internal.SAFETY }
      }
    }

  @Test
  fun `empty content`() =
    goldenUnaryFile("failure-empty-content.json") {
      withTimeout(testTimeout) {
        shouldThrow<SerializationException> {
          apiController.generateContent(textGenerateContentRequest("prompt"))
        }
      }
    }

  @Test
  fun `http error`() =
    goldenUnaryFile("failure-http-error.json", HttpStatusCode.PreconditionFailed) {
      withTimeout(testTimeout) {
        shouldThrow<ServerException> {
          apiController.generateContent(textGenerateContentRequest("prompt"))
        }
      }
    }

  @Test
  fun `user location error`() =
    goldenUnaryFile("failure-unsupported-user-location.json", HttpStatusCode.PreconditionFailed) {
      withTimeout(testTimeout) {
        shouldThrow<UnsupportedUserLocationException> {
          apiController.generateContent(textGenerateContentRequest("prompt"))
        }
      }
    }

  @Test
  fun `stopped for safety`() =
    goldenUnaryFile("failure-finish-reason-safety.json") {
      withTimeout(testTimeout) {
        val exception =
          shouldThrow<ResponseStoppedException> {
            apiController.generateContent(textGenerateContentRequest("prompt"))
          }
        exception.response.candidates?.first()?.finishReason shouldBe FinishReason.Internal.SAFETY
      }
    }

  @Test
  fun `citation returns correctly`() =
    goldenUnaryFile("success-citations.json") {
      withTimeout(testTimeout) {
        val response = apiController.generateContent(textGenerateContentRequest("prompt"))

        response.candidates?.isEmpty() shouldBe false
        response.candidates?.first()?.citationMetadata?.citationSources?.isNotEmpty() shouldBe true
      }
    }

  @Test
  fun `citation returns correctly with missing license and startIndex`() =
    goldenUnaryFile("success-citations-nolicense.json") {
      withTimeout(testTimeout) {
        val response = apiController.generateContent(textGenerateContentRequest("prompt"))

        response.candidates?.isEmpty() shouldBe false
        response.candidates?.first()?.citationMetadata?.citationSources?.isNotEmpty() shouldBe true
        // Verify the values in the citation source
        with(response.candidates?.first()?.citationMetadata?.citationSources?.first()!!) {
          license shouldBe null
          startIndex shouldBe 0
        }
      }
    }

  @Test
  fun `response includes usage metadata`() =
    goldenUnaryFile("success-usage-metadata.json") {
      withTimeout(testTimeout) {
        val response = apiController.generateContent(textGenerateContentRequest("prompt"))

        response.candidates?.isEmpty() shouldBe false
        response.candidates?.first()?.finishReason shouldBe FinishReason.Internal.STOP
        response.usageMetadata shouldNotBe null
        response.usageMetadata?.totalTokenCount shouldBe 363
      }
    }

  @Test
  fun `response includes partial usage metadata`() =
    goldenUnaryFile("success-partial-usage-metadata.json") {
      withTimeout(testTimeout) {
        val response = apiController.generateContent(textGenerateContentRequest("prompt"))

        response.candidates?.isEmpty() shouldBe false
        response.candidates?.first()?.finishReason shouldBe FinishReason.Internal.STOP
        response.usageMetadata shouldNotBe null
        response.usageMetadata?.promptTokenCount shouldBe 6
        response.usageMetadata?.totalTokenCount shouldBe null
      }
    }

  @Test
  fun `citation returns correctly when using alternative name`() =
    goldenUnaryFile("success-citations-altname.json") {
      withTimeout(testTimeout) {
        val response = apiController.generateContent(textGenerateContentRequest("prompt"))

        response.candidates?.isEmpty() shouldBe false
        response.candidates?.first()?.citationMetadata?.citationSources?.isNotEmpty() shouldBe true
      }
    }

  @OptIn(ExperimentalSerializationApi::class)
  @Test
  fun `properly translates json text`() =
    goldenUnaryFile("success-constraint-decoding-json.json") {
      withTimeout(testTimeout) {
        val response = apiController.generateContent(textGenerateContentRequest("prompt"))

        response.candidates?.isEmpty() shouldBe false
        with(
          response.candidates
            ?.first()
            ?.content
            ?.parts
            ?.first()
            ?.shouldBeInstanceOf<TextPart.Internal>()
        ) {
          shouldNotBeNull()
          JSON.decodeFromString<List<MountainColors>>(text).shouldNotBeEmpty()
        }
      }
    }

  @Test
  fun `invalid response`() =
    goldenUnaryFile("failure-invalid-response.json") {
      withTimeout(testTimeout) {
        shouldThrow<SerializationException> {
          apiController.generateContent(textGenerateContentRequest("prompt"))
        }
      }
    }

  @Test
  fun `malformed content`() =
    goldenUnaryFile("failure-malformed-content.json") {
      withTimeout(testTimeout) {
        shouldThrow<SerializationException> {
          apiController.generateContent(textGenerateContentRequest("prompt"))
        }
      }
    }

  @Test
  fun `invalid api key`() =
    goldenUnaryFile("failure-api-key.json", HttpStatusCode.BadRequest) {
      withTimeout(testTimeout) {
        shouldThrow<InvalidAPIKeyException> {
          apiController.generateContent(textGenerateContentRequest("prompt"))
        }
      }
    }

  @Test
  fun `quota exceeded`() =
    goldenUnaryFile("failure-quota-exceeded.json", HttpStatusCode.BadRequest) {
      withTimeout(testTimeout) {
        shouldThrow<QuotaExceededException> {
          apiController.generateContent(textGenerateContentRequest("prompt"))
        }
      }
    }

  @Test
  fun `image rejected`() =
    goldenUnaryFile("failure-image-rejected.json", HttpStatusCode.BadRequest) {
      withTimeout(testTimeout) {
        shouldThrow<ServerException> {
          apiController.generateContent(textGenerateContentRequest("prompt"))
        }
      }
    }

  @Test
  fun `unknown model`() =
    goldenUnaryFile("failure-unknown-model.json", HttpStatusCode.NotFound) {
      withTimeout(testTimeout) {
        shouldThrow<ServerException> {
          apiController.generateContent(textGenerateContentRequest("prompt"))
        }
      }
    }

  @Test
  fun `service disabled`() =
    goldenUnaryFile("failure-service-disabled.json", HttpStatusCode.Forbidden) {
      withTimeout(testTimeout) {
        shouldThrow<ServiceDisabledException> {
          apiController.generateContent(textGenerateContentRequest("prompt"))
        }
      }
    }

  @OptIn(ExperimentalSerializationApi::class)
  @Test
  fun `function call contains null param`() =
    goldenUnaryFile("success-function-call-null.json") {
      withTimeout(testTimeout) {
        val response = apiController.generateContent(textGenerateContentRequest("prompt"))
        val callPart =
          (response.candidates!!.first().content!!.parts.first() as FunctionCallPart.Internal)

        callPart.functionCall.args shouldNotBe null
        callPart.functionCall.args?.get("season") shouldBe null
      }
    }

  @OptIn(ExperimentalSerializationApi::class)
  @Test
  fun `function call contains json literal`() =
    goldenUnaryFile("success-function-call-json-literal.json") {
      withTimeout(testTimeout) {
        val response = apiController.generateContent(textGenerateContentRequest("prompt"))
        val content = response.candidates.shouldNotBeNullOrEmpty().first().content
        val callPart =
          content.let {
            it.shouldNotBeNull()
            it.parts.shouldNotBeEmpty()
            it.parts.first().shouldBeInstanceOf<FunctionCallPart.Internal>()
          }

        callPart.functionCall.args shouldNotBe null
        callPart.functionCall.args?.get("current") shouldBe JsonPrimitive(true)
      }
    }

  @OptIn(ExperimentalSerializationApi::class)
  @Test
  fun `function call has no arguments field`() =
    goldenUnaryFile("success-function-call-empty-arguments.json") {
      withTimeout(testTimeout) {
        val response = apiController.generateContent(textGenerateContentRequest("prompt"))
        val content = response.candidates.shouldNotBeNullOrEmpty().first().content
        content.shouldNotBeNull()
        val callPart = content.parts.shouldNotBeNullOrEmpty().first() as FunctionCallPart.Internal

        callPart.functionCall.name shouldBe "current_time"
        callPart.functionCall.args shouldBe null
      }
    }
}
