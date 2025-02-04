package com.google.firebase.functions.ktx

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.firebase.Firebase
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.functions
import com.google.firebase.initialize
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription

@RunWith(AndroidJUnit4::class)
class StreamTests {

  private lateinit var functions: FirebaseFunctions
  var onNextList = mutableListOf<Any>()
  private lateinit var subscriber: Subscriber<Any>
  private var throwable: Throwable? = null
  private var isComplete = false

  @Before
  fun setup() {
    Firebase.initialize(ApplicationProvider.getApplicationContext())
    functions = Firebase.functions
    subscriber =
      object : Subscriber<Any> {
        override fun onSubscribe(subscription: Subscription?) {
          subscription?.request(1)
        }

        override fun onNext(t: Any) {
          onNextList.add(t)
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
  fun testGenStream() {
    val input = hashMapOf("data" to "Why is the sky blue")
    val function = functions.getHttpsCallable("genStream")

    function.stream(input).subscribe(subscriber)

    Thread.sleep(8000)
    val onNextStringList = onNextList.map { it.toString() }
    assertThat(onNextStringList)
      .containsExactly(
        "{chunk=hello}",
        "{chunk=world}",
        "{chunk=this}",
        "{chunk=is}",
        "{chunk=cool}",
        "hello world this is cool"
      )
    assertThat(throwable).isNull()
    assertThat(isComplete).isTrue()
  }

  @Test
  fun testGenStreamError() {
    val input = hashMapOf("data" to "Why is the sky blue")
    val function = functions.getHttpsCallable("genStreamError").withTimeout(1, TimeUnit.SECONDS)

    function.stream(input).subscribe(subscriber)
    Thread.sleep(8000)

    val onNextStringList = onNextList.map { it.toString() }
    assertThat(onNextStringList)
      .containsExactly(
        "{chunk=hello}",
      )
    assertThat(throwable).isNotNull()
    assertThat(requireNotNull(throwable).message).isEqualTo("timeout")
    assertThat(isComplete).isFalse()
  }

  @Test
  fun testGenStreamNoReturn() {
    val input = hashMapOf("data" to "Why is the sky blue")
    val function = functions.getHttpsCallable("genStreamNoReturn")

    function.stream(input).subscribe(subscriber)
    Thread.sleep(8000)

    val onNextStringList = onNextList.map { it.toString() }
    assertThat(onNextStringList)
      .containsExactly(
        "{chunk=hello}",
        "{chunk=world}",
        "{chunk=this}",
        "{chunk=is}",
        "{chunk=cool}"
      )
    assertThat(isComplete).isFalse()
  }

  @Test
  fun testGenStream_cancelStream() {
    val input = hashMapOf("data" to "Why is the sky blue")
    val function = functions.getHttpsCallable("genStreamNoReturn")
    val publisher = function.stream(input)
    var subscription: Subscription? = null
    val cancelableSubscriber =
      object : Subscriber<Any> {
        override fun onSubscribe(s: Subscription?) {
          subscription = s
          s?.request(1)
        }

        override fun onNext(message: Any) {
          onNextList.add(message)
        }

        override fun onError(t: Throwable?) {
          throwable = t
        }

        override fun onComplete() {
          isComplete = true
        }
      }

    publisher.subscribe(cancelableSubscriber)
    Thread.sleep(2000)
    subscription?.cancel()
    Thread.sleep(6000)

    val onNextStringList = onNextList.map { it.toString() }
    assertThat(onNextStringList)
      .containsExactly(
        "{chunk=hello}",
        "{chunk=world}",
      )
    assertThat(throwable).isNotNull()
    assertThat(requireNotNull(throwable).message).isEqualTo("Stream was canceled")
    assertThat(isComplete).isFalse()
  }
}
