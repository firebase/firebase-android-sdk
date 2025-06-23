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

package com.google.firebase.ai.common

import com.google.firebase.FirebaseApp
import com.google.firebase.ai.BuildConfig
import com.google.firebase.ai.common.util.commonTest
import com.google.firebase.ai.common.util.createResponses
import com.google.firebase.ai.common.util.doBlocking
import com.google.firebase.ai.common.util.prepareStreamingResponse
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.CountTokensResponse
import com.google.firebase.ai.type.FunctionCallingConfig
import com.google.firebase.ai.type.GoogleSearch
import com.google.firebase.ai.type.RequestOptions
import com.google.firebase.ai.type.TextPart
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.ToolConfig
import io.kotest.assertions.json.shouldContainJsonKey
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.content.TextContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.writeFully
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.Mockito

private val TEST_CLIENT_ID = "genai-android/test"

private val TEST_APP_ID = "1:android:12345"

private val TEST_VERSION = 1

internal class APIControllerTests {
  private val testTimeout = 5.seconds

  @Test
  fun `(generateContentStream) emits responses as they come in`() = commonTest {
    val response = createResponses("The", " world", " is", " a", " beautiful", " place!")
    val bytes = prepareStreamingResponse(response)

    bytes.forEach { channel.writeFully(it) }
    val responses = apiController.generateContentStream(textGenerateContentRequest("test"))

    withTimeout(testTimeout) {
      responses.collect {
        it.candidates?.isEmpty() shouldBe false
        channel.close()
      }
    }
  }

  @Test
  fun `(generateContent) respects a custom timeout`() =
    commonTest(requestOptions = RequestOptions(2.seconds)) {
      shouldThrow<RequestTimeoutException> {
        withTimeout(testTimeout) {
          apiController.generateContent(textGenerateContentRequest("test"))
        }
      }
    }
}

@OptIn(ExperimentalSerializationApi::class)
internal class RequestFormatTests {

  private val mockFirebaseApp = Mockito.mock<FirebaseApp>()

  @Before
  fun setup() {
    Mockito.`when`(mockFirebaseApp.isDataCollectionDefaultEnabled).thenReturn(false)
  }

  @Test
  fun `using default endpoint`() = doBlocking {
    val channel = ByteChannel(autoFlush = true)
    val mockEngine = MockEngine {
      respond(channel, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
    }
    prepareStreamingResponse(createResponses("Random")).forEach { channel.writeFully(it) }
    val controller =
      APIController(
        "super_cool_test_key",
        "gemini-pro-1.5",
        RequestOptions(),
        mockEngine,
        "genai-android/${BuildConfig.VERSION_NAME}",
        mockFirebaseApp,
        TEST_VERSION,
        TEST_APP_ID,
        null,
      )

    withTimeout(5.seconds) {
      controller.generateContentStream(textGenerateContentRequest("cats")).collect {
        it.candidates?.isEmpty() shouldBe false
        channel.close()
      }
    }

    mockEngine.requestHistory.first().url.host shouldBe "firebasevertexai.googleapis.com"
  }

  @Test
  fun `using custom endpoint`() = doBlocking {
    val channel = ByteChannel(autoFlush = true)
    val mockEngine = MockEngine {
      respond(channel, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
    }
    prepareStreamingResponse(createResponses("Random")).forEach { channel.writeFully(it) }
    val controller =
      APIController(
        "super_cool_test_key",
        "gemini-pro-1.5",
        RequestOptions(timeout = 5.seconds, endpoint = "https://my.custom.endpoint"),
        mockEngine,
        TEST_CLIENT_ID,
        mockFirebaseApp,
        TEST_VERSION,
        TEST_APP_ID,
        null,
      )

    withTimeout(5.seconds) {
      controller.generateContentStream(textGenerateContentRequest("cats")).collect {
        it.candidates?.isEmpty() shouldBe false
        channel.close()
      }
    }

    mockEngine.requestHistory.first().url.host shouldBe "my.custom.endpoint"
  }

  @Test
  fun `client id header is set correctly in the request`() = doBlocking {
    val response = JSON.encodeToString(CountTokensResponse.Internal(totalTokens = 10))
    val mockEngine = MockEngine {
      respond(response, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
    }

    val controller =
      APIController(
        "super_cool_test_key",
        "gemini-pro-1.5",
        RequestOptions(),
        mockEngine,
        TEST_CLIENT_ID,
        mockFirebaseApp,
        TEST_VERSION,
        TEST_APP_ID,
        null,
      )

    withTimeout(5.seconds) { controller.countTokens(textCountTokenRequest("cats")) }

    mockEngine.requestHistory.first().headers["x-goog-api-client"] shouldBe TEST_CLIENT_ID
  }

  @Test
  fun `ml monitoring header is set correctly if data collection is enabled`() = doBlocking {
    val response = JSON.encodeToString(CountTokensResponse.Internal(totalTokens = 10))
    val mockEngine = MockEngine {
      respond(response, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
    }

    Mockito.`when`(mockFirebaseApp.isDataCollectionDefaultEnabled).thenReturn(true)

    val controller =
      APIController(
        "super_cool_test_key",
        "gemini-pro-1.5",
        RequestOptions(),
        mockEngine,
        TEST_CLIENT_ID,
        mockFirebaseApp,
        TEST_VERSION,
        TEST_APP_ID,
        null,
      )

    withTimeout(5.seconds) { controller.countTokens(textCountTokenRequest("cats")) }

    mockEngine.requestHistory.first().headers["X-Firebase-AppId"] shouldBe TEST_APP_ID
    mockEngine.requestHistory.first().headers["X-Firebase-AppVersion"] shouldBe
      TEST_VERSION.toString()
  }

  @Test
  fun `ToolConfig serialization contains correct keys`() = doBlocking {
    val channel = ByteChannel(autoFlush = true)
    val mockEngine = MockEngine {
      respond(channel, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
    }
    prepareStreamingResponse(createResponses("Random")).forEach { channel.writeFully(it) }

    val controller =
      APIController(
        "super_cool_test_key",
        "gemini-pro-1.5",
        RequestOptions(),
        mockEngine,
        TEST_CLIENT_ID,
        mockFirebaseApp,
        TEST_VERSION,
        TEST_APP_ID,
        null,
      )

    withTimeout(5.seconds) {
      controller
        .generateContentStream(
          GenerateContentRequest(
            model = "unused",
            contents = listOf(Content.Internal(parts = listOf(TextPart.Internal("Arbitrary")))),
            toolConfig =
              ToolConfig.Internal(
                FunctionCallingConfig.Internal(
                  mode = FunctionCallingConfig.Internal.Mode.ANY,
                  allowedFunctionNames = listOf("allowedFunctionName")
                )
              )
          ),
        )
        .collect { channel.close() }
    }

    val requestBodyAsText = (mockEngine.requestHistory.first().body as TextContent).text

    requestBodyAsText shouldContainJsonKey "tool_config.function_calling_config.mode"
    requestBodyAsText shouldContainJsonKey
      "tool_config.function_calling_config.allowed_function_names"
  }

  @Test
  fun `google search tool serialization contains correct keys`() = doBlocking {
    val channel = ByteChannel(autoFlush = true)
    val mockEngine = MockEngine {
      respond(channel, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
    }
    prepareStreamingResponse(createResponses("Random")).forEach { channel.writeFully(it) }

    val controller =
      APIController(
        "super_cool_test_key",
        "gemini-pro-1.5",
        RequestOptions(),
        mockEngine,
        TEST_CLIENT_ID,
        mockFirebaseApp,
        TEST_VERSION,
        TEST_APP_ID,
        null,
      )

    withTimeout(5.seconds) {
      controller
        .generateContentStream(
          GenerateContentRequest(
            model = "unused",
            contents = listOf(Content.Internal(parts = listOf(TextPart.Internal("Arbitrary")))),
            tools = listOf(Tool.Internal(googleSearch = GoogleSearch())),
          )
        )
        .collect { channel.close() }
    }

    val requestBodyAsText = (mockEngine.requestHistory.first().body as TextContent).text

    requestBodyAsText shouldContainJsonKey "tools[0].googleSearch"
  }

  @Test
  fun `headers from HeaderProvider are added to the request`() = doBlocking {
    val response = JSON.encodeToString(CountTokensResponse.Internal(totalTokens = 10))
    val mockEngine = MockEngine {
      respond(response, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
    }

    val testHeaderProvider =
      object : HeaderProvider {
        override val timeout: Duration
          get() = 5.seconds

        override suspend fun generateHeaders(): Map<String, String> =
          mapOf("header1" to "value1", "header2" to "value2")
      }

    val controller =
      APIController(
        "super_cool_test_key",
        "gemini-pro-1.5",
        RequestOptions(),
        mockEngine,
        TEST_CLIENT_ID,
        mockFirebaseApp,
        TEST_VERSION,
        TEST_APP_ID,
        testHeaderProvider,
      )

    withTimeout(5.seconds) { controller.countTokens(textCountTokenRequest("cats")) }

    mockEngine.requestHistory.first().headers["header1"] shouldBe "value1"
    mockEngine.requestHistory.first().headers["header2"] shouldBe "value2"
  }

  @Test
  fun `headers from HeaderProvider are ignored if timeout`() = doBlocking {
    val response = JSON.encodeToString(CountTokensResponse.Internal(totalTokens = 10))
    val mockEngine = MockEngine {
      respond(response, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
    }

    val testHeaderProvider =
      object : HeaderProvider {
        override val timeout: Duration
          get() = 5.milliseconds

        override suspend fun generateHeaders(): Map<String, String> {
          delay(10.milliseconds)
          return mapOf("header1" to "value1")
        }
      }

    val controller =
      APIController(
        "super_cool_test_key",
        "gemini-pro-1.5",
        RequestOptions(),
        mockEngine,
        TEST_CLIENT_ID,
        mockFirebaseApp,
        TEST_VERSION,
        TEST_APP_ID,
        testHeaderProvider,
      )

    withTimeout(5.seconds) { controller.countTokens(textCountTokenRequest("cats")) }

    mockEngine.requestHistory.first().headers.contains("header1") shouldBe false
  }

  @Test
  fun `code execution tool serialization contains correct keys`() = doBlocking {
    val channel = ByteChannel(autoFlush = true)
    val mockEngine = MockEngine {
      respond(channel, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
    }
    prepareStreamingResponse(createResponses("Random")).forEach { channel.writeFully(it) }

    val controller =
      APIController(
        "super_cool_test_key",
        "gemini-pro-1.5",
        RequestOptions(),
        mockEngine,
        TEST_CLIENT_ID,
        mockFirebaseApp,
        TEST_VERSION,
        TEST_APP_ID,
        null,
      )

    withTimeout(5.seconds) {
      controller
        .generateContentStream(
          GenerateContentRequest(
            model = "unused",
            contents = listOf(Content.Internal(parts = listOf(TextPart.Internal("Arbitrary")))),
            tools = listOf(Tool.Internal(codeExecution = JsonObject(emptyMap()))),
          )
        )
        .collect { channel.close() }
    }

    val requestBodyAsText = (mockEngine.requestHistory.first().body as TextContent).text

    requestBodyAsText shouldContainJsonKey "tools[0].codeExecution"
  }
}

@RunWith(Parameterized::class)
internal class ModelNamingTests(private val modelName: String, private val actualName: String) {
  private val mockFirebaseApp = Mockito.mock<FirebaseApp>()

  @Before
  fun setup() {
    Mockito.`when`(mockFirebaseApp.isDataCollectionDefaultEnabled).thenReturn(false)
  }

  @Test
  fun `request should include right model name`() = doBlocking {
    val channel = ByteChannel(autoFlush = true)
    val mockEngine = MockEngine {
      respond(channel, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
    }
    prepareStreamingResponse(createResponses("Random")).forEach { channel.writeFully(it) }
    val controller =
      APIController(
        "super_cool_test_key",
        modelName,
        RequestOptions(),
        mockEngine,
        TEST_CLIENT_ID,
        mockFirebaseApp,
        TEST_VERSION,
        TEST_APP_ID,
        null,
      )

    withTimeout(5.seconds) {
      controller.generateContentStream(textGenerateContentRequest("cats")).collect {
        it.candidates?.isEmpty() shouldBe false
        channel.close()
      }
    }

    mockEngine.requestHistory.first().url.encodedPath shouldContain actualName
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters
    fun data() =
      listOf(
        arrayOf("gemini-pro", "models/gemini-pro"),
        arrayOf("x/gemini-pro", "x/gemini-pro"),
        arrayOf("models/gemini-pro", "models/gemini-pro"),
        arrayOf("/modelname", "/modelname"),
        arrayOf("modifiedNaming/mymodel", "modifiedNaming/mymodel"),
      )
  }
}

internal fun textGenerateContentRequest(prompt: String) =
  GenerateContentRequest(
    model = "unused",
    contents = listOf(Content.Internal(parts = listOf(TextPart.Internal(prompt)))),
  )

internal fun textCountTokenRequest(prompt: String) =
  CountTokensRequest(generateContentRequest = textGenerateContentRequest(prompt))
