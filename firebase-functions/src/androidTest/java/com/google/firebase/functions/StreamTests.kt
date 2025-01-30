package com.google.firebase.functions.ktx

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.firebase.Firebase
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.StreamFunctionsTask
import com.google.firebase.functions.StreamListener
import com.google.firebase.functions.functions
import com.google.firebase.initialize
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StreamTests {

  private lateinit var listener: StreamListener
  private lateinit var functions: FirebaseFunctions
  var onNext = mutableListOf<Any>()

  @Before
  fun setup() {
    Firebase.initialize(ApplicationProvider.getApplicationContext())
    functions = Firebase.functions
    listener =
      object : StreamListener {
        override fun onNext(message: Any) {
          onNext.add(message)
        }
      }
  }

  @After
  fun clear() {
    onNext.clear()
  }

  @Test
  fun testGenStream() {
    val input = hashMapOf("data" to "Why is the sky blue")
    val function = functions.getHttpsCallable("genStream")

    val task = function.stream(input).addOnStreamListener(listener)

    Thread.sleep(6000)
    val onNextStringList = onNext.map { it.toString() }
    assertThat(onNextStringList)
      .containsExactly(
        "{chunk=hello}",
        "{chunk=world}",
        "{chunk=this}",
        "{chunk=is}",
        "{chunk=cool}"
      )
    assertThat(task.result.data).isEqualTo("hello world this is cool")
  }

  @Test
  fun testGenStreamError() {
    val input = hashMapOf("data" to "Why is the sky blue")
    val function = functions.getHttpsCallable("genStreamError").withTimeout(6, TimeUnit.SECONDS)
    var task: StreamFunctionsTask? = null

    try {
      task = function.stream(input).addOnStreamListener(listener)
    } catch (_: Throwable) {}
    Thread.sleep(7000)

    val onNextStringList = onNext.map { it.toString() }
    assertThat(onNextStringList)
      .containsExactly(
        "{chunk=hello}",
        "{chunk=world}",
        "{chunk=this}",
        "{chunk=is}",
        "{chunk=cool}"
      )
    assertThat(requireNotNull(task).isSuccessful).isFalse()
    assertThat(task.exception).isInstanceOf(FirebaseFunctionsException::class.java)
    assertThat(requireNotNull(task.exception).message).contains("stream was reset: CANCEL")
  }

  @Test
  fun testGenStreamNoReturn() {
    val input = hashMapOf("data" to "Why is the sky blue")
    val function = functions.getHttpsCallable("genStreamNoReturn")

    val task = function.stream(input).addOnStreamListener(listener)
    Thread.sleep(7000)

    val onNextStringList = onNext.map { it.toString() }
    assertThat(onNextStringList)
      .containsExactly(
        "{chunk=hello}",
        "{chunk=world}",
        "{chunk=this}",
        "{chunk=is}",
        "{chunk=cool}"
      )
    try {
      task.result
    } catch (e: Throwable) {
      assertThat(e).isInstanceOf(IllegalStateException::class.java)
      assertThat(e.message).isEqualTo("No result available.")
    }
  }

  @Test
  fun testGenStream_cancelStream() {
    val input = hashMapOf("data" to "Why is the sky blue")
    val function = functions.getHttpsCallable("genStreamNoReturn")
    val task = function.stream(input).addOnStreamListener(listener)
    Thread.sleep(2000)

    task.cancel()

    val onNextStringList = onNext.map { it.toString() }
    assertThat(onNextStringList)
      .containsExactly(
        "{chunk=hello}",
        "{chunk=world}",
      )
    try {
      task.result
    } catch (e: Throwable) {
      assertThat(e).isInstanceOf(IllegalStateException::class.java)
      assertThat(e.message).isEqualTo("No result available.")
    }
    assertThat(task.isCanceled).isTrue()
    assertThat(task.isComplete).isFalse()
    assertThat(task.isSuccessful).isFalse()
  }
}
