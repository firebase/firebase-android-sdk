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

  private val subscribers = ConcurrentLinkedQueue<Subscriber<in StreamResponse>>()
  private var activeCall: Call? = null

  override fun subscribe(subscriber: Subscriber<in StreamResponse>) {
    subscribers.add(subscriber)
    subscriber.onSubscribe(
      object : Subscription {
        override fun request(n: Long) {
          startStreaming()
        }

        override fun cancel() {
          cancelStream()
          subscribers.remove(subscriber)
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
      val callClient = options.apply(client)
      val requestBody =
        RequestBody.create(
          MediaType.parse("application/json"),
          JSONObject(mapOf("data" to serializer.encode(data))).toString()
        )
      val requestBuilder =
        Request.Builder().url(url).post(requestBody).header("Accept", "text/event-stream")
      applyCommonConfiguration(requestBuilder, context)
      val request = requestBuilder.build()
      val call = callClient.newCall(request)
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

  private fun applyCommonConfiguration(
    requestBuilder: Request.Builder,
    context: HttpsCallableContext?
  ) {
    context?.authToken?.let { requestBuilder.header("Authorization", "Bearer $it") }
    context?.instanceIdToken?.let { requestBuilder.header("Firebase-Instance-ID-Token", it) }
    context?.appCheckToken?.let { requestBuilder.header("X-Firebase-AppCheck", it) }
  }

  private fun processSSEStream(inputStream: InputStream) {
    BufferedReader(InputStreamReader(inputStream)).use { reader ->
      try {
        reader.lineSequence().forEach { line ->
          val dataChunk =
            when {
              line.startsWith("data:") -> line.removePrefix("data:")
              line.startsWith("result:") -> line.removePrefix("result:")
              else -> return@forEach
            }
          try {
            val json = JSONObject(dataChunk)
            when {
              json.has("message") ->
                serializer.decode(json.opt("message"))?.let {
                  notifyData(StreamResponse.Message(data = HttpsCallableResult(it)))
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
                  notifyData(StreamResponse.Result(data = HttpsCallableResult(it)))
                  notifyComplete()
                }
                return
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
        notifyError(
          FirebaseFunctionsException(
            "Stream ended unexpectedly without completion",
            FirebaseFunctionsException.Code.INTERNAL,
            null
          )
        )
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

  private fun notifyData(data: StreamResponse?) {
    for (subscriber in subscribers) {
      subscriber.onNext(data)
    }
  }

  private fun notifyError(e: FirebaseFunctionsException) {
    for (subscriber in subscribers) {
      subscriber.onError(e)
    }
    subscribers.clear()
  }

  private fun notifyComplete() {
    for (subscriber in subscribers) {
      subscriber.onComplete()
    }
    subscribers.clear()
  }

  private fun validateResponse(response: Response) {
    if (response.isSuccessful) return

    val htmlContentType = "text/html; charset=utf-8"
    val trimMargin: String
    if (response.code() == 404 && response.header("Content-Type") == htmlContentType) {
      trimMargin = """URL not found. Raw response: ${response.body()?.string()}""".trimMargin()
      throw FirebaseFunctionsException(
        trimMargin,
        FirebaseFunctionsException.Code.fromHttpStatus(response.code()),
        null
      )
    }

    val text = response.body()?.string() ?: ""
    val error: Any?
    try {
      val json = JSONObject(text)
      error = serializer.decode(json.opt("error"))
    } catch (e: Throwable) {
      throw FirebaseFunctionsException(
        "${e.message} Unexpected Response:\n$text ",
        FirebaseFunctionsException.Code.INTERNAL,
        e
      )
    }
    throw FirebaseFunctionsException(
      error.toString(),
      FirebaseFunctionsException.Code.INTERNAL,
      error
    )
  }
}
