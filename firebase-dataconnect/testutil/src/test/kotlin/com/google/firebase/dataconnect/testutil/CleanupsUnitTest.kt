/*
 * Copyright 2026 Google LLC
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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CleanupsUnitTest {

  @Test
  fun `register autoCloseable executes close on close`() {
    val cleanups = Cleanups()
    val closed = AtomicBoolean(false)
    val autoCloseable = AutoCloseable { closed.set(true) }

    cleanups.register(autoCloseable)
    closed.get() shouldBe false

    cleanups.close()
    closed.get() shouldBe true
  }

  @Test
  fun `register lambda executes lambda on close`() {
    val cleanups = Cleanups()
    val closed = AtomicBoolean(false)

    cleanups.register { closed.set(true) }
    closed.get() shouldBe false

    cleanups.close()
    closed.get() shouldBe true
  }

  @Test
  fun `register named lambda executes lambda on close`() {
    val cleanups = Cleanups()
    val closed = AtomicBoolean(false)

    cleanups.register(name = "test-cleanup") { closed.set(true) }
    closed.get() shouldBe false

    cleanups.close()
    closed.get() shouldBe true
  }

  @Test
  fun `registerSuspending lambda executes lambda on close`() = runTest {
    val cleanups = Cleanups()
    val closed = AtomicBoolean(false)

    cleanups.registerSuspending { closed.set(true) }
    closed.get() shouldBe false

    cleanups.close()
    closed.get() shouldBe true
  }

  @Test
  fun `registerSuspending named lambda executes lambda on close`() = runTest {
    val cleanups = Cleanups()
    val closed = AtomicBoolean(false)

    cleanups.registerSuspending(name = "test-suspending") { closed.set(true) }
    closed.get() shouldBe false

    cleanups.close()
    closed.get() shouldBe true
  }

  @Test
  fun `cleanups run in reverse order of registration`() {
    val cleanups = Cleanups()
    val executionOrder = mutableListOf<String>()

    cleanups.register { executionOrder.add("first") }
    cleanups.register { executionOrder.add("second") }
    cleanups.register { executionOrder.add("third") }

    cleanups.close()

    executionOrder shouldContainExactly listOf("third", "second", "first")
  }

  @Test
  fun `close multiple times throws IllegalStateException`() {
    val cleanups = Cleanups()
    cleanups.close()

    val exception = shouldThrow<IllegalStateException> { cleanups.close() }
    exception.message shouldContainWithNonAbuttingText "hfteew3829"
    exception.message shouldContainWithNonAbuttingTextIgnoringCase "already been called"
  }

  @Test
  fun `register after close throws IllegalStateException`() {
    val cleanups = Cleanups()
    cleanups.close()

    val exception = shouldThrow<IllegalStateException> { cleanups.register {} }
    exception.message shouldContainWithNonAbuttingText "n3jsqd4d2f"
    exception.message shouldContainWithNonAbuttingTextIgnoringCase "failed to register cleanup"
    exception.message shouldContainWithNonAbuttingTextIgnoringCase "close() has been called"
  }

  @Test
  fun `registerSuspending after close throws IllegalStateException`() {
    val cleanups = Cleanups()
    cleanups.close()

    val exception = shouldThrow<IllegalStateException> { cleanups.registerSuspending {} }
    exception.message shouldContainWithNonAbuttingText "n3jsqd4d2f"
    exception.message shouldContainWithNonAbuttingTextIgnoringCase "failed to register cleanup"
    exception.message shouldContainWithNonAbuttingTextIgnoringCase "close() has been called"
  }

  @Test
  fun `cleanup throws exception does not prevent other cleanups from running`() {
    val cleanups = Cleanups()
    val executionOrder = mutableListOf<String>()

    cleanups.register { executionOrder.add("first") }
    cleanups.register { throw RuntimeException("fail") }
    cleanups.register { executionOrder.add("third") }

    val exception = shouldThrow<RuntimeException> { cleanups.close() }
    exception.message shouldBe "fail"
    executionOrder shouldContainExactly listOf("third", "first")
  }

  @Test
  fun `multiple cleanups throw exception accumulates them as suppressed`() {
    val cleanups = Cleanups()
    val executionOrder = mutableListOf<String>()

    cleanups.register { executionOrder.add("first") }
    cleanups.register { throw RuntimeException("fail 1") }
    cleanups.register { throw RuntimeException("fail 2") }
    cleanups.register { executionOrder.add("fourth") }

    val exception = shouldThrow<RuntimeException> { cleanups.close() }
    exception.message shouldBe "fail 2"
    exception.suppressed.toList().map { it.message } shouldContainExactly listOf("fail 1")
    executionOrder shouldContainExactly listOf("fourth", "first")
  }
}
