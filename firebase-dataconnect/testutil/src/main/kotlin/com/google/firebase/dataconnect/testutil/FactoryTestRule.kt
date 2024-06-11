/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
    check(active.get()) { "newInstance() may only be called during the test's execution" }
    val instance = createInstance(params)
    instances.add(instance)
    return instance
  }

  fun adoptInstance(instance: T) {
    check(active.get()) { "adoptInstance() may only be called during the test's execution" }
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
