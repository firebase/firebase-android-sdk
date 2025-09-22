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

package com.google.firebase.functions

import com.google.android.gms.tasks.Task
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.InterruptedIOException
import java.net.URL
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicLong
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.json.JSONObject
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription

internal class PublisherStream(
  private val url: URL,
  private val data: Any?,
  private val options: HttpsCallOptions,
  private val client: OkHttpClient,
  private val serializer: Serializer,
  private val contextTask: Task<HttpsCallableContext?>,
  private val executor: Executor
) : Publisher<StreamResponse> {

  private val subscribers = ConcurrentLinkedQueue<Pair<Subscriber<in StreamResponse>, AtomicLong>>()
  private var activeCall: Call? = null
  @Volatile private var isStreamingStarted = false
  @Volatile private var isCompleted = false
  private val messageQueue = ConcurrentLinkedQueue<StreamResponse>()

  override fun subscribe(subscriber: Subscriber<in StreamResponse>) {
    synchronized(this) {
      if (isCompleted) {
        subscriber.onError(
          FirebaseFunctionsException(
            "Cannot subscribe: Streaming has already completed.",
            FirebaseFunctionsException.Code.CANCELLED,
            null
          )
        )
        return
      }
      subscribers.add(subscriber to AtomicLong(0))
    }

    subscriber.onSubscribe(
      object : Subscription {
        override fun request(n: Long) {
          if (n <= 0) {
            subscriber.onError(IllegalArgumentException("Requested messages must be positive."))
            return
          }

          synchronized(this@PublisherStream) {
            if (isCompleted) return

            val subscriberEntry = subscribers.find { it.first == subscriber }
            subscriberEntry?.second?.addAndGet(n)
            dispatchMessages()
            if (!isStreamingStarted) {
              isStreamingStarted = true
              startStreaming()
            }
          }
        }

        override fun cancel() {
          synchronized(this@PublisherStream) {
            notifyError(
              FirebaseFunctionsException(
                "Stream was canceled",
                FirebaseFunctionsException.Code.CANCELLED,
                null
              )
            )
            val iterator = subscribers.iterator()
            while (iterator.hasNext()) {
              val pair = iterator.next()
              if (pair.first == subscriber) {
                iterator.remove()
              }
            }
            if (subscribers.isEmpty()) {
              cancelStream()
            }
          }
        }
      }
    )
  }

  private fun startStreaming() {
    contextTask.addOnCompleteListener(executor) { contextTask ->
      if (!contextTask.isSuccessful) {
        notifyError(
          FirebaseFunctionsException(
            "Error retrieving context",
            FirebaseFunctionsException.Code.INTERNAL,
            null,
            contextTask.exception
          )
        )
        return@addOnCompleteListener
      }

      val context = contextTask.result
      val configuredClient = options.apply(client)
      val requestBody =
        RequestBody.create(
          MediaType.parse("application/json"),
          JSONObject(mapOf("data" to serializer.encode(data))).toString()
        )
      val request =
        Request.Builder()
          .url(url)
          .post(requestBody)
          .apply {
            header("Accept", "text/event-stream")
            header("Content-Type", "application/json")
            context?.apply {
              authToken?.let { header("Authorization", "Bearer $it") }
              instanceIdToken?.let { header("Firebase-Instance-ID-Token", it) }
              appCheckToken?.let { header("X-Firebase-AppCheck", it) }
            }
          }
          .build()
      val call = configuredClient.newCall(request)
      activeCall = call

      call.enqueue(
        object : Callback {
          override fun onFailure(call: Call, e: IOException) {
            val code: FirebaseFunctionsException.Code =
              if (e is InterruptedIOException) {
                FirebaseFunctionsException.Code.DEADLINE_EXCEEDED
              } else {
                FirebaseFunctionsException.Code.INTERNAL
              }
            notifyError(FirebaseFunctionsException(code.name, code, null, e))
          }

          override fun onResponse(call: Call, response: Response) {
            validateResponse(response)
            val bodyStream = response.body()?.byteStream()
            if (bodyStream != null) {
              processSSEStream(bodyStream)
            } else {
              notifyError(
                FirebaseFunctionsException(
                  "Response body is null",
                  FirebaseFunctionsException.Code.INTERNAL,
                  null
                )
              )
            }
          }
        }
      )
    }
  }

  private fun cancelStream() {
    activeCall?.cancel()
    notifyError(
      FirebaseFunctionsException(
        "Stream was canceled",
        FirebaseFunctionsException.Code.CANCELLED,
        null
      )
    )
  }

  private fun processSSEStream(inputStream: InputStream) {
    BufferedReader(InputStreamReader(inputStream)).use { reader ->
      try {
        val eventBuffer = StringBuilder()
        reader.lineSequence().forEach { line ->
          if (line.isBlank()) {
            processEvent(eventBuffer.toString())
            eventBuffer.clear()
          } else {
            val dataChunk =
              when {
                line.startsWith("data:") -> line.removePrefix("data:")
                line.startsWith("result:") -> line.removePrefix("result:")
                else -> return@forEach
              }
            eventBuffer.append(dataChunk.trim()).append("\n")
          }
        }
        if (eventBuffer.isNotEmpty()) {
          processEvent(eventBuffer.toString())
        }
      } catch (e: Exception) {
        notifyError(
          FirebaseFunctionsException(
            e.message ?: "Error reading stream",
            FirebaseFunctionsException.Code.INTERNAL,
            e
          )
        )
      }
    }
  }

  private fun processEvent(dataChunk: String) {
    try {
      val json = JSONObject(dataChunk)
      when {
        json.has("message") -> {
          serializer.decode(json.opt("message"))?.let {
            messageQueue.add(StreamResponse.Message(message = HttpsCallableResult(it)))
          }
          dispatchMessages()
        }
        json.has("error") -> {
          serializer.decode(json.opt("error"))?.let {
            notifyError(
              FirebaseFunctionsException(
                it.toString(),
                FirebaseFunctionsException.Code.INTERNAL,
                it
              )
            )
          }
        }
        json.has("result") -> {
          serializer.decode(json.opt("result"))?.let {
            messageQueue.add(StreamResponse.Result(result = HttpsCallableResult(it)))
            dispatchMessages()
            notifyComplete()
          }
        }
      }
    } catch (e: Throwable) {
      notifyError(
        FirebaseFunctionsException(
          "Invalid JSON: $dataChunk",
          FirebaseFunctionsException.Code.INTERNAL,
          e
        )
      )
    }
  }

  private fun dispatchMessages() {
    synchronized(this) {
      val iterator = subscribers.iterator()
      while (iterator.hasNext()) {
        val (subscriber, requestedCount) = iterator.next()
        while (requestedCount.get() > 0 && messageQueue.isNotEmpty()) {
          subscriber.onNext(messageQueue.poll())
          requestedCount.decrementAndGet()
        }
      }
    }
  }

  private fun notifyError(e: Throwable) {
    if (!isCompleted) {
      isCompleted = true
      subscribers.forEach { (subscriber, _) ->
        try {
          subscriber.onError(e)
        } catch (ignored: Exception) {}
      }
      subscribers.clear()
      messageQueue.clear()
    }
  }

  private fun notifyComplete() {
    if (!isCompleted) {
      isCompleted = true
      subscribers.forEach { (subscriber, _) -> subscriber.onComplete() }
      subscribers.clear()
      messageQueue.clear()
    }
  }

  private fun validateResponse(response: Response) {
    if (response.isSuccessful) return

    val errorMessage: String
    if (
      response.code() == 404 &&
        MediaType.parse(response.header("Content-Type") ?: "")?.subtype() == "html"
    ) {
      errorMessage = """URL not found. Raw response: ${response.body()?.string()}""".trimMargin()
      notifyError(
        FirebaseFunctionsException(
          errorMessage,
          FirebaseFunctionsException.Code.fromHttpStatus(response.code()),
          null
        )
      )
      return
    }

    val text = response.body()?.string() ?: ""
    val error: Any?
    try {
      val json = JSONObject(text)
      error = serializer.decode(json.opt("error"))
    } catch (e: Throwable) {
      notifyError(
        FirebaseFunctionsException(
          "${e.message} Unexpected Response:\n$text ",
          FirebaseFunctionsException.Code.INTERNAL,
          e
        )
      )
      return
    }
    notifyError(
      FirebaseFunctionsException(error.toString(), FirebaseFunctionsException.Code.INTERNAL, error)
    )
  }
}
