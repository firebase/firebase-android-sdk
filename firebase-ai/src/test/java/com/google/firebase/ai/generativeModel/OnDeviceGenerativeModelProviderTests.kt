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

package com.google.firebase.ai.generativemodel

import com.google.firebase.ai.InferenceMode
import com.google.firebase.ai.InferenceSource
import com.google.firebase.ai.OnDeviceConfig
import com.google.firebase.ai.ondevice.interop.Candidate as OnDeviceCandidate
import com.google.firebase.ai.ondevice.interop.CountTokensResponse as OnDeviceCountTokensResponse
import com.google.firebase.ai.ondevice.interop.FinishReason as OnDeviceFinishReason
import com.google.firebase.ai.ondevice.interop.FirebaseAIOnDeviceNotAvailableException
import com.google.firebase.ai.ondevice.interop.GenerateContentResponse as OnDeviceGenerateContentResponse
import com.google.firebase.ai.ondevice.interop.GenerativeModel as OnDeviceGenerativeModel
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.FirebaseAIException
import com.google.firebase.ai.type.JsonSchema
import com.google.firebase.ai.type.TextPart
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

internal class OnDeviceGenerativeModelProviderTests {
  private lateinit var onDeviceModel: OnDeviceGenerativeModel
  private lateinit var onDeviceConfig: OnDeviceConfig
  private lateinit var provider: OnDeviceGenerativeModelProvider
  private val prompt = listOf(Content(parts = listOf(TextPart("test prompt"))))

  @Before
  fun setup() {
    onDeviceModel = mockk<OnDeviceGenerativeModel>()
    onDeviceConfig = OnDeviceConfig(
      mode = InferenceMode.ONLY_ON_DEVICE,
      temperature = 0.5f,
      topK = 40,
      seed = 123
    )
    provider = OnDeviceGenerativeModelProvider(onDeviceModel, onDeviceConfig)
  }

  @Test
  fun `generateContent throws when model is not available`(): Unit = runBlocking {
    coEvery { onDeviceModel.isAvailable() } returns false

    val exception = shouldThrow<FirebaseAIException> {
      provider.generateContent(prompt)
    }
    exception.cause!!::class shouldBe FirebaseAIOnDeviceNotAvailableException::class
  }

  @Test
  fun `generateContent returns response when successful`(): Unit = runBlocking {
    coEvery { onDeviceModel.isAvailable() } returns true
    val onDeviceResponse = OnDeviceGenerateContentResponse(
      listOf(OnDeviceCandidate("generated text", OnDeviceFinishReason.STOP))
    )
    coEvery { onDeviceModel.generateContent(any()) } returns onDeviceResponse

    val response = provider.generateContent(prompt)

    response.text shouldBe "generated text"
    response.inferenceSource shouldBe InferenceSource.ON_DEVICE
  }

  @Test
  fun `countTokens returns count when successful`(): Unit = runBlocking {
    coEvery { onDeviceModel.isAvailable() } returns true
    val onDeviceResponse = OnDeviceCountTokensResponse(42)
    coEvery { onDeviceModel.countTokens(any()) } returns onDeviceResponse

    val response = provider.countTokens(prompt)

    response.totalTokens shouldBe 42
  }

  @Test
  fun `generateContentStream returns flow when successful`(): Unit = runBlocking {
    coEvery { onDeviceModel.isAvailable() } returns true
    val onDeviceResponse = OnDeviceGenerateContentResponse(
      listOf(OnDeviceCandidate("streamed text", OnDeviceFinishReason.STOP))
    )
    every { onDeviceModel.generateContentStream(any()) } returns flowOf(onDeviceResponse)

    val response = provider.generateContentStream(prompt).first()

    response.text shouldBe "streamed text"
    response.inferenceSource shouldBe InferenceSource.ON_DEVICE
  }

  @Test
  fun `generateObject always throws FirebaseAIException`(): Unit = runBlocking {
    val schema = mockk<JsonSchema<Any>>()

    val exception = shouldThrow<FirebaseAIException> {
      provider.generateObject(schema, prompt)
    }
    exception.cause!!::class shouldBe IllegalArgumentException::class
  }

  @Test
  fun `generateContent throws when prompt is empty`(): Unit = runBlocking {
    coEvery { onDeviceModel.isAvailable() } returns true

    val exception = shouldThrow<FirebaseAIException> {
      provider.generateContent(emptyList())
    }
    exception.cause!!::class shouldBe IllegalArgumentException::class
  }

  @Test
  fun `generateContent throws when prompt has no text parts`(): Unit = runBlocking {
    coEvery { onDeviceModel.isAvailable() } returns true
    val promptNoText = listOf(Content(parts = emptyList()))

    val exception = shouldThrow<FirebaseAIException> {
      provider.generateContent(promptNoText)
    }
    exception.cause!!::class shouldBe IllegalArgumentException::class
  }
}
