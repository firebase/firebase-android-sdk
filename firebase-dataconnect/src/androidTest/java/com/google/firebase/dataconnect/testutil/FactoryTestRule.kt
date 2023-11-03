package com.google.firebase.dataconnect.testutil

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import org.junit.rules.ExternalResource

/**
 * A JUnit test rule that creates instances of an object for use during testing, and cleans them
 * upon test completion.
 */
abstract class FactoryTestRule<T, P>(startId: Long) : ExternalResource() {

  private val nextId = AtomicLong(startId)
  private val active = AtomicBoolean(false)
  private val instances = CopyOnWriteArrayList<T>()

  fun newInstance(params: P? = null): T {
    if (!active.get()) {
      throw IllegalStateException("newInstance() may only be called during the test's execution")
    }
    val instance = createInstance(nextId.getAndIncrement().toString(16), params)
    instances.add(instance)
    return instance
  }

  override fun before() {
    active.set(true)
  }

  override fun after() {
    active.set(false)
    while (instances.isNotEmpty()) {
      destroyInstance(instances.removeLast())
    }
  }

  protected abstract fun createInstance(instanceId: String, params: P?): T
  protected abstract fun destroyInstance(instance: T)
}
