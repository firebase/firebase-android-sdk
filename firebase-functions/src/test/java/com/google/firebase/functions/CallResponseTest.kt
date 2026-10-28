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

package com.google.firebase.functions

import com.google.android.gms.tasks.Task
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.google.firebase.Firebase
import com.google.firebase.functions.FirebaseFunctionsException.Code
import com.google.firebase.functions.Serializer.Companion.LONG_TYPE
import com.google.firebase.functions.Serializer.Companion.UNSIGNED_LONG_TYPE
import java.io.Closeable
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Tests how callable responses are handled, using a local HTTP server bound to 127.0.0.1. */
@RunWith(RobolectricTestRunner::class)
class CallResponseTest : BaseTestCase() {
  private val uncaughtExceptions = CopyOnWriteArrayList<Throwable>()
  private var previousUncaughtExceptionHandler: Thread.UncaughtExceptionHandler? = null
  private var server: LocalHttpServer? = null

  @Before
  fun recordUncaughtExceptions() {
    // OkHttp rethrows unchecked exceptions from Callback.onResponse on its dispatcher thread,
    // which crashes an app. Record them so that the tests can check that none are thrown.
    previousUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { _, e -> uncaughtExceptions.add(e) }
  }

  @After
  fun tearDown() {
    server?.close()
    Thread.setDefaultUncaughtExceptionHandler(previousUncaughtExceptionHandler)
  }

  @Test
  fun `call decodes a valid Int64Value as a Long`() {
    val task =
      callWithResponse("""{"data": {"@type": "$LONG_TYPE", "value": "9223372036854775807"}}""")

    assertThat(task.exception).isNull()
    assertThat(task.result?.data).isEqualTo(Long.MAX_VALUE)
  }

  @Test
  fun `call fails with INTERNAL when a UInt64Value does not fit in a Long`() {
    val task =
      callWithResponse(
        """{"data": {"@type": "$UNSIGNED_LONG_TYPE", "value": "18446744073709551615"}}"""
      )

    assertFailedWithInvalidValue(task)
  }

  @Test
  fun `call fails with INTERNAL when an Int64Value is not a number`() {
    val task = callWithResponse("""{"data": {"count": {"@type": "$LONG_TYPE", "value": "abc"}}}""")

    assertFailedWithInvalidValue(task)
  }

  @Test
  fun `call fails with INTERNAL when the connection closes while reading the body`() {
    val task = callFunction { output ->
      // Announce more bytes than are sent. The server closes the connection afterwards.
      output.write(responseHead(contentLength = 100))
      output.write("""{"data": """.toByteArray())
    }

    val exception = failureOf(task)
    assertThat(exception.code).isEqualTo(Code.INTERNAL)
    assertThat(exception.cause).isInstanceOf(IOException::class.java)
    assertThat(uncaughtExceptions).isEmpty()
  }

  private fun callWithResponse(body: String): Task<HttpsCallableResult?> = callFunction { output ->
    val bytes = body.toByteArray()
    output.write(responseHead(bytes.size))
    output.write(bytes)
  }

  /** Calls a function served by a [LocalHttpServer] and waits for the returned task. */
  private fun callFunction(respond: (OutputStream) -> Unit): Task<HttpsCallableResult?> {
    val localServer = LocalHttpServer(respond)
    server = localServer
    // The public call() methods first wait for a process-wide ProviderInstaller task, which does
    // not complete reliably under Robolectric, so send the request with an explicit context.
    val task =
      Firebase.functions.call(
        localServer.url,
        null,
        HttpsCallableContext(null, null, null),
        HttpsCallOptions()
      )
    val completed = CountDownLatch(1)
    // Use a direct executor because the main looper does not run while this thread waits.
    task.addOnCompleteListener(Executor { it.run() }) { completed.countDown() }
    assertWithMessage("The call did not complete. Uncaught exceptions: %s", uncaughtExceptions)
      .that(completed.await(30, TimeUnit.SECONDS))
      .isTrue()
    return task
  }

  private fun responseHead(contentLength: Int): ByteArray =
    ("HTTP/1.1 200 OK\r\n" +
        "Content-Type: application/json\r\n" +
        "Content-Length: $contentLength\r\n" +
        "Connection: close\r\n" +
        "\r\n")
      .toByteArray()

  private fun failureOf(task: Task<HttpsCallableResult?>): FirebaseFunctionsException {
    val exception = task.exception
    assertThat(exception).isInstanceOf(FirebaseFunctionsException::class.java)
    return exception as FirebaseFunctionsException
  }

  private fun assertFailedWithInvalidValue(task: Task<HttpsCallableResult?>) {
    val exception = failureOf(task)
    assertThat(exception.code).isEqualTo(Code.INTERNAL)
    assertThat(exception.message).isEqualTo("Response contains an invalid value.")
    assertThat(exception.details).isNull()
    assertThat(exception.cause).isInstanceOf(IllegalArgumentException::class.java)
    assertThat(uncaughtExceptions).isEmpty()
  }
}

/**
 * A minimal HTTP/1.1 server on 127.0.0.1. For each connection it reads the request, lets [respond]
 * write the raw response, and closes the connection. The JDK's `com.sun.net.httpserver` package is
 * not available to Android unit tests.
 */
private class LocalHttpServer(private val respond: (OutputStream) -> Unit) : Closeable {
  private val serverSocket = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))
  val url = URL("http://127.0.0.1:${serverSocket.localPort}/callable")

  init {
    thread(isDaemon = true, name = "LocalHttpServer") {
      while (true) {
        val socket =
          try {
            serverSocket.accept()
          } catch (e: IOException) {
            break // The server socket was closed.
          }
        try {
          socket.use {
            readRequest(it.getInputStream())
            respond(it.getOutputStream())
          }
        } catch (e: IOException) {
          // The client went away. The test checks the result of the call instead.
        }
      }
    }
  }

  override fun close() = serverSocket.close()

  /** Reads the whole request, so that closing the socket does not reset the connection. */
  private fun readRequest(input: InputStream) {
    val head = StringBuilder()
    while (!head.endsWith("\r\n\r\n")) {
      val b = input.read()
      if (b == -1) return
      head.append(b.toChar())
    }
    val contentLength =
      Regex("""content-length:\s*(\d+)""", RegexOption.IGNORE_CASE)
        .find(head)
        ?.groupValues
        ?.get(1)
        ?.toInt()
        ?: 0
    DataInputStream(input).readFully(ByteArray(contentLength))
  }
}
