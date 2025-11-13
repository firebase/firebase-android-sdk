/*
 * Copyright 2025 Google LLC
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
import com.google.firebase.ai.common.util.doBlocking
import com.google.firebase.ai.type.RequestOptions
import com.google.firebase.ai.type.TextPart
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import com.google.common.truth.Truth.assertThat
import com.google.firebase.ai.type.content

class ChatTest {
    private val TEST_CLIENT_ID = "test"
    private val TEST_APP_ID = "1:android:12345"
    private val TEST_VERSION = 1

    private var mockFirebaseApp: FirebaseApp = Mockito.mock<FirebaseApp>()

    @Before
    fun setup() {
        Mockito.`when`(mockFirebaseApp.isDataCollectionDefaultEnabled).thenReturn(false)
    }

    @Test
    fun `sendMessageStream preserves thoughtSignature in history`() = doBlocking {
        val mockResponse = """
            [
              {
                "candidates": [
                  {
                    "content": {
                      "role": "model",
                      "parts": [
                        {
                          "text": "This is a thought.",
                          "thought": true,
                          "thoughtSignature": "thought1"
                        }
                      ]
                    }
                  }
                ]
              },
              {
                "candidates": [
                  {
                    "content": {
                      "role": "model",
                      "parts": [
                        {
                          "text": "This is not a thought."
                        }
                      ]
                    }
                  }
                ]
              }
            ]
        """.trimIndent()
        val mockEngine = MockEngine {
            respond(
                mockResponse,
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val apiController =
            APIController(
                "super_cool_test_key",
                "gemini-2.5-flash",
                RequestOptions(timeout = 5.seconds),
                mockEngine,
                TEST_CLIENT_ID,
                mockFirebaseApp,
                TEST_VERSION,
                TEST_APP_ID,
                null,
            )

        val generativeModel =
            GenerativeModel(
                "gemini-2.5-flash",
                controller = apiController
            )
        val chat = Chat(generativeModel)

        withTimeout(5.seconds) {
            chat.sendMessageStream("my test prompt").collect()
        }

        val history = chat.history
        assertThat(history).hasSize(2)
        val modelResponse = history[1]
        assertThat(modelResponse.role).isEqualTo("model")
        assertThat(modelResponse.parts).hasSize(2)
        val thoughtPart = modelResponse.parts[0] as TextPart
        assertThat(thoughtPart.isThought).isTrue()
        assertThat(thoughtPart.thoughtSignature).isEqualTo("thought1")
        val textPart = modelResponse.parts[1] as TextPart
        assertThat(textPart.isThought).isFalse()
        assertThat(textPart.thoughtSignature).isNull()
    }
}
