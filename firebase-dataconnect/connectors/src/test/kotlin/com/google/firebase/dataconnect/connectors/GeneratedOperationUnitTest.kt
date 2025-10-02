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
import com.google.firebase.dataconnect.ExperimentalFirebaseDataConnect
import com.google.firebase.dataconnect.connectors.demo.DemoConnector
import com.google.firebase.dataconnect.connectors.demo.getInstance
import com.google.firebase.dataconnect.generated.GeneratedConnector
import com.google.firebase.dataconnect.generated.GeneratedMutation
import com.google.firebase.dataconnect.generated.GeneratedOperation
import com.google.firebase.dataconnect.generated.GeneratedQuery
import com.google.firebase.dataconnect.testutil.FirebaseAppUnitTestingRule
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.mock
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.of
import io.kotest.property.assume
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@Suppress("ReplaceCallWithBinaryOperator")
@RunWith(AndroidJUnit4::class)
class GeneratedOperationUnitTest {
  @get:Rule
  val firebaseAppFactory =
    FirebaseAppUnitTestingRule(
      appNameKey = "sydvwzh68f",
      applicationIdKey = "6qy4t5mbfc",
      projectIdKey = "dj5qhzdbwy"
    )

  private val connector: DemoConnector by lazy {
    DemoConnector.getInstance(firebaseAppFactory.newInstance())
  }

  private interface TestData
  private interface TestVariables

  @Test
  fun `copy() with no arguments should return a distinct, but equal, object`() = runTest {
    checkAll(propTestConfig, Arb.generatedOperation()) { generatedOperation1 ->
      val generatedOperation2 = generatedOperation1.copy()
      assertSoftly {
        generatedOperation1 shouldNotBeSameInstanceAs generatedOperation2
        generatedOperation1.shouldHaveProperties(generatedOperation1)
      }
    }
  }

  @Test
  fun `copy() should return an object whose properties are set to the given arguments`() = runTest {
    checkAll(
      propTestConfig,
      Arb.generatedOperation(),
      Arb.demoConnector(),
      Arb.dataConnect.string(),
      Arb.mock<DeserializationStrategy<Nothing>>(),
      Arb.mock<SerializationStrategy<Any?>>(),
    ) {
      generatedOperation1,
      newConnector,
      newOperationName,
      newDataDeserializer,
      newVariablesSerializer ->
      val generatedOperation2 =
        generatedOperation1.copy(
          connector = newConnector,
          operationName = newOperationName,
          dataDeserializer = newDataDeserializer,
          variablesSerializer = newVariablesSerializer,
        )
      generatedOperation2.shouldHaveProperties(
        connector = newConnector,
        operationName = newOperationName,
        dataDeserializer = newDataDeserializer,
        variablesSerializer = newVariablesSerializer,
      )
    }
  }

  @Test
  fun `withVariablesSerializer() should return an object with the given variablesSerializer`() =
    runTest {
      checkAll(
        propTestConfig,
        Arb.generatedOperation(),
        Arb.mock<SerializationStrategy<TestVariables>>(),
      ) { generatedOperation1, newVariablesSerializer ->
        val generatedOperation2 =
          generatedOperation1.withVariablesSerializer(
            variablesSerializer = newVariablesSerializer,
          )
        generatedOperation2.shouldHaveProperties(
          generatedOperation1,
          variablesSerializer = newVariablesSerializer,
        )
      }
    }

  @Test
  fun `withDataDeserializer() should return an object with the given dataDeserializer`() = runTest {
    checkAll(
      propTestConfig,
      Arb.generatedOperation(),
      Arb.mock<DeserializationStrategy<TestData>>(),
    ) { generatedOperation1, newDataDeserializer ->
      val generatedOperation2 =
        generatedOperation1.withDataDeserializer(
          dataDeserializer = newDataDeserializer,
        )
      generatedOperation2.shouldHaveProperties(
        generatedOperation1,
        dataDeserializer = newDataDeserializer,
      )
    }
  }

  @Test
  fun `equals(null) should return false`() = runTest {
    checkAll(propTestConfig, Arb.generatedOperation()) { generatedOperation ->
      generatedOperation.equals(null) shouldBe false
    }
  }

  @Test
  fun `equals(an object of a different type) should return false`() = runTest {
    val others = Arb.of("string", 42, emptyList<Nothing>())
    checkAll(propTestConfig, Arb.generatedOperation(), others) { generatedOperation, other ->
      generatedOperation.equals(other) shouldBe false
    }
  }

  @Test
  fun `equals() with an otherwise equal but different implementation of GeneratedQuery should return false`() =
    runTest {
      checkAll(propTestConfig, Arb.generatedQuery()) { generatedQuery1 ->
        @Suppress("UNCHECKED_CAST")
        val generatedQuery2 =
          object :
            GeneratedQuery<DemoConnector, Nothing, Nothing> by (generatedQuery1
              as GeneratedQuery<DemoConnector, Nothing, Nothing>) {}
        generatedQuery1.equals(generatedQuery2) shouldBe false
      }
    }

  @Test
  fun `equals() with an otherwise equal but different implementation of GeneratedMutation should return false`() =
    runTest {
      checkAll(propTestConfig, Arb.generatedMutation()) { generatedMutation1 ->
        @Suppress("UNCHECKED_CAST")
        val generatedMutation2 =
          object :
            GeneratedMutation<DemoConnector, Nothing, Nothing> by (generatedMutation1
              as GeneratedMutation<DemoConnector, Nothing, Nothing>) {}
        generatedMutation1.equals(generatedMutation2) shouldBe false
      }
    }

  @Test
  fun `equals() with itself should return true`() = runTest {
    checkAll(propTestConfig, Arb.generatedOperation()) { generatedOperation ->
      generatedOperation.equals(generatedOperation) shouldBe true
    }
  }

  @Test
  fun `equals() with a copy of itself should return true`() = runTest {
    checkAll(propTestConfig, Arb.generatedOperation()) { generatedOperation ->
      generatedOperation.equals(generatedOperation.copy()) shouldBe true
    }
  }

  @Test
  fun `equals() should return false if only connector differs`() = runTest {
    checkAll(propTestConfig, Arb.generatedOperation(), Arb.demoConnector()) {
      generatedOperation1,
      newConnector ->
      val generatedOperation2 = generatedOperation1.copy(connector = newConnector)
      generatedOperation1.equals(generatedOperation2) shouldBe false
    }
  }

  @Test
  fun `equals() should return false if only operationName differs`() = runTest {
    checkAll(propTestConfig, Arb.generatedOperation(), Arb.dataConnect.string()) {
      generatedOperation1,
      newOperationName ->
      val generatedOperation2 = generatedOperation1.copy(operationName = newOperationName)
      generatedOperation1.equals(generatedOperation2) shouldBe false
    }
  }

  @Test
  fun `equals() should return false if only dataDeserializer differs`() = runTest {
    checkAll(
      propTestConfig,
      Arb.generatedOperation(),
      Arb.mock<DeserializationStrategy<Nothing>>()
    ) { generatedOperation1, newDataDeserializer ->
      val generatedOperation2 = generatedOperation1.copy(dataDeserializer = newDataDeserializer)
      generatedOperation1.equals(generatedOperation2) shouldBe false
    }
  }

  @Test
  fun `equals() should return false if only variablesSerializer differs`() = runTest {
    checkAll(propTestConfig, Arb.generatedOperation(), Arb.mock<SerializationStrategy<Any?>>()) {
      generatedOperation1,
      newVariablesSerializer ->
      val generatedOperation2 =
        generatedOperation1.copy(variablesSerializer = newVariablesSerializer)
      generatedOperation1.equals(generatedOperation2) shouldBe false
    }
  }

  @Test
  fun `hashCode() should always return the same value when invoked on a given object`() = runTest {
    checkAll(propTestConfig, Arb.generatedOperation()) { generatedOperation ->
      val hashCode = generatedOperation.hashCode()
      repeat(10) { withClue("index=$it") { generatedOperation.hashCode() shouldBe hashCode } }
    }
  }

  @Test
  fun `hashCode() should return the same value when invoked on distinct, but equal objects`() =
    runTest {
      checkAll(propTestConfig, Arb.generatedOperation()) { generatedOperation ->
        generatedOperation.hashCode() shouldBe generatedOperation.copy().hashCode()
      }
    }

  @Test
  fun `hashCode() should return different values on objects with different connector`() = runTest {
    checkAll(propTestConfig, Arb.generatedOperation(), Arb.demoConnector()) {
      generatedOperation1,
      newConnector ->
      assume(generatedOperation1.connector.hashCode() != newConnector.hashCode())
      val generatedOperation2 = generatedOperation1.copy(connector = newConnector)
      generatedOperation1.hashCode() shouldNotBe generatedOperation2.hashCode()
    }
  }

  @Test
  fun `hashCode() should return different values on objects with different operationName`() =
    runTest {
      checkAll(propTestConfig, Arb.generatedOperation(), Arb.dataConnect.string()) {
        generatedOperation1,
        newOperationName ->
        assume(generatedOperation1.operationName.hashCode() != newOperationName.hashCode())
        val generatedOperation2 = generatedOperation1.copy(operationName = newOperationName)
        generatedOperation1.hashCode() shouldNotBe generatedOperation2.hashCode()
      }
    }

  @Test
  fun `hashCode() should return different values on objects with different dataDeserializer`() =
    runTest {
      checkAll(
        propTestConfig,
        Arb.generatedOperation(),
        Arb.mock<DeserializationStrategy<Nothing>>()
      ) { generatedOperation1, newDataDeserializer ->
        assume(generatedOperation1.dataDeserializer.hashCode() != newDataDeserializer.hashCode())
        val generatedOperation2 = generatedOperation1.copy(dataDeserializer = newDataDeserializer)
        generatedOperation1.hashCode() shouldNotBe generatedOperation2.hashCode()
      }
    }

  @Test
  fun `hashCode() should return different values on objects with different variablesSerializer`() =
    runTest {
      checkAll(propTestConfig, Arb.generatedOperation(), Arb.mock<SerializationStrategy<Any?>>()) {
        generatedOperation1,
        newVariablesSerializer ->
        assume(
          generatedOperation1.variablesSerializer.hashCode() != newVariablesSerializer.hashCode()
        )
        val generatedOperation2 =
          generatedOperation1.copy(variablesSerializer = newVariablesSerializer)
        generatedOperation1.hashCode() shouldNotBe generatedOperation2.hashCode()
      }
    }

  @Test
  fun `toString() should return a string containing the expected components`() = runTest {
    checkAll(propTestConfig, Arb.generatedOperation()) { generatedOperation ->
      val toStringResult = generatedOperation.toString()
      assertSoftly {
        toStringResult shouldStartWith Regex(".*Generated(Query|Mutation)(\\w|\\d)*\\(")
        toStringResult shouldEndWith ")"
        toStringResult shouldContainWithNonAbuttingText "connector=${generatedOperation.connector}"
        toStringResult shouldContainWithNonAbuttingText
          "operationName=${generatedOperation.operationName}"
        toStringResult shouldContainWithNonAbuttingText
          "dataDeserializer=${generatedOperation.dataDeserializer}"
        toStringResult shouldContainWithNonAbuttingText
          "variablesSerializer=${generatedOperation.variablesSerializer}"
      }
    }
  }

  private fun Arb.Companion.demoConnector(): Arb<DemoConnector> = arbitrary {
    DemoConnector.getInstance(firebaseAppFactory.newInstance())
  }

  private fun Arb.Companion.generatedOperation(
    demoConnector: Arb<DemoConnector> = Arb.constant(connector),
  ): Arb<GeneratedOperation<DemoConnector, *, *>> = arbitrary { rs ->
    demoConnector.bind().operations().random(rs.random)
  }

  private fun Arb.Companion.generatedQuery(
    demoConnector: Arb<DemoConnector> = Arb.constant(connector),
  ): Arb<GeneratedQuery<DemoConnector, *, *>> = arbitrary { rs ->
    demoConnector.bind().queries().random(rs.random)
  }

  private fun Arb.Companion.generatedMutation(
    demoConnector: Arb<DemoConnector> = Arb.constant(connector),
  ): Arb<GeneratedMutation<DemoConnector, *, *>> = arbitrary { rs ->
    demoConnector.bind().mutations().random(rs.random)
  }

  private companion object {
    val propTestConfig = PropTestConfig(iterations = 50)

    private fun GeneratedOperation<*, *, *>.shouldHaveProperties(
      base: GeneratedOperation<*, *, *>,
      connector: GeneratedConnector<*> = base.connector,
      operationName: String = base.operationName,
      dataDeserializer: DeserializationStrategy<*> = base.dataDeserializer,
      variablesSerializer: SerializationStrategy<*> = base.variablesSerializer,
    ) = shouldHaveProperties(connector, operationName, dataDeserializer, variablesSerializer)

    private fun GeneratedOperation<*, *, *>.shouldHaveProperties(
      connector: GeneratedConnector<*>,
      operationName: String,
      dataDeserializer: DeserializationStrategy<*>,
      variablesSerializer: SerializationStrategy<*>,
    ) {
      assertSoftly {
        withClue("connector") { this.connector shouldBeSameInstanceAs connector }
        withClue("operationName") { this.operationName shouldBe operationName }
        withClue("dataDeserializer") {
          this.dataDeserializer shouldBeSameInstanceAs dataDeserializer
        }
        withClue("variablesSerializer") {
          this.variablesSerializer shouldBeSameInstanceAs variablesSerializer
        }
      }
    }
  }
}
