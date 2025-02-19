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

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.firebase.Firebase
import com.google.firebase.functions.StreamResponse.Message
import com.google.firebase.functions.StreamResponse.Result
import com.google.firebase.initialize
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription

@RunWith(AndroidJUnit4::class)
class StreamTests {

  private lateinit var functions: FirebaseFunctions

  @Before
  fun setup() {
    Firebase.initialize(ApplicationProvider.getApplicationContext())
    functions = Firebase.functions
  }

  internal class StreamSubscriber : Subscriber<StreamResponse> {
    internal val onNextList = mutableListOf<StreamResponse>()
    internal var throwable: Throwable? = null
    internal var isComplete = false
    internal var subscription: Subscription? = null

    override fun onSubscribe(subscription: Subscription?) {
      this.subscription = subscription
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

  @Test
  fun genStream_withPublisher_receivesMessagesAndFinalResult() = runBlocking {
    val input = mapOf("data" to "Why is the sky blue")
    val function = functions.getHttpsCallable("genStream")
    val subscriber = StreamSubscriber()

    function.stream(input).subscribe(subscriber)

    while (!subscriber.isComplete) {
      delay(100)
    }
    val messages = subscriber.onNextList.filterIsInstance<Message>()
    val results = subscriber.onNextList.filterIsInstance<Result>()
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
    assertThat(subscriber.throwable).isNull()
    assertThat(subscriber.isComplete).isTrue()
  }

  @Test
  fun genStream_withFlow_receivesMessagesAndFinalResult() = runBlocking {
    val input = mapOf("data" to "Why is the sky blue")
    val function = functions.getHttpsCallable("genStream")
    var isComplete = false
    var throwable: Throwable? = null

    val flow = function.stream(input).asFlow()
    val receivedResponses = mutableListOf<StreamResponse>()
    try {
      withTimeout(1000) { flow.collect { response -> receivedResponses.add(response) } }
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
  fun genStreamError_receivesErrorAndStops() = runBlocking {
    val input = mapOf("data" to "Why is the sky blue")
    val function =
      functions.getHttpsCallable("genStreamError").withTimeout(1000, TimeUnit.MILLISECONDS)
    val subscriber = StreamSubscriber()

    function.stream(input).subscribe(subscriber)
    delay(1000)

    val messages = subscriber.onNextList.filterIsInstance<Message>()
    val onNextStringList = messages.map { it.data.data.toString() }
    assertThat(onNextStringList).contains("{chunk=hello}")
    assertThat(subscriber.throwable).isNotNull()
    assertThat(subscriber.throwable!!.message).isEqualTo("{message=INTERNAL, status=INTERNAL}")
    assertThat(subscriber.isComplete).isFalse()
  }

  @Test
  fun genStreamNoReturn_receivesOnlyMessages() = runBlocking {
    val input = mapOf("data" to "Why is the sky blue")
    val function = functions.getHttpsCallable("genStreamNoReturn")
    val subscriber = StreamSubscriber()

    function.stream(input).subscribe(subscriber)

    while (subscriber.onNextList.size < 5) {
      delay(100)
    }
    val messages = subscriber.onNextList.filterIsInstance<Message>()
    val results = subscriber.onNextList.filterIsInstance<Result>()
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
    assertThat(subscriber.throwable).isNull()
    assertThat(subscriber.isComplete).isFalse()
  }

  @Test
  fun genStream_cancelStream_receivesPartialMessagesAndError() = runBlocking {
    val input = mapOf("data" to "Why is the sky blue")
    val function = functions.getHttpsCallable("genStreamNoReturn")
    val publisher = function.stream(input)
    val cancelableSubscriber = StreamSubscriber()
    publisher.subscribe(cancelableSubscriber)
    while (cancelableSubscriber.onNextList.isEmpty()) {
      delay(50)
    }
    cancelableSubscriber.subscription?.cancel()

    while (cancelableSubscriber.throwable == null) {
      delay(300)
    }
    val messages = cancelableSubscriber.onNextList.filterIsInstance<Message>()
    val onNextStringList = messages.map { it.data.data.toString() }
    assertThat(onNextStringList).containsExactly("{chunk=hello}")
    assertThat(onNextStringList).doesNotContain("{chunk=cool}")
    assertThat(cancelableSubscriber.throwable!!.message!!.uppercase()).contains("CANCEL")
    assertThat(cancelableSubscriber.isComplete).isFalse()
  }
}
