// Copyright 2024 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.dataconnect.connectors.demo

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.google.firebase.dataconnect.connectors.demo.testutil.DemoConnectorIntegrationTestBase
import com.google.firebase.dataconnect.testutil.containsWithNonAdjacentText
import java.util.concurrent.Executors
import java.util.concurrent.Future
import org.junit.Test

class DemoConnectorIntegrationTest : DemoConnectorIntegrationTestBase() {

  @Test
  fun getFooById_ShouldAlwaysReturnTheExactSameObject() {
    verifyBlockInvokedConcurrentlyAlwaysReturnsTheSameObject { connector.getFooById }
  }

  @Test
  fun equals_ShouldReturnFalseWhenArgumentIsNull() {
    assertThat(connector.equals(null)).isFalse()
  }

  @Test
  fun equals_ShouldReturnFalseWhenArgumentIsAnInstanceOfADifferentClass() {
    assertThat(connector.equals("foo")).isFalse()
  }

  @Test
  fun equals_ShouldReturnFalseWhenInvokedOnADistinctObject() {
    assertThat(connector.equals(demoConnectorFactory.newInstance())).isFalse()
  }

  @Test
  fun equals_ShouldReturnFalseWhenInvokedOnTheSameObjectAfterClose() {
    val connector1 = demoConnectorFactory.newInstance()
    connector1.dataConnect.close()
    val connector2 = demoConnectorFactory.newInstance()
    assertThat(connector1).isNotSameInstanceAs(connector2)

    assertThat(connector1.equals(connector2)).isFalse()
  }

  @Test
  fun equals_ShouldReturnFalseWhenInvokedOnAnApparentlyEqualButDifferentImplementation() {
    val connectorAlternateImpl = DemoConnectorAlternateImpl(connector)

    assertThat(connector.equals(connectorAlternateImpl)).isFalse()
  }

  @Test
  fun hashCode_ShouldReturnSameValueOnEachInvocation() {
    val hashCode1 = connector.hashCode()
    val hashCode2 = connector.hashCode()

    assertThat(hashCode1).isEqualTo(hashCode2)
  }

  @Test
  fun hashCode_ShouldReturnDistinctValuesOnDistinctInstances() {
    val hashCode1 = demoConnectorFactory.newInstance().hashCode()
    val hashCode2 = demoConnectorFactory.newInstance().hashCode()

    assertThat(hashCode1).isNotEqualTo(hashCode2)
  }

  @Test
  fun toString_ShouldReturnAStringThatStartsWithClassName() {
    assertThat("$connector").startsWith("DemoConnectorImpl(")
    assertThat("$connector").endsWith(")")
  }

  @Test
  fun toString_ShouldReturnAStringThatContainsTheToStringOfTheDataConnectInstance() {
    assertThat("$connector").containsWithNonAdjacentText("dataConnect=${connector.dataConnect}")
  }

  class DemoConnectorAlternateImpl(delegate: DemoConnector) : DemoConnector by delegate

  // TODO: Write tests for each property in DemoConnector.

  private fun <T> verifyBlockInvokedConcurrentlyAlwaysReturnsTheSameObject(block: () -> T) {
    val results = mutableListOf<T>()
    val futures = mutableListOf<Future<*>>()
    val executor = Executors.newFixedThreadPool(6)
    try {
      repeat(1000) {
        executor
          .submit {
            val result = block()
            synchronized(results) { results.add(result) }
          }
          .also { futures.add(it) }
      }

      futures.forEach { it.get() }
    } finally {
      executor.shutdownNow()
    }

    assertWithMessage("results.size").that(results.size).isGreaterThan(0)
    val expectedResults = List(1000) { results[0] }
    assertWithMessage("results").that(results).containsExactlyElementsIn(expectedResults)
  }
}
