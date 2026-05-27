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

import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.CountTokensResponse
import com.google.firebase.ai.type.FirebaseAIException
import com.google.firebase.ai.type.FirebaseAIOnDeviceInvalidRequestException
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.GenerateObjectResponse
import com.google.firebase.ai.type.JsonSchema
import com.google.firebase.ai.type.PromptBlockedException
import com.google.firebase.ai.type.PublicPreviewAPI
import io.kotest.assertions.throwables.shouldNotThrowAnyUnit
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowUnit
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

internal class FallbackGenerativeModelProviderTests {
  private lateinit var defaultModel: GenerativeModelProvider
  private lateinit var fallbackModel: GenerativeModelProvider
  private val prompt = listOf(Content(parts = emptyList()))

  @Before
  fun setup() {
    defaultModel = mockk<GenerativeModelProvider>()
    fallbackModel = mockk<GenerativeModelProvider>()
  }

  @Test
  fun `generateContent uses default model when successful`() = runBlocking {
    val expectedResponse: GenerateContentResponse = mockk()
    coEvery { defaultModel.generateContent(prompt) } returns expectedResponse

    val provider = FallbackGenerativeModelProvider(defaultModel, fallbackModel)
    shouldNotThrowAnyUnit {
      val response = provider.generateContent(prompt)

      response shouldBe expectedResponse
      coVerify(exactly = 0) { fallbackModel.generateContent(any()) }
    }
  }

  @Test
  fun `generateContent falls back when precondition fails`() = runBlocking {
    val expectedResponse: GenerateContentResponse = mockk()
    coEvery { fallbackModel.generateContent(prompt) } returns expectedResponse

    val provider =
      FallbackGenerativeModelProvider(defaultModel, fallbackModel, precondition = { false })
    shouldNotThrowAnyUnit {
      val response = provider.generateContent(prompt)

      response shouldBe expectedResponse
      coVerify(exactly = 0) { defaultModel.generateContent(any()) }
      coVerify { fallbackModel.generateContent(prompt) }
    }
  }

  @Test
  fun `generateContent falls back when default model throws FirebaseAIException`() = runBlocking {
    val expectedResponse: GenerateContentResponse = mockk()
    // Test using an exception that extends FirebaseAIException
    val exception = mockk<PromptBlockedException>()
    coEvery { defaultModel.generateContent(prompt) } throws exception
    coEvery { fallbackModel.generateContent(prompt) } returns expectedResponse

    val provider = FallbackGenerativeModelProvider(defaultModel, fallbackModel)
    shouldNotThrowAnyUnit {
      val response = provider.generateContent(prompt)

      response shouldBe expectedResponse
      coVerify { fallbackModel.generateContent(prompt) }
    }
  }

  @OptIn(PublicPreviewAPI::class)
  @Test
  fun `generateContent shouldn't falls back when default model throws unrelated exception`(): Unit =
    runBlocking {
      val expectedResponse: GenerateContentResponse = mockk()
      // Test using an exception that extends FirebaseAIOnDeviceException
      val exception = mockk<ArithmeticException>()
      coEvery { defaultModel.generateContent(prompt) } throws exception
      coEvery { fallbackModel.generateContent(prompt) } returns expectedResponse

      val provider = FallbackGenerativeModelProvider(defaultModel, fallbackModel)
      shouldThrowUnit<ArithmeticException> { provider.generateContent(prompt) }
    }

  @OptIn(PublicPreviewAPI::class)
  @Test
  fun `generateContent falls back when default model throws FirebaseAIOnDeviceException`() =
    runBlocking {
      val expectedResponse: GenerateContentResponse = mockk()
      // Test using an exception that extends FirebaseAIOnDeviceException
      val exception = mockk<FirebaseAIOnDeviceInvalidRequestException>()
      coEvery { defaultModel.generateContent(prompt) } throws exception
      coEvery { fallbackModel.generateContent(prompt) } returns expectedResponse

      val provider = FallbackGenerativeModelProvider(defaultModel, fallbackModel)
      shouldNotThrowAnyUnit {
        val response = provider.generateContent(prompt)

        response shouldBe expectedResponse
        coVerify { fallbackModel.generateContent(prompt) }
      }
    }

  @Test
  fun `generateContent rethrows FirebaseAIException when fallback is disabled`() = runBlocking {
    val exception = mockk<FirebaseAIException>()
    coEvery { defaultModel.generateContent(prompt) } throws exception

    val provider =
      FallbackGenerativeModelProvider(
        defaultModel,
        fallbackModel,
        shouldFallbackInException = false
      )

    shouldThrow<FirebaseAIException> { provider.generateContent(prompt) }
    coVerify(exactly = 0) { fallbackModel.generateContent(any()) }
  }

  @Test
  fun `generateContent rethrows non-FirebaseAIException`() = runBlocking {
    coEvery { defaultModel.generateContent(prompt) } throws
      IllegalArgumentException("critical error")

    val provider = FallbackGenerativeModelProvider(defaultModel, fallbackModel)

    shouldThrow<IllegalArgumentException> { provider.generateContent(prompt) }
    coVerify(exactly = 0) { fallbackModel.generateContent(any()) }
  }

  @Test
  fun `countTokens falls back when default model throws FirebaseAIException`() = runBlocking {
    val expectedResponse = CountTokensResponse(10)
    val exception = mockk<FirebaseAIException>()
    coEvery { defaultModel.countTokens(prompt) } throws exception
    coEvery { fallbackModel.countTokens(prompt) } returns expectedResponse

    val provider = FallbackGenerativeModelProvider(defaultModel, fallbackModel)
    shouldNotThrowAnyUnit {
      val response = provider.countTokens(prompt)

      response shouldBe expectedResponse
      coVerify { fallbackModel.countTokens(prompt) }
    }
  }

  @Test
  fun `generateContentStream falls back when default model throws FirebaseAIException`() =
    runBlocking {
      val expectedResponse: GenerateContentResponse = mockk()
      val fallbackFlow = flowOf(expectedResponse)
      // Test using an exception that extends FirebaseAIOnException
      val exception = mockk<PromptBlockedException>()
      // throw the exception during the flow collection
      every { defaultModel.generateContentStream(prompt) } returns flow { throw exception }
      every { fallbackModel.generateContentStream(prompt) } returns fallbackFlow

      val provider = FallbackGenerativeModelProvider(defaultModel, fallbackModel)
      shouldNotThrowAnyUnit {
        val responseFlow = provider.generateContentStream(prompt)

        responseFlow.first() shouldBe expectedResponse
        verify { fallbackModel.generateContentStream(prompt) }
      }
    }

  @OptIn(PublicPreviewAPI::class)
  @Test
  fun `generateContentStream falls back when default model throws FirebaseAIOnDeviceException`() =
    runBlocking {
      val expectedResponse: GenerateContentResponse = mockk()
      val fallbackFlow = flowOf(expectedResponse)
      // Test using an exception that extends FirebaseAIOnDeviceException
      val exception = mockk<FirebaseAIOnDeviceInvalidRequestException>()
      // throw the exception during the flow collection
      every { defaultModel.generateContentStream(prompt) } returns flow { throw exception }
      every { fallbackModel.generateContentStream(prompt) } returns fallbackFlow

      val provider = FallbackGenerativeModelProvider(defaultModel, fallbackModel)
      shouldNotThrowAnyUnit {
        val responseFlow = provider.generateContentStream(prompt)

        responseFlow.first() shouldBe expectedResponse
        verify { fallbackModel.generateContentStream(prompt) }
      }
    }

  @Test
  fun `generateContentStream rethrows non-FirebaseAIException`() = runBlocking {
    val expectedResponse: GenerateContentResponse = mockk()
    val fallbackFlow = flowOf(expectedResponse)
    val exception = mockk<ArithmeticException>()
    // throw the exception during the flow collection
    every { defaultModel.generateContentStream(prompt) } returns flow { throw exception }
    every { fallbackModel.generateContentStream(prompt) } returns fallbackFlow

    val provider = FallbackGenerativeModelProvider(defaultModel, fallbackModel)
    shouldThrow<ArithmeticException> { provider.generateContentStream(prompt).first() }

    verify(exactly = 0) { fallbackModel.generateContentStream(prompt) }
  }

  @Test
  fun `generateContentStream rethrows exception if a value was already emitted`() = runBlocking {
    val expectedResponse: GenerateContentResponse = mockk()
    val fallbackFlow = flowOf(expectedResponse)
    val exception = mockk<PromptBlockedException>()
    // throw the exception during the flow collection
    every { defaultModel.generateContentStream(prompt) } returns
      flow {
        emit(expectedResponse)
        throw exception
      }
    every { fallbackModel.generateContentStream(prompt) } returns fallbackFlow

    val provider = FallbackGenerativeModelProvider(defaultModel, fallbackModel)
    // Even though it's an exception we can fall back from, we don't since a value has been
    // generated
    // already.
    shouldThrow<PromptBlockedException> { provider.generateContentStream(prompt).collect() }

    verify(exactly = 0) { fallbackModel.generateContentStream(prompt) }
  }

  @Test
  fun `generateObject falls back when default model throws FirebaseAIException`() = runBlocking {
    val schema: JsonSchema<Any> = mockk()
    val expectedResponse: GenerateObjectResponse<Any> = mockk()
    // Test using an exception that extends
    val exception = mockk<FirebaseAIException>()
    coEvery { defaultModel.generateObject(schema, prompt) } throws exception
    coEvery { fallbackModel.generateObject(schema, prompt) } returns expectedResponse

    val provider = FallbackGenerativeModelProvider(defaultModel, fallbackModel)
    shouldNotThrowAnyUnit {
      val response = provider.generateObject(schema, prompt)

      response shouldBe expectedResponse
      coVerify { fallbackModel.generateObject(schema, prompt) }
    }
  }

  @Test
  fun `generateContent rethrows CancellationException and does not fall back`() = runBlocking {
    val exception = kotlinx.coroutines.CancellationException("cancelled")
    coEvery { defaultModel.generateContent(prompt) } throws exception

    val provider = FallbackGenerativeModelProvider(defaultModel, fallbackModel)

    shouldThrow<kotlinx.coroutines.CancellationException> { provider.generateContent(prompt) }
    coVerify(exactly = 0) { fallbackModel.generateContent(any()) }
  }

  @Test
  fun `generateContentStream rethrows CancellationException and does not fall back`() =
    runBlocking {
      val exception = kotlinx.coroutines.CancellationException("cancelled")
      every { defaultModel.generateContentStream(prompt) } returns flow { throw exception }

      val provider = FallbackGenerativeModelProvider(defaultModel, fallbackModel)

      shouldThrow<kotlinx.coroutines.CancellationException> {
        provider.generateContentStream(prompt).first()
      }
      verify(exactly = 0) { fallbackModel.generateContentStream(any()) }
    }
}
