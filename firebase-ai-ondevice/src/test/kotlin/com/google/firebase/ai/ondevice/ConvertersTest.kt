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

import com.google.common.truth.Truth.assertThat
import com.google.firebase.ai.ondevice.interop.FinishReason as InteropFinishReason
import com.google.firebase.ai.ondevice.interop.GenerateContentRequest as InteropGenerateContentRequest
import com.google.firebase.ai.ondevice.interop.TextPart as InteropTextPart
import com.google.mlkit.genai.prompt.Candidate
import com.google.mlkit.genai.prompt.CountTokensResponse
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

internal class ConvertersTest {

  // TODO: Introduce a similar tests for Images. Currently using "bitmaps" is not feasible in
  // robolectric.
  @Test
  fun `InteropParts toMlKit should convert text parts correctly`() {
    // TextPart conversion
    val interopTextPart = InteropTextPart("hello")
    val mlKitTextPart = interopTextPart.toMlKit()
    assertThat(mlKitTextPart.textString).isEqualTo("hello")
  }

  @Test
  fun `CountTokensResponse toInterop should convert correctly`() {
    val mlKitCountTokensResponse = CountTokensResponse(42)
    val interopCountTokensResponse = mlKitCountTokensResponse.toInterop()
    assertThat(interopCountTokensResponse.totalTokens).isEqualTo(42)
  }

  @Test
  fun `Candidate toInterop should convert correctly with MAX_TOKENS reason`() {
    // There's no way of create a Candidate object, so mocking is the closest second.
    val mlKitCandidate =
      mock(Candidate::class.java).apply {
        `when`(text).thenReturn("truncated text")
        `when`(finishReason).thenReturn(Candidate.FinishReason.MAX_TOKENS)
      }
    val interopCandidate = mlKitCandidate.toInterop()
    assertThat(interopCandidate.text).isEqualTo("truncated text")
    assertThat(interopCandidate.finishReason).isEqualTo(InteropFinishReason.MAX_TOKENS)
  }

  @Test
  fun `Candidate toInterop should map unknown finishReason to OTHER`() {
    val mlKitCandidate =
      mock(Candidate::class.java).apply {
        `when`(text).thenReturn("some text")
        `when`(finishReason).thenReturn(999) // Unknown reason
      }
    val interopCandidate = mlKitCandidate.toInterop()
    assertThat(interopCandidate.finishReason).isEqualTo(InteropFinishReason.OTHER)
  }

  @Test
  fun `GenerateContentRequest toMlKit should convert correctly and cover all fields`() {
    val interopRequest =
      InteropGenerateContentRequest(
        text = InteropTextPart("prompt"),
        temperature = 0.7f,
        topK = 20,
        seed = 42,
        candidateCount = 1,
        maxOutputTokens = 250
      )
    val mlKitRequest = interopRequest.toMlKit()

    assertThat(mlKitRequest.text.textString).isEqualTo("prompt")
    assertThat(mlKitRequest.temperature).isEqualTo(0.7f)
    assertThat(mlKitRequest.topK).isEqualTo(20)
    assertThat(mlKitRequest.seed).isEqualTo(42)
    assertThat(mlKitRequest.candidateCount).isEqualTo(1)
    assertThat(mlKitRequest.maxOutputTokens).isEqualTo(250)
  }

  @Test
  fun `GenerateContentRequest toMlKit should convert correctly optional fields`() {
    val interopRequest = InteropGenerateContentRequest(text = InteropTextPart("prompt"))
    val mlKitRequest = interopRequest.toMlKit()

    // Documented default values
    assertThat(mlKitRequest.text.textString).isEqualTo("prompt")
    assertThat(mlKitRequest.temperature).isEqualTo(0.0f)
    assertThat(mlKitRequest.topK).isEqualTo(3)
    assertThat(mlKitRequest.seed).isEqualTo(0)
    assertThat(mlKitRequest.candidateCount).isEqualTo(1)
    assertThat(mlKitRequest.maxOutputTokens).isEqualTo(256)
  }
}
