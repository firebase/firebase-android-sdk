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

@file:Suppress("ReplaceCallWithBinaryOperator")

package com.google.firebase.dataconnect.connectors.demo

import com.google.firebase.dataconnect.connectors.demo.testutil.DemoConnectorIntegrationTestBase
import com.google.firebase.dataconnect.testutil.SuspendingCountDownLatch
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DemoConnectorIntegrationTest : DemoConnectorIntegrationTestBase() {

  @Test
  fun getFooById_ShouldAlwaysReturnTheExactSameObject() = runTest {
    verifyBlockInvokedConcurrentlyAlwaysReturnsTheSameObject { connector.getFooById }
  }

  @Test
  fun equals_ShouldReturnFalseWhenArgumentIsNull() {
    connector.equals(null) shouldBe false
  }

  @Test
  fun equals_ShouldReturnFalseWhenArgumentIsAnInstanceOfADifferentClass() {
    connector.equals("foo") shouldBe false
  }

  @Test
  fun equals_ShouldReturnFalseWhenInvokedOnADistinctObject() {
    connector.equals(demoConnectorFactory.newInstance()) shouldBe false
  }

  @Test
  fun equals_ShouldReturnFalseWhenInvokedOnTheSameObjectAfterClose() {
    val connector1 = demoConnectorFactory.newInstance()
    connector1.dataConnect.close()
    val connector2 = demoConnectorFactory.newInstance()
    connector1 shouldNotBeSameInstanceAs connector2

    connector1.equals(connector2) shouldBe false
  }

  @Test
  fun equals_ShouldReturnFalseWhenInvokedOnAnApparentlyEqualButDifferentImplementation() {
    val connectorAlternateImpl = DemoConnectorAlternateImpl(connector)

    connector.equals(connectorAlternateImpl) shouldBe false
  }

  @Test
  fun hashCode_ShouldReturnSameValueOnEachInvocation() {
    val hashCode1 = connector.hashCode()
    val hashCode2 = connector.hashCode()

    hashCode1 shouldBe hashCode2
  }

  @Test
  fun hashCode_ShouldReturnDistinctValuesOnDistinctInstances() {
    val hashCode1 = demoConnectorFactory.newInstance().hashCode()
    val hashCode2 = demoConnectorFactory.newInstance().hashCode()

    hashCode1 shouldNotBe hashCode2
  }

  @Test
  fun toString_ShouldReturnAStringWithCorrectComponents() {
    val toStringResult = connector.toString()

    assertSoftly {
      toStringResult shouldStartWith "DemoConnectorImpl("
      toStringResult shouldContainWithNonAbuttingText "dataConnect=${connector.dataConnect}"
      toStringResult shouldEndWith ")"
    }
  }

  class DemoConnectorAlternateImpl(delegate: DemoConnector) : DemoConnector by delegate

  // TODO: Write tests for each property in DemoConnector.

  private suspend fun <T> TestScope.verifyBlockInvokedConcurrentlyAlwaysReturnsTheSameObject(
    block: () -> T
  ) {
    val resultsMutex = Mutex()
    val results = mutableListOf<T>()
    val numCoroutines = 1000
    val latch = SuspendingCountDownLatch(numCoroutines)

    // Start the coroutines.
    val coroutines =
      List(numCoroutines) {
        // Use Dispatchers.Default, which guarantees at least threads.
        backgroundScope.launch(Dispatchers.Default) {
          latch.countDown()
          val result = block()
          resultsMutex.withLock { results.add(result) }
        }
      }

    // Wait for each coroutine to finish.
    coroutines.forEach { it.join() }

    val expectedResults = List(1000) { results[0] }
    results shouldContainExactly expectedResults
  }
}
