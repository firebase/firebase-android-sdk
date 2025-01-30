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

  public fun addOnStreamListener(listener: StreamListener): StreamFunctionsTask {
    listenerQueue.add(listener)
    // TODO: Attach the listener
    return this
  }

  public fun removeOnStreamListener(listener: StreamListener) {
    listenerQueue.remove(listener)
    // TODO: Remove the listener
  }

  // ALL OVERRIDES LISTED BELOW ARE FROM THE Task INTERFACE
  override fun getException(): Exception? {
    listenerQueue.clear()
    TODO("Not yet implemented")
  }

  override fun getResult(): HttpsCallableResult {
    listenerQueue.clear()
    TODO("Not yet implemented")
  }

  override fun addOnFailureListener(p0: OnFailureListener): Task<HttpsCallableResult> {
    TODO("Not yet implemented")
  }

  override fun addOnFailureListener(
    p0: Activity,
    p1: OnFailureListener
  ): Task<HttpsCallableResult> {
    TODO("Not yet implemented")
  }

  override fun addOnFailureListener(
    p0: Executor,
    p1: OnFailureListener
  ): Task<HttpsCallableResult> {
    TODO("Not yet implemented")
  }

  override fun addOnSuccessListener(
    p0: Executor,
    p1: OnSuccessListener<in HttpsCallableResult>
  ): Task<HttpsCallableResult> {
    TODO("Not yet implemented")
  }

  override fun addOnSuccessListener(
    p0: Activity,
    p1: OnSuccessListener<in HttpsCallableResult>
  ): Task<HttpsCallableResult> {
    TODO("Not yet implemented")
  }

  override fun addOnSuccessListener(p0: OnSuccessListener<in HttpsCallableResult>): Task<HttpsCallableResult> {
    TODO("Not yet implemented")
  }


  override fun <X : Throwable?> getResult(p0: Class<X>): HttpsCallableResult {
    TODO("Not yet implemented")
  }

  override fun isCanceled(): Boolean {
    TODO("Not yet implemented")
  }

  override fun isComplete(): Boolean {
    TODO("Not yet implemented")
  }

  override fun isSuccessful(): Boolean {
    TODO("Not yet implemented")
  }


}