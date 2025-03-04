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
    internal val messages = mutableListOf<StreamResponse.Message>()
    internal var result: StreamResponse.Result? = null
    internal var throwable: Throwable? = null
    internal var isComplete = false
    internal lateinit var subscription: Subscription

    override fun onSubscribe(subscription: Subscription) {
      this.subscription = subscription
      subscription.request(Long.MAX_VALUE)
    }

    override fun onNext(streamResponse: StreamResponse) {
      if (streamResponse is StreamResponse.Message) {
        messages.add(streamResponse)
      } else {
        result = streamResponse as StreamResponse.Result
      }
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
    assertThat(subscriber.messages.map { it.message.data.toString() })
      .containsExactly(
        "{chunk=hello}",
        "{chunk=world}",
        "{chunk=this}",
        "{chunk=is}",
        "{chunk=cool}"
      )
    assertThat(subscriber.result).isNotNull()
    assertThat(subscriber.result!!.result.data.toString()).isEqualTo("hello world this is cool")
    assertThat(subscriber.throwable).isNull()
    assertThat(subscriber.isComplete).isTrue()
  }

  @Test
  fun genStream_withFlow_receivesMessagesAndFinalResult() = runBlocking {
    val input = mapOf("data" to "Why is the sky blue")
    val function = functions.getHttpsCallable("genStream")
    var isComplete = false
    var throwable: Throwable? = null
    val messages = mutableListOf<StreamResponse.Message>()
    var result: StreamResponse.Result? = null

    val flow = function.stream(input).asFlow()
    try {
      withTimeout(1000) {
        flow.collect { response ->
          if (response is StreamResponse.Message) {
            messages.add(response)
          } else {
            result = response as StreamResponse.Result
          }
        }
      }
      isComplete = true
    } catch (e: Throwable) {
      throwable = e
    }

    assertThat(messages.map { it.message.data.toString() })
      .containsExactly(
        "{chunk=hello}",
        "{chunk=world}",
        "{chunk=this}",
        "{chunk=is}",
        "{chunk=cool}"
      )
    assertThat(result).isNotNull()
    assertThat(result!!.result.data.toString()).isEqualTo("hello world this is cool")
    assertThat(throwable).isNull()
    assertThat(isComplete).isTrue()
  }

  @Test
  fun genStreamError_receivesErrorAndStops() = runBlocking {
    val input = mapOf("data" to "Why is the sky blue")
    val function =
      functions.getHttpsCallable("genStreamError").withTimeout(2000, TimeUnit.MILLISECONDS)
    val subscriber = StreamSubscriber()

    function.stream(input).subscribe(subscriber)
    withTimeout(2000) {
      while (subscriber.throwable == null) {
        delay(100)
      }
    }

    assertThat(subscriber.messages.map { it.message.data.toString() }).contains("{chunk=hello}")
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

    withTimeout(2000) {
      while (subscriber.messages.size < 5) {
        delay(100)
      }
    }
    assertThat(subscriber.messages.map { it.message.data.toString() })
      .containsExactly(
        "{chunk=hello}",
        "{chunk=world}",
        "{chunk=this}",
        "{chunk=is}",
        "{chunk=cool}"
      )
    assertThat(subscriber.result).isNull()
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
    withTimeout(2000) {
      while (cancelableSubscriber.messages.isEmpty()) {
        delay(50)
      }
    }
    withTimeout(2000) { cancelableSubscriber.subscription.cancel() }
    withTimeout(1000) {
      while (cancelableSubscriber.throwable == null) {
        delay(300)
      }
    }
    val messagesAsStringList = cancelableSubscriber.messages.map { it.message.data.toString() }
    assertThat(messagesAsStringList).contains("{chunk=hello}")
    assertThat(messagesAsStringList).doesNotContain("{chunk=cool}")
    assertThat(cancelableSubscriber.throwable!!.message!!.uppercase()).contains("CANCEL")
    assertThat(cancelableSubscriber.isComplete).isFalse()
  }

  @Test
  fun genStreamWeather_receivesWeatherForecasts() = runBlocking {
    val inputData = listOf(mapOf("name" to "Toronto"), mapOf("name" to "London"))
    val input = mapOf("data" to inputData)

    val function = functions.getHttpsCallable("genStreamWeather")
    val subscriber = StreamSubscriber()

    function.stream(input).subscribe(subscriber)

    while (!subscriber.isComplete) {
      delay(100)
    }

    assertThat(subscriber.messages.map { it.message.data.toString() })
      .containsExactly(
        "{temperature=25, location=Toronto, conditions=snowy}",
        "{temperature=50, location=London, conditions=rainy}"
      )
    assertThat(subscriber.result).isNotNull()
    assertThat(subscriber.result!!.result.data.toString()).contains("forecasts")
    assertThat(subscriber.throwable).isNull()
    assertThat(subscriber.isComplete).isTrue()
  }
}
