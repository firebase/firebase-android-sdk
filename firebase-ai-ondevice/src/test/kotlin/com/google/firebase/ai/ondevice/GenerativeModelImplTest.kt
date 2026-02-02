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

package com.google.firebase.ai.ondevice

import com.google.firebase.ai.ondevice.interop.FinishReason
import com.google.firebase.ai.ondevice.interop.FirebaseAIOnDeviceException
import com.google.firebase.ai.ondevice.interop.FirebaseAIOnDeviceNotAvailableException
import com.google.firebase.ai.ondevice.interop.FirebaseAIOnDeviceUnknownException
import com.google.firebase.ai.ondevice.interop.FirebaseAiOnDeviceInvalidRequestException
import com.google.firebase.ai.ondevice.interop.GenerateContentRequest as InteropGenerateContentRequest
import com.google.firebase.ai.ondevice.interop.TextPart as InteropTextPart
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.common.GenAiException.ErrorCode
import com.google.mlkit.genai.prompt.Candidate as MlKitCandidate
import com.google.mlkit.genai.prompt.CountTokensResponse as MlKitCountTokensResponse
import com.google.mlkit.genai.prompt.GenerateContentRequest as MlKitGenerateContentRequest
import com.google.mlkit.genai.prompt.GenerativeModel as MlKitGenerativeModel
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class GenerativeModelImplTest {

  @Test
  fun `isAvailable returns true when MLKit status is AVAILABLE`() = runTest {
    val mlkitModel =
      mockk<MlKitGenerativeModel> { coEvery { checkStatus() } returns FeatureStatus.AVAILABLE }
    val model = GenerativeModelImpl(mlkitModel)

    model.isAvailable().shouldBeTrue()
  }

  @Test
  fun `isAvailable returns false when MLKit status is not AVAILABLE`() = runTest {
    val mlkitModel =
      mockk<MlKitGenerativeModel> { coEvery { checkStatus() } returns FeatureStatus.DOWNLOADING }
    val model = GenerativeModelImpl(mlkitModel)

    model.isAvailable().shouldBeFalse()
  }

  @Test
  fun `generateContent should return converted response`() = runTest {
    val mlkitModel =
      mockk<MlKitGenerativeModel> {
        coEvery { generateContent(any<MlKitGenerateContentRequest>()) } returns
          mockk {
            every { candidates } returns
              listOf(
                mockk {
                  every { text } returns "response text"
                  every { finishReason } returns MlKitCandidate.FinishReason.MAX_TOKENS
                }
              )
          }
      }
    val model = GenerativeModelImpl(mlkitModel)

    val generateContentRequest = InteropGenerateContentRequest(InteropTextPart("prompt"))
    val generateContentResponse = model.generateContent(generateContentRequest)

    generateContentResponse.apply {
      candidates shouldHaveSize 1
      candidates.first().apply {
        text shouldBe "response text"
        finishReason shouldBe FinishReason.MAX_TOKENS
      }
    }
  }

  @Test
  fun `countTokens should return converted response`() = runTest {
    val mlkitModel =
      mockk<MlKitGenerativeModel> {
        coEvery { countTokens(any()) } returns MlKitCountTokensResponse(10)
      }
    val model = GenerativeModelImpl(mlkitModel)

    val generateContentRequest = InteropGenerateContentRequest(InteropTextPart("prompt"))
    val countTokensResponse = model.countTokens(generateContentRequest)

    countTokensResponse.apply { totalTokens shouldBe 10 }
  }

  @Test
  fun `generateContentStream should emit converted responses`() = runTest {
    val mlkitModel =
      mockk<MlKitGenerativeModel> {
        coEvery { generateContentStream(any<MlKitGenerateContentRequest>()) } returns
          flowOf(
            mockk {
              every { candidates } returns
                listOf(
                  mockk {
                    every { text } returns "partial response"
                    every { finishReason } returns MlKitCandidate.FinishReason.STOP
                  }
                )
            }
          )
      }
    val model = GenerativeModelImpl(mlkitModel)

    val generateContentRequest = InteropGenerateContentRequest(InteropTextPart("prompt"))
    val generateContentResponseFlow = model.generateContentStream(generateContentRequest)

    generateContentResponseFlow.first().apply {
      candidates shouldHaveSize 1
      candidates.first().apply {
        text shouldBe "partial response"
        finishReason shouldBe FinishReason.STOP
      }
    }
  }

  @Test
  fun `getBaseModelName returns name from MLKit`() = runTest {
    val mlkitModel =
      mockk<MlKitGenerativeModel> { coEvery { getBaseModelName() } returns "gemini-3" }
    val model = GenerativeModelImpl(mlkitModel)

    val response = model.getBaseModelName()

    response shouldBe "gemini-3"
  }

  @Test
  fun `getTokenLimit returns limit from MLKit`() = runTest {
    val mlkitModel = mockk<MlKitGenerativeModel> { coEvery { getTokenLimit() } returns 4096 }
    val model = GenerativeModelImpl(mlkitModel)

    val response = model.getTokenLimit()

    response shouldBe 4096
  }

  @Test
  fun `warmup wraps GenAiException`() = runTest {
    val genAiException = GenAiException("MLKit error", null, ErrorCode.BUSY)
    val mlkitModel = mockk<MlKitGenerativeModel> { coEvery { warmup() } throws genAiException }
    val model = GenerativeModelImpl(mlkitModel)

    val exception = shouldThrow<FirebaseAIOnDeviceException> { model.warmup() }

    exception.shouldBeInstanceOf<FirebaseAIOnDeviceUnknownException>()
    exception.cause shouldBe genAiException
  }

  @Test
  fun `handleGenAiException maps INVALID_INPUT_IMAGE to InvalidRequestException`() = runTest {
    val genAiException = GenAiException("Invalid image", null, ErrorCode.INVALID_INPUT_IMAGE)
    val mlkitModel =
      mockk<MlKitGenerativeModel> {
        coEvery { generateContent(any<MlKitGenerateContentRequest>()) } throws genAiException
      }
    val model = GenerativeModelImpl(mlkitModel)

    // TODO: Use an image part. There's a bug preventing us from testing this using actual bitmaps.
    // Since we are testing the exception handlers, it doesn't make much of a difference.
    val generateContentRequest = InteropGenerateContentRequest(InteropTextPart("prompt"))
    val exception =
      shouldThrow<FirebaseAiOnDeviceInvalidRequestException> {
        model.generateContent(generateContentRequest)
      }

    exception.cause shouldBe genAiException
  }

  @Test
  fun `handleGenAiException maps NOT_AVAILABLE to NotAvailableException`() = runTest {
    val genAiException = GenAiException("Not available", null, ErrorCode.NOT_AVAILABLE)
    val mlkitModel = mockk<MlKitGenerativeModel> { coEvery { warmup() } throws genAiException }
    val model = GenerativeModelImpl(mlkitModel)

    val exception = shouldThrow<FirebaseAIOnDeviceNotAvailableException> { model.warmup() }

    exception.cause shouldBe genAiException
  }
}
