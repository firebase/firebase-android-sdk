package com.google.firebase.dataconnect.testutil

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.rules.ExternalResource

/**
 * A JUnit test rule that creates instances of an object for use during testing, and cleans them
 * upon test completion.
 */
abstract class FactoryTestRule<T, P> : ExternalResource() {

  private val active = AtomicBoolean(false)
  private val instances = CopyOnWriteArrayList<T>()

  fun newInstance(params: P? = null): T {
    if (!active.get()) {
      throw IllegalStateException("newInstance() may only be called during the test's execution")
    }
    val instance = createInstance(params)
    instances.add(instance)
    return instance
  }

  fun adoptInstance(instance: T) {
    if (!active.get()) {
      throw IllegalStateException("adoptInstance() may only be called during the test's execution")
    }
    instances.add(instance)
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

  protected abstract fun createInstance(params: P?): T
  protected abstract fun destroyInstance(instance: T)
}
