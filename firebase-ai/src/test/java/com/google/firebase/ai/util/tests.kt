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

@file:OptIn(PublicPreviewAPI::class)

package com.google.firebase.ai.util

import com.google.firebase.FirebaseApp
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ImagenModel
import com.google.firebase.ai.common.APIController
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.RequestOptions
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.writeFully
import java.io.File
import kotlinx.coroutines.launch
import org.mockito.Mockito

private val TEST_CLIENT_ID = "firebase-ai-android/test"
private val TEST_APP_ID = "1:android:12345"
private val TEST_VERSION = 1

/** String separator used in SSE communication to signal the end of a message. */
internal const val SSE_SEPARATOR = "\r\n\r\n"

/**
 * Writes the provided [bytes] to the channel and closes it.
 *
 * Just a wrapper around [writeFully] that closes the channel after writing is complete.
 *
 * @param bytes the data to send through the channel
 */
internal suspend fun ByteChannel.send(bytes: ByteArray) {
  writeFully(bytes)
  close()
}

/**
 * Wrapper around common instances needed in tests.
 *
 * @param channel A [ByteChannel] for sending responses through the mock HTTP engine
 * @param apiController A [APIController] that consumes the [channel]
 * @see commonTest
 * @see send
 */
internal data class CommonTestScope(
  val channel: ByteChannel,
  val model: GenerativeModel,
  val imagenModel: ImagenModel,
)

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
  backend: GenerativeBackend = GenerativeBackend.vertexAI(),
  block: CommonTest,
) = doBlocking {
  val channel = ByteChannel(autoFlush = true)
  val mockFirebaseApp = Mockito.mock<FirebaseApp>()
  Mockito.`when`(mockFirebaseApp.isDataCollectionDefaultEnabled).thenReturn(false)

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
  val model =
    GenerativeModel("cool-model-name", generativeBackend = backend, controller = apiController)
  val imagenModel = ImagenModel("cooler-model-name", controller = apiController)
  CommonTestScope(channel, model, imagenModel).block()
}

/**
 * A variant of [commonTest] for performing *streaming-based* snapshot tests.
 *
 * Loads the *Golden File* and automatically parses the messages from it; providing it to the
 * channel.
 *
 * @param name The name of the *Golden File* to load
 * @param httpStatusCode An optional [HttpStatusCode] to return as a response
 * @param block The test contents themselves, with a [CommonTestScope] implicitly provided
 * @see goldenVertexUnaryFile
 */
internal fun goldenStreamingFile(
  name: String,
  httpStatusCode: HttpStatusCode = HttpStatusCode.OK,
  backend: GenerativeBackend = GenerativeBackend.vertexAI(),
  block: CommonTest,
) = doBlocking {
  val goldenFile = loadGoldenFile(name)
  val messages = goldenFile.readLines().filter { it.isNotBlank() }

  commonTest(httpStatusCode, backend = backend) {
    launch {
      for (message in messages) {
        channel.writeFully("$message$SSE_SEPARATOR".toByteArray())
      }
      channel.close()
    }

    block()
  }
}

/**
 * A variant of [goldenStreamingFile] for testing vertexAI
 *
 * Loads the *Golden File* and automatically parses the messages from it; providing it to the
 * channel.
 *
 * @param name The name of the *Golden File* to load
 * @param httpStatusCode An optional [HttpStatusCode] to return as a response
 * @param block The test contents themselves, with a [CommonTestScope] implicitly provided
 * @see goldenStreamingFile
 */
internal fun goldenVertexStreamingFile(
  name: String,
  httpStatusCode: HttpStatusCode = HttpStatusCode.OK,
  block: CommonTest,
) = goldenStreamingFile("vertexai/$name", httpStatusCode, block = block)

/**
 * A variant of [goldenStreamingFile] for testing the developer api
 *
 * Loads the *Golden File* and automatically parses the messages from it; providing it to the
 * channel.
 *
 * @param name The name of the *Golden File* to load
 * @param httpStatusCode An optional [HttpStatusCode] to return as a response
 * @param block The test contents themselves, with a [CommonTestScope] implicitly provided
 * @see goldenStreamingFile
 */
internal fun goldenDevAPIStreamingFile(
  name: String,
  httpStatusCode: HttpStatusCode = HttpStatusCode.OK,
  block: CommonTest,
) = goldenStreamingFile("googleai/$name", httpStatusCode, GenerativeBackend.googleAI(), block)

/**
 * A variant of [commonTest] for performing snapshot tests.
 *
 * Loads the *Golden File* and automatically provides it to the channel.
 *
 * @param name The name of the *Golden File* to load
 * @param httpStatusCode An optional [HttpStatusCode] to return as a response
 * @param block The test contents themselves, with a [CommonTestScope] implicitly provided
 * @see goldenStreamingFile
 */
internal fun goldenUnaryFile(
  name: String,
  httpStatusCode: HttpStatusCode = HttpStatusCode.OK,
  backend: GenerativeBackend = GenerativeBackend.vertexAI(),
  block: CommonTest,
) = doBlocking {
  commonTest(httpStatusCode, backend = backend) {
    val goldenFile = loadGoldenFile(name)
    val message = goldenFile.readText()

    launch { channel.send(message.toByteArray()) }

    block()
  }
}

/**
 * A variant of [goldenUnaryFile] for vertexai tests Loads the *Golden File* and automatically
 * provides it to the channel.
 *
 * @param name The name of the *Golden File* to load
 * @param httpStatusCode An optional [HttpStatusCode] to return as a response
 * @param block The test contents themselves, with a [CommonTestScope] implicitly provided
 * @see goldenUnaryFile
 */
internal fun goldenVertexUnaryFile(
  name: String,
  httpStatusCode: HttpStatusCode = HttpStatusCode.OK,
  block: CommonTest,
) = goldenUnaryFile("vertexai/$name", httpStatusCode, block = block)

/**
 * A variant of [goldenUnaryFile] for developer api tests Loads the *Golden File* and automatically
 * provides it to the channel.
 *
 * @param name The name of the *Golden File* to load
 * @param httpStatusCode An optional [HttpStatusCode] to return as a response
 * @param block The test contents themselves, with a [CommonTestScope] implicitly provided
 * @see goldenUnaryFile
 */
internal fun goldenDevAPIUnaryFile(
  name: String,
  httpStatusCode: HttpStatusCode = HttpStatusCode.OK,
  block: CommonTest,
) = goldenUnaryFile("googleai/$name", httpStatusCode, GenerativeBackend.googleAI(), block)

/**
 * Loads a *Golden File* from the resource directory.
 *
 * Expects golden files to live under `golden-files` in the resource files.
 *
 * @see goldenUnaryFile
 */
internal fun loadGoldenFile(path: String): File =
  loadResourceFile("vertexai-sdk-test-data/mock-responses/$path")

/** Loads a file from the test resources directory. */
internal fun loadResourceFile(path: String) = File("src/test/resources/$path")

/**
 * Ensures that a collection is neither null or empty.
 *
 * Syntax sugar for [shouldNotBeNull] and [shouldNotBeEmpty].
 */
inline fun <reified T : Any> Collection<T>?.shouldNotBeNullOrEmpty(): Collection<T> {
  shouldNotBeNull()
  shouldNotBeEmpty()
  return this
}
