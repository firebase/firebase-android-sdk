package com.google.firebase.functions

import android.app.Activity
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executor

public class StreamFunctionsTask : Task<HttpsCallableResult>() {

  private val listenerQueue: Queue<StreamListener> = ConcurrentLinkedQueue()
  private var result: HttpsCallableResult? = null
  private var exception: Exception? = null
  private var isComplete: Boolean = false
  private var isCanceled = false

  public fun addOnStreamListener(listener: StreamListener): StreamFunctionsTask {
    listenerQueue.add(listener)
    return this
  }

  public fun removeOnStreamListener(listener: StreamListener) {
    listenerQueue.remove(listener)
  }

  internal fun notifyListeners(data: Any) {
    for (listener in listenerQueue) {
      listener.onNext(data)
    }
  }

  internal fun complete(result: HttpsCallableResult) {
    this.result = result
    this.isComplete = true
  }

  internal fun fail(exception: Exception) {
    this.exception = exception
    this.isComplete = true
  }

  override fun getException(): Exception? {
    listenerQueue.clear()
    return exception
  }

  override fun getResult(): HttpsCallableResult {
    listenerQueue.clear()
    return result ?: throw IllegalStateException("No result available.")
  }

  override fun <X : Throwable?> getResult(p0: Class<X>): HttpsCallableResult {
    if (p0.isInstance(exception)) {
      throw p0.cast(exception)!!
    }
    return getResult()
  }

  override fun addOnFailureListener(listener: OnFailureListener): Task<HttpsCallableResult> {
    if (exception != null) listener.onFailure(requireNotNull(exception))
    return this
  }

  override fun addOnFailureListener(
    activity: Activity,
    listener: OnFailureListener
  ): Task<HttpsCallableResult> {
    if (exception != null) listener.onFailure(requireNotNull(exception))
    return this
  }

  override fun addOnFailureListener(
    executor: Executor,
    listener: OnFailureListener
  ): Task<HttpsCallableResult> {
    if (exception != null) executor.execute { listener.onFailure(requireNotNull(exception)) }
    return this
  }

  override fun addOnSuccessListener(
    executor: Executor,
    listener: OnSuccessListener<in HttpsCallableResult>
  ): Task<HttpsCallableResult> {
    if (result != null) executor.execute { listener.onSuccess(requireNotNull(result)) }
    return this
  }

  override fun addOnSuccessListener(
    activity: Activity,
    listener: OnSuccessListener<in HttpsCallableResult>
  ): Task<HttpsCallableResult> {
    if (result != null) listener.onSuccess(requireNotNull(result))
    return this
  }

  override fun addOnSuccessListener(
    listener: OnSuccessListener<in HttpsCallableResult>
  ): Task<HttpsCallableResult> {
    if (result != null) listener.onSuccess(requireNotNull(result))
    return this
  }

  override fun isCanceled(): Boolean {
    return isCanceled
  }

  override fun isComplete(): Boolean {
    return isComplete
  }

  override fun isSuccessful(): Boolean {
    return exception == null && result != null
  }

  public fun cancel() {
    isCanceled = true
    listenerQueue.clear()
  }
}
