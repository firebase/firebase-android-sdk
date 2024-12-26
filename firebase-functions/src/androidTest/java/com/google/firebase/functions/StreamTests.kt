package com.google.firebase.functions.ktx

import androidx.test.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import com.google.android.gms.tasks.Tasks
import com.google.common.truth.Truth.assertThat
import com.google.firebase.FirebaseApp
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.SSETaskListener
import com.google.firebase.ktx.Firebase
import com.google.firebase.ktx.initialize
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StreamTests {

  private lateinit var app: FirebaseApp
  private lateinit var listener: SSETaskListener

  private lateinit var functions: FirebaseFunctions
  var onNext = mutableListOf<Any>()
  var onError: Any? = null
  var onComplete: Any? = null

  @Before
  fun setup() {
    app = Firebase.initialize(InstrumentationRegistry.getContext())!!
    functions = FirebaseFunctions.getInstance()
    listener =
      object : SSETaskListener {
        override fun onNext(event: Any) {
          onNext.add(event)
        }

        override fun onError(event: Any) {
          onError = event
        }

        override fun onComplete(event: Any) {
          onComplete = event
        }
      }
  }

  @After
  fun clear() {
    onNext.clear()
    onError = null
    onComplete = null
  }

  @Test
  fun testGenStream() {
    val input = hashMapOf("data" to "Why is the sky blue")

    val function = functions.getHttpsCallable("genStream")
    val httpsCallableResult = Tasks.await(function.stream(input, listener))

    val onNextStringList = onNext.map { it.toString() }
    assertThat(onNextStringList)
      .containsExactly(
        "{chunk=hello}",
        "{chunk=world}",
        "{chunk=this}",
        "{chunk=is}",
        "{chunk=cool}"
      )
    assertThat(onError).isNull()
    assertThat(onComplete).isEqualTo("hello world this is cool")
    assertThat(httpsCallableResult.data).isEqualTo("hello world this is cool")
  }

  @Test
  fun testGenStreamError() {
    val input = hashMapOf("data" to "Why is the sky blue")
    val function = functions.getHttpsCallable("genStreamError").withTimeout(7, TimeUnit.SECONDS)

    try {
      Tasks.await(function.stream(input, listener))
    } catch (exception: Exception) {
      onError = exception
    }

    val onNextStringList = onNext.map { it.toString() }
    assertThat(onNextStringList)
      .containsExactly(
        "{chunk=hello}",
        "{chunk=world}",
        "{chunk=this}",
        "{chunk=is}",
        "{chunk=cool}"
      )
    assertThat(onError).isInstanceOf(ExecutionException::class.java)
    val cause = (onError as ExecutionException).cause
    assertThat(cause).isInstanceOf(FirebaseFunctionsException::class.java)
    assertThat((cause as FirebaseFunctionsException).message).contains("stream was reset: CANCEL")
    assertThat(onComplete).isNull()
  }

  @Test
  fun testGenStreamNoReturn() {
    val input = hashMapOf("data" to "Why is the sky blue")

    val function = functions.getHttpsCallable("genStreamNoReturn")
    try {
      Tasks.await(function.stream(input, listener), 7, TimeUnit.SECONDS)
    } catch (_: Exception) {}

    val onNextStringList = onNext.map { it.toString() }
    assertThat(onNextStringList)
      .containsExactly(
        "{chunk=hello}",
        "{chunk=world}",
        "{chunk=this}",
        "{chunk=is}",
        "{chunk=cool}"
      )
    assertThat(onError).isNull()
    assertThat(onComplete).isNull()
  }
}
