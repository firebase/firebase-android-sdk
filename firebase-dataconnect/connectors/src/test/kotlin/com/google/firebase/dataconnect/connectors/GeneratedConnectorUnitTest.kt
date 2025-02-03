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

@file:OptIn(ExperimentalKotest::class, ExperimentalFirebaseDataConnect::class)

package com.google.firebase.dataconnect.connectors

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.dataconnect.ConnectorConfig
import com.google.firebase.dataconnect.DataConnectSettings
import com.google.firebase.dataconnect.ExperimentalFirebaseDataConnect
import com.google.firebase.dataconnect.FirebaseDataConnect
import com.google.firebase.dataconnect.connectors.demo.DemoConnector
import com.google.firebase.dataconnect.connectors.demo.getInstance
import com.google.firebase.dataconnect.generated.GeneratedConnector
import com.google.firebase.dataconnect.getInstance
import com.google.firebase.dataconnect.testutil.FirebaseAppUnitTestingRule
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.of
import io.kotest.property.assume
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@Suppress("ReplaceCallWithBinaryOperator")
@RunWith(AndroidJUnit4::class)
class GeneratedConnectorUnitTest {
  @get:Rule
  val firebaseAppFactory =
    FirebaseAppUnitTestingRule(
      appNameKey = "eqgmyasyky",
      applicationIdKey = "4nggx4yp4r",
      projectIdKey = "833t3hkvf2"
    )

  @Test
  fun `copy() with no arguments should return a distinct, but equal, object`() = runTest {
    checkAll(propTestConfig, Arb.generatedConnector()) { generatedConnector1 ->
      val generatedConnector2 = generatedConnector1.copy()
      assertSoftly {
        generatedConnector2 shouldNotBeSameInstanceAs generatedConnector1
        generatedConnector2.dataConnect shouldBeSameInstanceAs generatedConnector1.dataConnect
        generatedConnector2.operations().map { it.operationName } shouldContainExactlyInAnyOrder
          generatedConnector1.operations().map { it.operationName }
        generatedConnector2.queries().map { it.operationName } shouldContainExactlyInAnyOrder
          generatedConnector1.queries().map { it.operationName }
        generatedConnector2.mutations().map { it.operationName } shouldContainExactlyInAnyOrder
          generatedConnector1.mutations().map { it.operationName }
      }
    }
  }

  @Test
  fun `copy() should return an object whose properties are set to the given arguments`() = runTest {
    checkAll(propTestConfig, Arb.generatedConnector(), Arb.dataConnect()) {
      generatedConnector1,
      newDataConnect ->
      val generatedConnector2 = generatedConnector1.copy(dataConnect = newDataConnect)
      generatedConnector2.dataConnect shouldBeSameInstanceAs newDataConnect
      generatedConnector2.operations().map { it.operationName } shouldContainExactlyInAnyOrder
        generatedConnector1.operations().map { it.operationName }
      generatedConnector2.queries().map { it.operationName } shouldContainExactlyInAnyOrder
        generatedConnector1.queries().map { it.operationName }
      generatedConnector2.mutations().map { it.operationName } shouldContainExactlyInAnyOrder
        generatedConnector1.mutations().map { it.operationName }
    }
  }

  @Test
  fun `equals(null) should return false`() = runTest {
    checkAll(propTestConfig, Arb.generatedConnector()) { generatedConnector ->
      generatedConnector.equals(null) shouldBe false
    }
  }

  @Test
  fun `equals(an object of a different type) should return false`() = runTest {
    val others = Arb.of("string", 42, emptyList<Nothing>())
    checkAll(propTestConfig, Arb.generatedConnector(), others) { generatedConnector, other ->
      generatedConnector.equals(other) shouldBe false
    }
  }

  @Test
  fun `equals() with an otherwise equal but different implementation should return false`() =
    runTest {
      checkAll(propTestConfig, Arb.generatedConnector()) { generatedConnector1 ->
        val generatedConnector2 =
          object : GeneratedConnector<DemoConnector> by generatedConnector1 {}
        generatedConnector1.equals(generatedConnector2) shouldBe false
      }
    }

  @Test
  fun `equals() with itself should return true`() = runTest {
    checkAll(propTestConfig, Arb.generatedConnector()) { generatedConnector ->
      generatedConnector.equals(generatedConnector) shouldBe true
    }
  }

  @Test
  fun `equals() with a copy of itself should return true`() = runTest {
    checkAll(propTestConfig, Arb.generatedConnector()) { generatedConnector ->
      generatedConnector.equals(generatedConnector.copy()) shouldBe true
    }
  }

  @Test
  fun `equals() should return false if only dataConnect differs`() = runTest {
    checkAll(propTestConfig, Arb.generatedConnector(), Arb.dataConnect()) {
      generatedConnector1,
      newDataConnect ->
      val generatedConnector2 = generatedConnector1.copy(dataConnect = newDataConnect)
      generatedConnector1.equals(generatedConnector2) shouldBe false
    }
  }

  @Test
  fun `hashCode() should always return the same value when invoked on a given object`() = runTest {
    checkAll(propTestConfig, Arb.generatedConnector()) { generatedConnector ->
      val hashCode = generatedConnector.hashCode()
      repeat(10) { withClue("index=$it") { generatedConnector.hashCode() shouldBe hashCode } }
    }
  }

  @Test
  fun `hashCode() should return the same value when invoked on distinct, but equal objects`() =
    runTest {
      checkAll(propTestConfig, Arb.generatedConnector()) { generatedConnector ->
        generatedConnector.hashCode() shouldBe generatedConnector.copy().hashCode()
      }
    }

  @Test
  fun `hashCode() should return different values on objects with different dataConnect`() =
    runTest {
      checkAll(propTestConfig, Arb.generatedConnector(), Arb.dataConnect()) {
        generatedConnector1,
        newDataConnect ->
        assume(generatedConnector1.dataConnect.hashCode() != newDataConnect.hashCode())
        val generatedConnector2 = generatedConnector1.copy(dataConnect = newDataConnect)
        generatedConnector1.hashCode() shouldNotBe generatedConnector2.hashCode()
      }
    }

  @Test
  fun `toString() should return a string containing the expected components`() = runTest {
    checkAll(propTestConfig, Arb.generatedConnector()) { generatedConnector ->
      val toStringResult = generatedConnector.toString()
      assertSoftly {
        toStringResult shouldStartWith Regex(".*Connector(\\w|\\d)*\\(")
        toStringResult shouldEndWith ")"
        toStringResult shouldContainWithNonAbuttingText
          "dataConnect=${generatedConnector.dataConnect}"
      }
    }
  }

  private fun Arb.Companion.generatedConnector(): Arb<DemoConnector> = arbitrary {
    DemoConnector.getInstance(firebaseAppFactory.newInstance())
  }

  private fun Arb.Companion.dataConnect(
    config: Arb<ConnectorConfig> = Arb.dataConnect.connectorConfig(),
    settings: Arb<DataConnectSettings> = Arb.dataConnect.dataConnectSettings(),
  ): Arb<FirebaseDataConnect> = arbitrary {
    FirebaseDataConnect.getInstance(
      firebaseAppFactory.newInstance(),
      config.bind(),
      settings.bind(),
    )
  }

  private companion object {
    val propTestConfig = PropTestConfig(iterations = 50)
  }
}
