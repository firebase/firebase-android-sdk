package com.google.firebase.functions.ktx

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.firebase.Firebase
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.StreamResponse
import com.google.firebase.functions.StreamResponse.Message
import com.google.firebase.functions.StreamResponse.Result
import com.google.firebase.functions.functions
import com.google.firebase.initialize
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription

@RunWith(AndroidJUnit4::class)
class StreamTests {

  private lateinit var functions: FirebaseFunctions
  var onNextList = mutableListOf<StreamResponse>()
  private lateinit var subscriber: Subscriber<StreamResponse>
  private var throwable: Throwable? = null
  private var isComplete = false

  @Before
  fun setup() {
    Firebase.initialize(ApplicationProvider.getApplicationContext())
    functions = Firebase.functions
    subscriber =
      object : Subscriber<StreamResponse> {
        override fun onSubscribe(subscription: Subscription?) {
          subscription?.request(1)
        }

        override fun onNext(streamResponse: StreamResponse) {
          onNextList.add(streamResponse)
        }

        override fun onError(t: Throwable?) {
          throwable = t
        }

        override fun onComplete() {
          isComplete = true
        }
      }
  }

  @After
  fun clear() {
    onNextList.clear()
    throwable = null
    isComplete = false
  }

  @Test
  fun genStream_withPublisher_receivesMessagesAndFinalResult() {
    val input = mapOf("data" to "Why is the sky blue")
    val function = functions.getHttpsCallable("genStream")

    function.stream(input).subscribe(subscriber)

    Thread.sleep(8000)
    val messages = onNextList.filterIsInstance<Message>()
    val results = onNextList.filterIsInstance<Result>()
    assertThat(messages.map { it.data.data.toString() })
      .containsExactly(
        "{chunk=hello}",
        "{chunk=world}",
        "{chunk=this}",
        "{chunk=is}",
        "{chunk=cool}"
      )
    assertThat(results).hasSize(1)
    assertThat(results.first().data.data.toString()).isEqualTo("hello world this is cool")
    assertThat(throwable).isNull()
    assertThat(isComplete).isTrue()
  }

  @Test
  fun genStream_withFlow_receivesMessagesAndFinalResult() = runBlocking {
    val input = mapOf("data" to "Why is the sky blue")
    val function = functions.getHttpsCallable("genStream")

    val flow = function.stream(input).asFlow()
    val receivedResponses = mutableListOf<StreamResponse>()
    try {
      withTimeout(8000) { flow.collect { response -> receivedResponses.add(response) } }
      isComplete = true
    } catch (e: Throwable) {
      throwable = e
    }

    val messages = receivedResponses.filterIsInstance<Message>()
    val results = receivedResponses.filterIsInstance<Result>()
    assertThat(messages.map { it.data.data.toString() })
      .containsExactly(
        "{chunk=hello}",
        "{chunk=world}",
        "{chunk=this}",
        "{chunk=is}",
        "{chunk=cool}"
      )
    assertThat(results).hasSize(1)
    assertThat(results.first().data.data.toString()).isEqualTo("hello world this is cool")
    assertThat(throwable).isNull()
    assertThat(isComplete).isTrue()
  }

  @Test
  fun genStreamError_receivesErrorAndStops() {
    val input = mapOf("data" to "Why is the sky blue")
    val function =
      functions.getHttpsCallable("genStreamError").withTimeout(800, TimeUnit.MILLISECONDS)

    function.stream(input).subscribe(subscriber)
    Thread.sleep(2000)

    val messages = onNextList.filterIsInstance<Message>()
    val onNextStringList = messages.map { it.data.data.toString() }
    assertThat(onNextStringList)
      .containsExactly(
        "{chunk=hello}",
      )
    assertThat(throwable).isNotNull()
    assertThat(isComplete).isFalse()
  }

  @Test
  fun genStreamNoReturn_receivesOnlyMessages() {
    val input = mapOf("data" to "Why is the sky blue")
    val function = functions.getHttpsCallable("genStreamNoReturn")

    function.stream(input).subscribe(subscriber)
    Thread.sleep(8000)

    val messages = onNextList.filterIsInstance<Message>()
    val results = onNextList.filterIsInstance<Result>()

    val onNextStringList = messages.map { it.data.data.toString() }
    assertThat(onNextStringList)
      .containsExactly(
        "{chunk=hello}",
        "{chunk=world}",
        "{chunk=this}",
        "{chunk=is}",
        "{chunk=cool}"
      )
    assertThat(results).isEmpty()
    assertThat(throwable).isNull()
    assertThat(isComplete).isFalse()
  }

  @Test
  fun genStream_cancelStream_receivesPartialMessagesAndError() {
    val input = mapOf("data" to "Why is the sky blue")
    val function = functions.getHttpsCallable("genStreamNoReturn")
    val publisher = function.stream(input)
    var subscription: Subscription? = null
    val cancelableSubscriber =
      object : Subscriber<StreamResponse> {
        override fun onSubscribe(s: Subscription?) {
          subscription = s
          s?.request(1)
        }

        override fun onNext(streamResponse: StreamResponse) {
          onNextList.add(streamResponse)
        }

        override fun onError(t: Throwable?) {
          throwable = t
        }

        override fun onComplete() {
          isComplete = true
        }
      }

    publisher.subscribe(cancelableSubscriber)
    Thread.sleep(500)
    subscription?.cancel()
    Thread.sleep(6000)

    val messages = onNextList.filterIsInstance<Message>()
    val onNextStringList = messages.map { it.data.data.toString() }
    assertThat(onNextStringList)
      .containsExactly(
        "{chunk=hello}",
      )
    assertThat(throwable).isNotNull()
    assertThat(requireNotNull(throwable).message).isEqualTo("Stream was canceled")
    assertThat(isComplete).isFalse()
  }
}
