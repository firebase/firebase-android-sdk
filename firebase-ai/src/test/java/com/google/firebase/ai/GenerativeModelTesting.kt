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

import com.google.firebase.FirebaseApp
import com.google.firebase.ai.common.APIController
import com.google.firebase.ai.common.JSON
import com.google.firebase.ai.common.util.doBlocking
import com.google.firebase.ai.type.Candidate
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.RequestOptions
import com.google.firebase.ai.type.ServerException
import com.google.firebase.ai.type.TextPart
import com.google.firebase.ai.type.content
import io.kotest.assertions.json.shouldContainJsonKey
import io.kotest.assertions.json.shouldContainJsonKeyValue
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

internal class GenerativeModelTesting {
  private val TEST_CLIENT_ID = "test"
  private val TEST_APP_ID = "1:android:12345"
  private val TEST_VERSION = 1

  private var mockFirebaseApp: FirebaseApp = Mockito.mock<FirebaseApp>()

  @Before
  fun setup() {
    Mockito.`when`(mockFirebaseApp.isDataCollectionDefaultEnabled).thenReturn(false)
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
        "gemini-1.5-flash",
        RequestOptions(timeout = 5.seconds, endpoint = "https://my.custom.endpoint"),
        mockEngine,
        TEST_CLIENT_ID,
        mockFirebaseApp,
        TEST_VERSION,
        TEST_APP_ID,
        null,
      )

    val generativeModel =
      GenerativeModel(
        "gemini-1.5-flash",
        systemInstruction = content { text("system instruction") },
        controller = apiController
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
        "gemini-1.5-flash",
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
        "projects/PROJECTID/locations/INVALID_LOCATION/publishers/google/models/gemini-1.5-flash",
        controller = apiController
      )

    val exception =
      shouldThrow<ServerException> {
        withTimeout(5.seconds) { generativeModel.generateContent("my test prompt") }
      }

    // Let's not be too strict on the wording to avoid breaking the test unnecessarily.
    exception.message shouldContain "location"
  }

  @OptIn(ExperimentalSerializationApi::class)
  private fun generateContentResponseAsJsonString(text: String): String {
    return JSON.encodeToString(
      GenerateContentResponse.Internal(
        listOf(Candidate.Internal(Content.Internal(parts = listOf(TextPart.Internal(text)))))
      )
    )
  }
}
