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

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.common.APIController
import com.google.firebase.ai.common.JSON
import com.google.firebase.ai.common.util.doBlocking
import com.google.firebase.ai.generativemodel.CloudGenerativeModelProvider
import com.google.firebase.ai.type.Candidate
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.CountTokensResponse
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.HarmBlockMethod
import com.google.firebase.ai.type.HarmBlockThreshold
import com.google.firebase.ai.type.HarmCategory
import com.google.firebase.ai.type.InvalidStateException
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.RequestOptions
import com.google.firebase.ai.type.SafetySetting
import com.google.firebase.ai.type.ServerException
import com.google.firebase.ai.type.TextPart
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import io.kotest.assertions.json.shouldContainJsonKey
import io.kotest.assertions.json.shouldContainJsonKeyValue
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito

@RunWith(AndroidJUnit4::class)
internal class GenerativeModelTesting {
  private val TEST_CLIENT_ID = "test"
  private val TEST_APP_ID = "1:android:12345"
  private val TEST_VERSION = 1

  private var mockFirebaseApp: FirebaseApp = Mockito.mock<FirebaseApp>()

  @Before
  fun setup() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    Mockito.`when`(mockFirebaseApp.isDataCollectionDefaultEnabled).thenReturn(false)
    Mockito.`when`(mockFirebaseApp.applicationContext).thenReturn(context)
  }

  @Test
  fun `system calling in request`() = doBlocking {
    val mockEngine = MockEngine {
      respond(
        generateContentResponseAsJsonString("text response"),
        HttpStatusCode.OK,
        headersOf(HttpHeaders.ContentType, "application/json")
      )
    }

    val apiController =
      APIController(
        "super_cool_test_key",
        "gemini-2.5-flash",
        RequestOptions(
          timeout = 5.seconds,
          endpoint = "https://my.custom.endpoint",
          autoFunctionCallingTurnLimit = 10
        ),
        mockEngine,
        TEST_CLIENT_ID,
        mockFirebaseApp,
        TEST_VERSION,
        TEST_APP_ID,
        null,
      )

    val generativeModel =
      GenerativeModel(
        actualModel =
          CloudGenerativeModelProvider(
            "gemini-2.5-flash",
            systemInstruction = content { text("system instruction") },
            controller = apiController
          ),
        requestOptions = RequestOptions()
      )

    withTimeout(5.seconds) { generativeModel.generateContent("my test prompt") }

    mockEngine.requestHistory.shouldNotBeEmpty()

    val request = mockEngine.requestHistory.first().body
    request.shouldBeInstanceOf<TextContent>()

    request.text.let {
      it shouldContainJsonKey "system_instruction"
      it.shouldContainJsonKeyValue("$.system_instruction.role", "system")
      it.shouldContainJsonKeyValue("$.system_instruction.parts[0].text", "system instruction")
    }
  }

  @Test
  fun `security headers are included in request`() = doBlocking {
    val mockEngine = MockEngine {
      respond(
        generateContentResponseAsJsonString("text response"),
        HttpStatusCode.OK,
        headersOf(HttpHeaders.ContentType, "application/json")
      )
    }
    val generativeModel = generativeModelWithMockEngine(mockEngine)

    withTimeout(5.seconds) { generativeModel.generateContent("my test prompt") }

    val headers = mockEngine.requestHistory.first().headers
    headers["X-Android-Package"] shouldBe "com.google.firebase.ai.test"
    // X-Android-Cert will be empty because Robolectric doesn't provide signatures by default
    headers["X-Android-Cert"] shouldBe ""
  }

  @Test
  fun `security headers are included in streaming request`() = doBlocking {
    val mockEngine = MockEngine {
      respond(
        generateContentResponseAsJsonString("text response"),
        HttpStatusCode.OK,
        headersOf(HttpHeaders.ContentType, "application/json")
      )
    }
    val generativeModel = generativeModelWithMockEngine(mockEngine)

    withTimeout(5.seconds) { generativeModel.generateContentStream("my test prompt").collect() }

    val headers = mockEngine.requestHistory.first().headers
    headers["X-Android-Package"] shouldBe "com.google.firebase.ai.test"
    // X-Android-Cert will be empty because Robolectric doesn't provide signatures by default
    headers["X-Android-Cert"] shouldBe ""
  }

  @Test
  fun `security headers are included in countTokens request`() = doBlocking {
    val mockEngine = MockEngine {
      respond(
        JSON.encodeToString(CountTokensResponse.Internal(totalTokens = 10)),
        HttpStatusCode.OK,
        headersOf(HttpHeaders.ContentType, "application/json")
      )
    }
    val generativeModel = generativeModelWithMockEngine(mockEngine)

    withTimeout(5.seconds) { generativeModel.countTokens("my test prompt") }

    val headers = mockEngine.requestHistory.first().headers
    headers["X-Android-Package"] shouldBe "com.google.firebase.ai.test"
    // X-Android-Cert will be empty because Robolectric doesn't provide signatures by default
    headers["X-Android-Cert"] shouldBe ""
  }

  @Test
  fun `X-Android-Cert is empty when signatures are missing`() = doBlocking {
    val mockEngine = MockEngine {
      respond(
        generateContentResponseAsJsonString("text response"),
        HttpStatusCode.OK,
        headersOf(HttpHeaders.ContentType, "application/json")
      )
    }

    val mockPackageManager = Mockito.mock(PackageManager::class.java)
    val mockContext = Mockito.mock(Context::class.java)
    Mockito.`when`(mockContext.packageName).thenReturn("com.test.app")
    Mockito.`when`(mockContext.packageManager).thenReturn(mockPackageManager)

    val mockApp = Mockito.mock(FirebaseApp::class.java)
    Mockito.`when`(mockApp.applicationContext).thenReturn(mockContext)

    val apiController =
      APIController(
        "super_cool_test_key",
        "gemini-2.5-flash",
        RequestOptions(),
        mockEngine,
        TEST_CLIENT_ID,
        mockApp,
        TEST_VERSION,
        TEST_APP_ID,
        null,
      )

    val generativeModel =
      GenerativeModel(
        actualModel = CloudGenerativeModelProvider("gemini-2.5-flash", controller = apiController),
        requestOptions = RequestOptions()
      )

    withTimeout(5.seconds) { generativeModel.generateContent("my test prompt") }

    val headers = mockEngine.requestHistory.first().headers
    headers["X-Android-Package"] shouldBe "com.test.app"
    // X-Android-Cert will be empty because Robolectric doesn't provide signatures by default
    headers["X-Android-Cert"] shouldBe ""
  }

  @Test
  fun `exception thrown when using invalid location`() = doBlocking {
    val mockEngine = MockEngine {
      respond(
        """<!DOCTYPE html>
           <html lang=en>
            <title>Error 404 (Not Found)!!1</title>
        """
          .trimIndent(),
        HttpStatusCode.NotFound,
        headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8")
      )
    }

    val apiController =
      APIController(
        "super_cool_test_key",
        "gemini-2.5-flash",
        RequestOptions(),
        mockEngine,
        TEST_CLIENT_ID,
        mockFirebaseApp,
        TEST_VERSION,
        TEST_APP_ID,
        null,
      )

    // Creating the
    val generativeModel =
      GenerativeModel(
        actualModel =
          CloudGenerativeModelProvider(
            "projects/PROJECTID/locations/INVALID_LOCATION/publishers/google/models/gemini-2.5-flash",
            controller = apiController
          ),
        requestOptions = RequestOptions()
      )

    val exception =
      shouldThrow<ServerException> {
        withTimeout(5.seconds) { generativeModel.generateContent("my test prompt") }
      }

    // Let's not be too strict on the wording to avoid breaking the test unnecessarily.
    exception.message shouldContain "location"
  }

  @Test
  fun `exception thrown when using HarmBlockMethod with GoogleAI`() = doBlocking {
    val mockEngine = MockEngine {
      respond(
        generateContentResponseAsJsonString("text response"),
        HttpStatusCode.OK,
        headersOf(HttpHeaders.ContentType, "application/json")
      )
    }

    val apiController =
      APIController(
        "super_cool_test_key",
        "gemini-2.5-flash",
        RequestOptions(),
        mockEngine,
        TEST_CLIENT_ID,
        mockFirebaseApp,
        TEST_VERSION,
        TEST_APP_ID,
        null,
      )

    val safetySettings =
      listOf(
        SafetySetting(
          HarmCategory.HARASSMENT,
          HarmBlockThreshold.MEDIUM_AND_ABOVE,
          HarmBlockMethod.SEVERITY
        )
      )

    val generativeModel =
      GenerativeModel(
        actualModel =
          CloudGenerativeModelProvider(
            "gemini-2.5-flash",
            safetySettings = safetySettings,
            generativeBackend = GenerativeBackend.googleAI(),
            controller = apiController
          ),
        requestOptions = RequestOptions()
      )

    val exception =
      shouldThrow<InvalidStateException> { generativeModel.generateContent("my test prompt") }

    exception.message shouldContain "HarmBlockMethod is unsupported by the Google Developer API"
  }

  @Test
  fun `exception NOT thrown when using HarmBlockMethod with VertexAI`() = doBlocking {
    val mockEngine = MockEngine {
      respond(
        generateContentResponseAsJsonString("text response"),
        HttpStatusCode.OK,
        headersOf(HttpHeaders.ContentType, "application/json")
      )
    }

    val apiController =
      APIController(
        "super_cool_test_key",
        "gemini-2.5-flash",
        RequestOptions(),
        mockEngine,
        TEST_CLIENT_ID,
        mockFirebaseApp,
        TEST_VERSION,
        TEST_APP_ID,
        null,
      )

    val safetySettings =
      listOf(
        SafetySetting(
          HarmCategory.HARASSMENT,
          HarmBlockThreshold.MEDIUM_AND_ABOVE,
          HarmBlockMethod.SEVERITY
        )
      )

    val generativeModel =
      GenerativeModel(
        actualModel =
          CloudGenerativeModelProvider(
            "gemini-2.5-flash",
            safetySettings = safetySettings,
            generativeBackend = GenerativeBackend.vertexAI("us-central1"),
            controller = apiController
          ),
        requestOptions = RequestOptions()
      )

    withTimeout(5.seconds) { generativeModel.generateContent("my test prompt") }
  }

  @OptIn(PublicPreviewAPI::class)
  private fun generateContentResponseAsJsonString(text: String): String {
    return JSON.encodeToString(
      GenerateContentResponse.Internal(
        listOf(Candidate.Internal(Content.Internal(parts = listOf(TextPart.Internal(text)))))
      )
    )
  }

  @Test
  fun `thinkingLevel and thinkingBudget are mutually exclusive`() = doBlocking {
    val exception =
      shouldThrow<IllegalArgumentException> {
        thinkingConfig {
          thinkingLevel = ThinkingLevel.MEDIUM
          thinkingBudget = 1
        }
      }
    exception.message shouldContain "Cannot set both"
  }

  @Test
  fun `correctly setting thinkingLevel in request`() = doBlocking {
    val mockEngine = MockEngine {
      respond(
        generateContentResponseAsJsonString("text response"),
        HttpStatusCode.OK,
        headersOf(HttpHeaders.ContentType, "application/json")
      )
    }

    val apiController =
      APIController(
        "super_cool_test_key",
        "gemini-2.5-flash",
        RequestOptions(),
        mockEngine,
        TEST_CLIENT_ID,
        mockFirebaseApp,
        TEST_VERSION,
        TEST_APP_ID,
        null,
      )

    val generativeModel =
      GenerativeModel(
        actualModel =
          CloudGenerativeModelProvider(
            "gemini-2.5-flash",
            generationConfig =
              generationConfig {
                thinkingConfig = thinkingConfig { thinkingLevel = ThinkingLevel.MEDIUM }
              },
            controller = apiController
          ),
        requestOptions = RequestOptions()
      )

    withTimeout(5.seconds) { generativeModel.generateContent("my test prompt") }

    mockEngine.requestHistory.shouldNotBeEmpty()

    val request = mockEngine.requestHistory.first().body
    request.shouldBeInstanceOf<TextContent>()

    request.text.let {
      it shouldContainJsonKey "generation_config"
      it.shouldContainJsonKeyValue("$.generation_config.thinking_config.thinking_level", "MEDIUM")
    }
  }

  private fun generativeModelWithMockEngine(mockEngine: MockEngine): GenerativeModel {
    val apiController =
      APIController(
        "super_cool_test_key",
        "gemini-2.5-flash",
        RequestOptions(),
        mockEngine,
        TEST_CLIENT_ID,
        mockFirebaseApp,
        TEST_VERSION,
        TEST_APP_ID,
        null,
      )

    return GenerativeModel(
      actualModel = CloudGenerativeModelProvider("gemini-2.5-flash", controller = apiController),
      requestOptions = RequestOptions()
    )
  }
}
