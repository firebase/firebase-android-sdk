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
import com.google.firebase.ai.OnDeviceModelOption
import com.google.firebase.ai.ondevice.interop.Candidate as OnDeviceCandidate
import com.google.firebase.ai.ondevice.interop.CountTokensResponse as OnDeviceCountTokensResponse
import com.google.firebase.ai.ondevice.interop.FinishReason as OnDeviceFinishReason
import com.google.firebase.ai.ondevice.interop.FirebaseAIOnDeviceInvalidRequestException
import com.google.firebase.ai.ondevice.interop.FirebaseAIOnDeviceNotAvailableException
import com.google.firebase.ai.ondevice.interop.GenerateContentResponse as OnDeviceGenerateContentResponse
import com.google.firebase.ai.ondevice.interop.GenerativeModel as OnDeviceGenerativeModel
import com.google.firebase.ai.toInterop
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.FirebaseAIException
import com.google.firebase.ai.type.JsonSchema
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.TextPart
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

@OptIn(PublicPreviewAPI::class)
internal class OnDeviceGenerativeModelProviderTests {
  private lateinit var onDeviceModel: OnDeviceGenerativeModel
  private lateinit var onDeviceConfig: OnDeviceConfig
  private lateinit var provider: OnDeviceGenerativeModelProvider
  private val prompt = listOf(Content(parts = listOf(TextPart("test prompt"))))

  @Before
  fun setup() {
    onDeviceModel = mockk<OnDeviceGenerativeModel>()
    onDeviceConfig =
      OnDeviceConfig(mode = InferenceMode.ONLY_ON_DEVICE, temperature = 0.5f, topK = 40, seed = 123)
    provider = OnDeviceGenerativeModelProvider(onDeviceModel, onDeviceConfig)
  }

  @Test
  fun `generateContent throws when model is not available`(): Unit = runBlocking {
    coEvery { onDeviceModel.isAvailable() } returns false

    val exception = shouldThrow<FirebaseAIException> { provider.generateContent(prompt) }
    exception.cause!!::class shouldBe FirebaseAIOnDeviceNotAvailableException::class
  }

  @Test
  fun `generateContent returns response when successful`(): Unit = runBlocking {
    coEvery { onDeviceModel.isAvailable() } returns true
    val onDeviceResponse =
      OnDeviceGenerateContentResponse(
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
    val onDeviceResponse =
      OnDeviceGenerateContentResponse(
        listOf(OnDeviceCandidate("streamed text", OnDeviceFinishReason.STOP))
      )
    every { onDeviceModel.generateContentStream(any()) } returns flowOf(onDeviceResponse)

    val response = provider.generateContentStream(prompt).first()

    response.text shouldBe "streamed text"
    response.inferenceSource shouldBe InferenceSource.ON_DEVICE
  }

  private data class TestMovieReview(val title: String, val rating: Int)

  @Test
  fun `generateObject delegates to onDeviceModel`(): Unit = runBlocking {
    coEvery { onDeviceModel.isAvailable() } returns true
    val dummyInstance = TestMovieReview("Inception", 5)
    val schema: JsonSchema<TestMovieReview> = mockk {
      every { clazz } returns TestMovieReview::class
    }
    val interopResponse =
      com.google.firebase.ai.ondevice.interop.GenerateObjectResponse<TestMovieReview>(
        instance = dummyInstance
      )
    coEvery {
      onDeviceModel.generateObject(any(), any<kotlin.reflect.KClass<TestMovieReview>>())
    } returns interopResponse

    val response = provider.generateObject(schema, prompt)

    response.response.inferenceSource shouldBe InferenceSource.ON_DEVICE
    response.getObject() shouldBe dummyInstance
  }

  @Test
  fun `generateContent throws when prompt is empty`(): Unit = runBlocking {
    coEvery { onDeviceModel.isAvailable() } returns true

    val exception =
      shouldThrow<com.google.firebase.ai.type.FirebaseAIOnDeviceInvalidRequestException> {
        provider.generateContent(emptyList())
      }
    exception.cause!!::class shouldBe FirebaseAIOnDeviceInvalidRequestException::class
  }

  @Test
  fun `generateContent throws when prompt has no text parts`(): Unit = runBlocking {
    coEvery { onDeviceModel.isAvailable() } returns true
    val promptNoText = listOf(Content(parts = emptyList()))

    val exception =
      shouldThrow<com.google.firebase.ai.type.FirebaseAIOnDeviceInvalidRequestException> {
        provider.generateContent(promptNoText)
      }
    exception.cause!!::class shouldBe FirebaseAIOnDeviceInvalidRequestException::class
  }

  @Test
  fun `generateContent throws when prompt has unsupported part`(): Unit = runBlocking {
    coEvery { onDeviceModel.isAvailable() } returns true
    val unknownPart =
      object : com.google.firebase.ai.type.Part {
        override val isThought: Boolean = false
      }
    val promptWithUnknownPart = listOf(Content(parts = listOf(TextPart("hello"), unknownPart)))

    val exception =
      shouldThrow<com.google.firebase.ai.type.FirebaseAIOnDeviceInvalidRequestException> {
        provider.generateContent(promptWithUnknownPart)
      }
    exception.cause!!::class shouldBe FirebaseAIOnDeviceInvalidRequestException::class
  }

  @Test
  fun `generateContent throws when prompt has multiple images`(): Unit = runBlocking {
    coEvery { onDeviceModel.isAvailable() } returns true
    val image1 = com.google.firebase.ai.type.ImagePart(mockk<android.graphics.Bitmap>())
    val image2 = com.google.firebase.ai.type.ImagePart(mockk<android.graphics.Bitmap>())
    val promptWithMultipleImages =
      listOf(Content(parts = listOf(TextPart("hello"), image1, image2)))

    val exception =
      shouldThrow<com.google.firebase.ai.type.FirebaseAIOnDeviceInvalidRequestException> {
        provider.generateContent(promptWithMultipleImages)
      }
    exception.cause!!::class shouldBe FirebaseAIOnDeviceInvalidRequestException::class
  }

  @Test
  fun `OnDeviceConfig can be constructed with OnDeviceModelOption`() {
    val config =
      OnDeviceConfig(
        mode = InferenceMode.ONLY_ON_DEVICE,
        modelOption = OnDeviceModelOption.PREVIEW_FAST
      )
    config.modelOption shouldBe OnDeviceModelOption.PREVIEW_FAST
  }

  @Test
  fun `OnDeviceModelOption toInterop maps correctly`() {
    val option = OnDeviceModelOption.PREVIEW_FAST

    val interopConfig = option.toInterop()

    interopConfig.modelConfig?.releaseStage shouldBe
      com.google.firebase.ai.ondevice.interop.ModelReleaseStage.PREVIEW
    interopConfig.modelConfig?.preference shouldBe
      com.google.firebase.ai.ondevice.interop.ModelPreference.FAST
  }

  @Test
  fun `generateContent concatenates multiple prompts with newline for chat history`(): Unit =
    runBlocking {
      coEvery { onDeviceModel.isAvailable() } returns true
      val capturedRequest = slot<com.google.firebase.ai.ondevice.interop.GenerateContentRequest>()
      coEvery { onDeviceModel.generateContent(capture(capturedRequest)) } returns
        OnDeviceGenerateContentResponse(
          listOf(OnDeviceCandidate("response text", OnDeviceFinishReason.STOP))
        )

      val multiPrompt =
        listOf(
          Content(role = "user", parts = listOf(TextPart("hello"))),
          Content(role = "model", parts = listOf(TextPart("hi there"))),
          Content(role = "user", parts = listOf(TextPart("how are you?")))
        )

      provider.generateContent(multiPrompt)

      capturedRequest.captured.text.text shouldBe "hello\nhi there\nhow are you?"
    }

  @Test
  fun `generateContent includes systemInstruction when configured`(): Unit = runBlocking {
    coEvery { onDeviceModel.isAvailable() } returns true
    val capturedRequest = slot<com.google.firebase.ai.ondevice.interop.GenerateContentRequest>()
    coEvery { onDeviceModel.generateContent(capture(capturedRequest)) } returns
      OnDeviceGenerateContentResponse(
        listOf(OnDeviceCandidate("response text", OnDeviceFinishReason.STOP))
      )

    val providerWithSystemInstruction =
      OnDeviceGenerativeModelProvider(
        onDeviceModel,
        onDeviceConfig,
        Content(parts = listOf(TextPart("You are a helpful assistant.")))
      )

    providerWithSystemInstruction.generateContent(prompt)

    capturedRequest.captured.systemInstruction?.text shouldBe "You are a helpful assistant."
  }

  @Test
  fun `generateContent concatenates multiple text parts in systemInstruction`(): Unit =
    runBlocking {
      coEvery { onDeviceModel.isAvailable() } returns true
      val capturedRequest = slot<com.google.firebase.ai.ondevice.interop.GenerateContentRequest>()
      coEvery { onDeviceModel.generateContent(capture(capturedRequest)) } returns
        OnDeviceGenerateContentResponse(
          listOf(OnDeviceCandidate("response text", OnDeviceFinishReason.STOP))
        )

      val providerWithSystemInstruction =
        OnDeviceGenerativeModelProvider(
          onDeviceModel,
          onDeviceConfig,
          Content(parts = listOf(TextPart("Be concise."), TextPart("Respond in piratical tone.")))
        )

      providerWithSystemInstruction.generateContent(prompt)

      capturedRequest.captured.systemInstruction?.text shouldBe
        "Be concise.\nRespond in piratical tone."
    }

  @Test
  fun `generateContent throws when systemInstruction has non-text part`(): Unit = runBlocking {
    coEvery { onDeviceModel.isAvailable() } returns true
    val image = com.google.firebase.ai.type.ImagePart(mockk<android.graphics.Bitmap>())
    val providerWithInvalidSystemInstruction =
      OnDeviceGenerativeModelProvider(
        onDeviceModel,
        onDeviceConfig,
        Content(parts = listOf(TextPart("system instruction"), image))
      )

    val exception =
      shouldThrow<com.google.firebase.ai.type.FirebaseAIOnDeviceInvalidRequestException> {
        providerWithInvalidSystemInstruction.generateContent(prompt)
      }
    exception.cause!!::class shouldBe FirebaseAIOnDeviceInvalidRequestException::class
  }

  @Test
  fun `generateContent includes enableThinking and maps thoughtProcess to thoughtSummary`(): Unit =
    runBlocking {
      coEvery { onDeviceModel.isAvailable() } returns true
      coEvery { onDeviceModel.isThinkingModeAvailable() } returns true
      val capturedRequest = slot<com.google.firebase.ai.ondevice.interop.GenerateContentRequest>()
      coEvery { onDeviceModel.generateContent(capture(capturedRequest)) } returns
        OnDeviceGenerateContentResponse(
          candidates = listOf(OnDeviceCandidate("final answer", OnDeviceFinishReason.STOP)),
          modelVersion = "gemini-nano-v4",
          thoughtProcess =
            listOf(
              OnDeviceCandidate("step 1", OnDeviceFinishReason.OTHER),
              OnDeviceCandidate("step 2", OnDeviceFinishReason.STOP)
            )
        )

      val thinkingProvider =
        OnDeviceGenerativeModelProvider(
          onDeviceModel,
          OnDeviceConfig(mode = InferenceMode.ONLY_ON_DEVICE, enableThinking = true)
        )

      val response = thinkingProvider.generateContent(prompt)

      capturedRequest.captured.enableThinking shouldBe true
      response.text shouldBe "final answer"
      response.thoughtSummary shouldBe "step 1 step 2"
    }

  @Test
  fun `generateContent throws when thinking is enabled but thinking mode is not available`(): Unit =
    runBlocking {
      coEvery { onDeviceModel.isAvailable() } returns true
      coEvery { onDeviceModel.isThinkingModeAvailable() } returns false

      val thinkingProvider =
        OnDeviceGenerativeModelProvider(
          onDeviceModel,
          OnDeviceConfig(mode = InferenceMode.ONLY_ON_DEVICE, enableThinking = true)
        )

      val exception = shouldThrow<FirebaseAIException> { thinkingProvider.generateContent(prompt) }
      exception.cause!!::class shouldBe FirebaseAIOnDeviceNotAvailableException::class
    }

  @Test
  fun `generateContentStream maps thoughtProcess chunks and response text chunks`(): Unit =
    runBlocking {
      coEvery { onDeviceModel.isAvailable() } returns true
      coEvery { onDeviceModel.isThinkingModeAvailable() } returns true
      val thoughtChunk =
        OnDeviceGenerateContentResponse(
          candidates = emptyList(),
          thoughtProcess =
            listOf(
              OnDeviceCandidate("thinking 1...", OnDeviceFinishReason.OTHER),
              OnDeviceCandidate("thinking 2...", OnDeviceFinishReason.OTHER)
            )
        )
      val answerChunk =
        OnDeviceGenerateContentResponse(
          candidates = listOf(OnDeviceCandidate("streamed answer", OnDeviceFinishReason.STOP)),
          thoughtProcess = listOf(OnDeviceCandidate("done thinking", OnDeviceFinishReason.STOP))
        )
      every { onDeviceModel.generateContentStream(any()) } returns flowOf(thoughtChunk, answerChunk)

      val thinkingProvider =
        OnDeviceGenerativeModelProvider(
          onDeviceModel,
          OnDeviceConfig(mode = InferenceMode.ONLY_ON_DEVICE, enableThinking = true)
        )

      val emissions = mutableListOf<com.google.firebase.ai.type.GenerateContentResponse>()
      thinkingProvider.generateContentStream(prompt).collect { emissions.add(it) }

      emissions.size shouldBe 2
      emissions[0].candidates.size shouldBe 1
      emissions[0].thoughtSummary shouldBe "thinking 1... thinking 2..."
      emissions[0].text shouldBe null
      emissions[1].thoughtSummary shouldBe "done thinking"
      emissions[1].text shouldBe "streamed answer"
    }
}
