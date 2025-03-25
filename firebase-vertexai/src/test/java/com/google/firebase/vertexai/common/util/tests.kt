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

@file:Suppress("DEPRECATION") // a replacement for our purposes has not been published yet

package com.google.firebase.vertexai.common.util

import com.google.firebase.FirebaseApp
import com.google.firebase.vertexai.common.APIController
import com.google.firebase.vertexai.common.JSON
import com.google.firebase.vertexai.type.Candidate
import com.google.firebase.vertexai.type.Content
import com.google.firebase.vertexai.type.GenerateContentResponse
import com.google.firebase.vertexai.type.RequestOptions
import com.google.firebase.vertexai.type.TextPart
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import org.mockito.Mockito

private val TEST_CLIENT_ID = "genai-android/test"
private val TEST_APP_ID = "1:android:12345"
private val TEST_VERSION = 1

internal fun prepareStreamingResponse(
  response: List<GenerateContentResponse.Internal>
): List<ByteArray> = response.map { "data: ${JSON.encodeToString(it)}$SSE_SEPARATOR".toByteArray() }

@OptIn(ExperimentalSerializationApi::class)
internal fun createResponses(vararg text: String): List<GenerateContentResponse.Internal> {
  val candidates =
    text.map { Candidate.Internal(Content.Internal(parts = listOf(TextPart.Internal(it)))) }

  return candidates.map { GenerateContentResponse.Internal(candidates = listOf(it)) }
}

/**
 * Wrapper around common instances needed in tests.
 *
 * @param channel A [ByteChannel] for sending responses through the mock HTTP engine
 * @param apiController A [APIController] that consumes the [channel]
 * @see commonTest
 * @see send
 */
internal data class CommonTestScope(val channel: ByteChannel, val apiController: APIController)

/** A test that runs under a [CommonTestScope]. */
internal typealias CommonTest = suspend CommonTestScope.() -> Unit

/**
 * Common test block for providing a [CommonTestScope] during tests.
 *
 * Example usage:
 * ```
 * @Test
 * fun `(generateContent) generates a proper response`() = commonTest {
 *   val request = createRequest("say something nice")
 *   val response = createResponse("The world is a beautiful place!")
 *
 *   channel.send(prepareResponse(response))
 *
 *   withTimeout(testTimeout) {
 *     val data = controller.generateContent(request)
 *     data.candidates.shouldNotBeEmpty()
 *   }
 * }
 * ```
 *
 * @param status An optional [HttpStatusCode] to return as a response
 * @param requestOptions Optional [RequestOptions] to utilize in the underlying controller
 * @param block The test contents themselves, with the [CommonTestScope] implicitly provided
 * @see CommonTestScope
 */
internal fun commonTest(
  status: HttpStatusCode = HttpStatusCode.OK,
  requestOptions: RequestOptions = RequestOptions(),
  block: CommonTest,
) = doBlocking {
  val mockFirebaseApp = Mockito.mock<FirebaseApp>()
  Mockito.`when`(mockFirebaseApp.isDataCollectionDefaultEnabled).thenReturn(false)

  val channel = ByteChannel(autoFlush = true)
  val apiController =
    APIController(
      "super_cool_test_key",
      "gemini-pro",
      requestOptions,
      MockEngine {
        respond(channel, status, headersOf(HttpHeaders.ContentType, "application/json"))
      },
      TEST_CLIENT_ID,
      mockFirebaseApp,
      TEST_VERSION,
      TEST_APP_ID,
      null,
    )
  CommonTestScope(channel, apiController).block()
}
