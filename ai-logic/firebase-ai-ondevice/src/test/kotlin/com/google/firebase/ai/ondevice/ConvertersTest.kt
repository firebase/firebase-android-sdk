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
import com.google.firebase.ai.ondevice.interop.Content as InteropContent
import com.google.firebase.ai.ondevice.interop.FinishReason as InteropFinishReason
import com.google.firebase.ai.ondevice.interop.FirebaseAIOnDeviceInvalidRequestException
import com.google.firebase.ai.ondevice.interop.FunctionCallPart as InteropFunctionCallPart
import com.google.firebase.ai.ondevice.interop.FunctionDeclaration as InteropFunctionDeclaration
import com.google.firebase.ai.ondevice.interop.FunctionResponsePart as InteropFunctionResponsePart
import com.google.firebase.ai.ondevice.interop.FunctionSchema as InteropFunctionSchema
import com.google.firebase.ai.ondevice.interop.GenerateContentRequest as InteropGenerateContentRequest
import com.google.firebase.ai.ondevice.interop.TextPart as InteropTextPart
import com.google.mlkit.genai.common.FunctionArgValue
import com.google.mlkit.genai.common.FunctionCall
import com.google.mlkit.genai.prompt.Candidate
import com.google.mlkit.genai.prompt.Content as MlKitContent
import com.google.mlkit.genai.prompt.CountTokensResponse
import com.google.mlkit.genai.prompt.FunctionCallPart
import com.google.mlkit.genai.prompt.FunctionResponsePart
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.schema.tools.FunctionType
import org.junit.Assert.assertThrows
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
  fun `InteropParts toMlKit should convert function call parts correctly`() {
    val interopPart =
      InteropFunctionCallPart("fetchWeather", mapOf("city" to "London", "temp" to 20L), "id-123")
    val mlKitPart = interopPart.toMlKit()
    assertThat(mlKitPart.functionCall.name).isEqualTo("fetchWeather")
    assertThat(mlKitPart.functionCall.id).isEqualTo("id-123")
    assertThat((mlKitPart.functionCall.arguments["city"] as FunctionArgValue.StringValue).value)
      .isEqualTo("London")
    assertThat((mlKitPart.functionCall.arguments["temp"] as FunctionArgValue.LongValue).value)
      .isEqualTo(20L)
  }

  @Test
  fun `MlKit FunctionCallPart toInterop should convert correctly`() {
    val mlKitPart =
      FunctionCallPart(
        FunctionCall(
          name = "fetchWeather",
          arguments = mapOf("city" to FunctionArgValue.StringValue("London")),
          id = "id-123"
        )
      )
    val interopPart = mlKitPart.toInterop()
    assertThat(interopPart.name).isEqualTo("fetchWeather")
    assertThat(interopPart.id).isEqualTo("id-123")
    assertThat(interopPart.args).isEqualTo(mapOf("city" to "London"))
  }

  @Test
  fun `InteropParts toMlKit should convert function response parts correctly`() {
    val interopPart =
      InteropFunctionResponsePart("fetchWeather", mapOf("result" to "sunny"), "id-123")
    val mlKitPart = interopPart.toMlKit()
    assertThat(mlKitPart.name).isEqualTo("fetchWeather")
    assertThat(mlKitPart.id).isEqualTo("id-123")
    assertThat(mlKitPart.response).isEqualTo(mapOf("result" to "sunny"))
  }

  @Test
  fun `MlKit FunctionResponsePart toInterop should convert correctly`() {
    val mlKitPart =
      FunctionResponsePart(
        id = "id-123",
        name = "fetchWeather",
        response = mapOf("result" to "sunny")
      )
    val interopPart = mlKitPart.toInterop()
    assertThat(interopPart.name).isEqualTo("fetchWeather")
    assertThat(interopPart.id).isEqualTo("id-123")
    assertThat(interopPart.response).isEqualTo(mapOf("result" to "sunny"))
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
  fun `Candidate toInterop should convert functionCalls correctly`() {
    val mlKitFunctionCall =
      FunctionCall(
        name = "fetchWeather",
        arguments = mapOf("city" to FunctionArgValue.StringValue("London")),
        id = "id-123"
      )
    val mlKitCandidate =
      mock(Candidate::class.java).apply {
        `when`(text).thenReturn("")
        `when`(finishReason).thenReturn(Candidate.FinishReason.STOP)
        `when`(functionCalls).thenReturn(listOf(mlKitFunctionCall))
      }
    val interopCandidate = mlKitCandidate.toInterop()
    assertThat(interopCandidate.functionCalls).hasSize(1)
    val call = interopCandidate.functionCalls[0]
    assertThat(call.name).isEqualTo("fetchWeather")
    assertThat(call.args).isEqualTo(mapOf("city" to "London"))
    assertThat(call.id).isEqualTo("id-123")
  }

  @Test
  fun `Content Role toMlKit should map USER and MODEL correctly`() {
    assertThat(InteropContent.Role.USER.toMlKit()).isEqualTo(MlKitContent.Role.USER)
    assertThat(InteropContent.Role.MODEL.toMlKit()).isEqualTo(MlKitContent.Role.MODEL)
  }

  @Test
  fun `Content toMlKit should preserve role and parts`() {
    val userContent = InteropContent(parts = listOf(InteropTextPart("hello")))
    val mlKitUserContent = userContent.toMlKit()
    assertThat(mlKitUserContent.role).isEqualTo(MlKitContent.Role.USER)
    assertThat(mlKitUserContent.parts).containsExactly(TextPart("hello"))

    val modelContent =
      InteropContent(
        parts = listOf(InteropTextPart("hi"), InteropTextPart("there")),
        role = InteropContent.Role.MODEL
      )
    val mlKitModelContent = modelContent.toMlKit()
    assertThat(mlKitModelContent.role).isEqualTo(MlKitContent.Role.MODEL)
    assertThat(mlKitModelContent.parts).containsExactly(TextPart("hi"), TextPart("there")).inOrder()
  }

  @Test
  fun `GenerateContentRequest toMlKit should convert tools correctly`() {
    val schema =
      InteropFunctionSchema(
        type = "OBJECT",
        description = "weather params",
        properties =
          mapOf("city" to InteropFunctionSchema(type = "STRING", description = "city name")),
        required = listOf("city")
      )
    val declaration =
      InteropFunctionDeclaration(
        name = "fetchWeather",
        description = "Fetches the weather for a city",
        parameters = schema
      )
    val interopRequest =
      InteropGenerateContentRequest(
        contents = listOf(InteropContent(listOf(InteropTextPart("What is the weather?")))),
        tools = listOf(declaration)
      )
    val mlKitRequest = interopRequest.toMlKit()

    assertThat(mlKitRequest.dynamicTools).isNotNull()
    assertThat(mlKitRequest.dynamicTools).hasSize(1)
    val mlKitTool = mlKitRequest.dynamicTools!!.first()
    assertThat(mlKitTool.name).isEqualTo("fetchWeather")
    assertThat(mlKitTool.description).isEqualTo("Fetches the weather for a city")
    assertThat(mlKitTool.parameters?.type).isEqualTo(FunctionType.OBJECT)
  }

  @Test
  fun `GenerateContentRequest toMlKit should convert correctly and cover all fields`() {
    val interopRequest =
      InteropGenerateContentRequest(
        contents =
          listOf(
            InteropContent(
              parts = listOf(InteropTextPart("prompt 1"), InteropTextPart("prompt 2")),
              role = InteropContent.Role.USER
            )
          ),
        temperature = 0.7f,
        topK = 20,
        seed = 42,
        candidateCount = 1,
        maxOutputTokens = 250,
        systemInstruction = InteropTextPart("system instruction"),
        enableThinking = true
      )
    val mlKitRequest = interopRequest.toMlKit()

    assertThat(mlKitRequest.contents).hasSize(1)
    assertThat(mlKitRequest.contents[0].role).isEqualTo(MlKitContent.Role.USER)
    assertThat(mlKitRequest.contents[0].parts)
      .containsExactly(TextPart("prompt 1"), TextPart("prompt 2"))
      .inOrder()
    assertThat(mlKitRequest.temperature).isEqualTo(0.7f)
    assertThat(mlKitRequest.topK).isEqualTo(20)
    assertThat(mlKitRequest.seed).isEqualTo(42)
    assertThat(mlKitRequest.candidateCount).isEqualTo(1)
    assertThat(mlKitRequest.maxOutputTokens).isEqualTo(250)
    assertThat(mlKitRequest.systemInstruction?.textString).isEqualTo("system instruction")
    assertThat(mlKitRequest.enableThinking).isTrue()
  }

  @Test
  fun `GenerateContentRequest toMlKit should preserve multi-turn contents and roles`() {
    val interopRequest =
      InteropGenerateContentRequest(
        contents =
          listOf(
            InteropContent(
              parts = listOf(InteropTextPart("What is the weather in London?")),
              role = InteropContent.Role.USER
            ),
            InteropContent(
              parts =
                listOf(
                  InteropFunctionCallPart("fetchWeather", mapOf("city" to "London"), "call-1")
                ),
              role = InteropContent.Role.MODEL
            ),
            InteropContent(
              parts =
                listOf(
                  InteropFunctionResponsePart(
                    "fetchWeather",
                    mapOf("condition" to "sunny"),
                    "call-1"
                  )
                ),
              role = InteropContent.Role.USER
            ),
            InteropContent(
              parts = listOf(InteropTextPart("It is sunny in London.")),
              role = InteropContent.Role.MODEL
            ),
            InteropContent(
              parts = listOf(InteropTextPart("Thanks!")),
              role = InteropContent.Role.USER
            )
          )
      )

    val mlKitRequest = interopRequest.toMlKit()

    assertThat(mlKitRequest.contents).hasSize(5)
    assertThat(mlKitRequest.contents[0].role).isEqualTo(MlKitContent.Role.USER)
    assertThat(mlKitRequest.contents[0].parts)
      .containsExactly(TextPart("What is the weather in London?"))

    assertThat(mlKitRequest.contents[1].role).isEqualTo(MlKitContent.Role.MODEL)
    val mlKitCallPart = mlKitRequest.contents[1].parts.single() as FunctionCallPart
    assertThat(mlKitCallPart.functionCall.name).isEqualTo("fetchWeather")
    assertThat(mlKitCallPart.functionCall.id).isEqualTo("call-1")

    assertThat(mlKitRequest.contents[2].role).isEqualTo(MlKitContent.Role.USER)
    val mlKitResponsePart = mlKitRequest.contents[2].parts.single() as FunctionResponsePart
    assertThat(mlKitResponsePart.name).isEqualTo("fetchWeather")
    assertThat(mlKitResponsePart.response).isEqualTo(mapOf("condition" to "sunny"))
    assertThat(mlKitResponsePart.id).isEqualTo("call-1")

    assertThat(mlKitRequest.contents[3].role).isEqualTo(MlKitContent.Role.MODEL)
    assertThat(mlKitRequest.contents[3].parts).containsExactly(TextPart("It is sunny in London."))

    assertThat(mlKitRequest.contents[4].role).isEqualTo(MlKitContent.Role.USER)
    assertThat(mlKitRequest.contents[4].parts).containsExactly(TextPart("Thanks!"))
  }

  @Test
  fun `GenerateContentRequest toMlKit should convert correctly optional fields`() {
    val interopRequest =
      InteropGenerateContentRequest(
        contents = listOf(InteropContent(listOf(InteropTextPart("prompt"))))
      )
    val mlKitRequest = interopRequest.toMlKit()

    // We only assert the fields we explicitly set,
    // as ML Kit's internal defaults may vary by version or environment.
    assertThat(mlKitRequest.contents).hasSize(1)
    assertThat(mlKitRequest.contents[0].role).isEqualTo(MlKitContent.Role.USER)
    assertThat(mlKitRequest.contents[0].parts).containsExactly(TextPart("prompt"))
    assertThat(mlKitRequest.systemInstruction).isNull()
    assertThat(mlKitRequest.enableThinking).isFalse()
  }

  @Test
  fun `GenerateContentRequest toMlKit should throw when contents is empty`() {
    val interopRequest = InteropGenerateContentRequest(contents = emptyList())

    assertThrows(FirebaseAIOnDeviceInvalidRequestException::class.java) { interopRequest.toMlKit() }
  }

  @Test
  fun `GenerateContentRequest toMlKit should throw when any content has empty parts`() {
    val interopRequest =
      InteropGenerateContentRequest(
        contents =
          listOf(
            InteropContent(listOf(InteropTextPart("hello")), InteropContent.Role.USER),
            InteropContent(emptyList(), InteropContent.Role.MODEL)
          )
      )

    assertThrows(FirebaseAIOnDeviceInvalidRequestException::class.java) { interopRequest.toMlKit() }
  }

  @Test
  fun `GenerateContentResponse toInterop should convert correctly and populate modelVersion`() {
    val mlKitCandidate =
      mock(Candidate::class.java).apply {
        `when`(text).thenReturn("hello")
        `when`(finishReason).thenReturn(Candidate.FinishReason.STOP)
      }
    val mlKitThoughtCandidate =
      mock(Candidate::class.java).apply {
        `when`(text).thenReturn("reasoning step")
        `when`(finishReason).thenReturn(Candidate.FinishReason.STOP)
      }
    val mlKitResponse =
      mock(com.google.mlkit.genai.prompt.GenerateContentResponse::class.java).apply {
        `when`(candidates).thenReturn(listOf(mlKitCandidate))
        `when`(thoughtProcess).thenReturn(listOf(mlKitThoughtCandidate))
      }
    val interopResponse = mlKitResponse.toInterop("gemini-3.1-flash-lite")

    assertThat(interopResponse.candidates.size).isEqualTo(1)
    assertThat(interopResponse.candidates[0].text).isEqualTo("hello")
    assertThat(interopResponse.thoughtProcess.size).isEqualTo(1)
    assertThat(interopResponse.thoughtProcess[0].text).isEqualTo("reasoning step")
    assertThat(interopResponse.modelVersion).isEqualTo("gemini-3.1-flash-lite")
  }
}
