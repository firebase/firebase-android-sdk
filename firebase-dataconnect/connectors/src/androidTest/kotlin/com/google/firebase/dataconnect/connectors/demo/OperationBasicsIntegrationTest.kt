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
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.next
import org.junit.Test

class OperationBasicsIntegrationTest : DemoConnectorIntegrationTestBase() {

  @Test
  fun ref_Variables_ShouldReturnAMutationRefWithTheCorrectProperties() {
    val variables = Arb.getFooByIdVariables().next(rs)
    val ref = connector.getFooById.ref(variables)

    assertSoftly {
      withClue("dataConnect") { ref.dataConnect shouldBeSameInstanceAs connector.dataConnect }
      withClue("variables") { ref.variables shouldBeSameInstanceAs variables }
      withClue("operationName") { ref.operationName shouldBe GetFooByIdQuery.operationName }
      withClue("dataDeserializer") {
        ref.dataDeserializer shouldBeSameInstanceAs GetFooByIdQuery.dataDeserializer
      }
      withClue("variablesSerializer") {
        ref.variablesSerializer shouldBeSameInstanceAs GetFooByIdQuery.variablesSerializer
      }
    }
  }

  @Test
  fun ref_Variables_ShouldReturnsADistinctButEqualObjectOnEachInvocation() {
    val variables = Arb.getFooByIdVariables().next(rs)
    val ref1 = connector.getFooById.ref(variables)
    val ref2 = connector.getFooById.ref(variables)
    val ref3 = connector.getFooById.ref(variables)

    assertSoftly {
      withClue("ref1!==ref2") { ref1 shouldNotBeSameInstanceAs ref2 }
      withClue("ref1!==ref3") { ref1 shouldNotBeSameInstanceAs ref3 }
      withClue("ref1==ref2") { ref1 shouldBe ref2 }
      withClue("ref1==ref3") { ref1 shouldBe ref3 }
    }
  }

  @Test
  fun ref_Variables_AlwaysUsesTheExactSameSerializerAndDeserializerInstances() {
    // Note: This test is very important because the [QueryManager] uses object identity of the
    // variables serializer when fanning out results.
    val variables = Arb.getFooByIdVariables().next(rs)
    val connector1 = demoConnectorFactory.newInstance()
    val connector2 = demoConnectorFactory.newInstance()
    connector1 shouldNotBeSameInstanceAs connector2

    val ref1 = demoConnectorFactory.newInstance().getFooById.ref(variables)
    val ref2 = demoConnectorFactory.newInstance().getFooById.ref(variables)

    assertSoftly {
      withClue("dataDeserializer") {
        ref1.dataDeserializer shouldBeSameInstanceAs ref2.dataDeserializer
      }
      withClue("variablesSerializer") {
        ref1.variablesSerializer shouldBeSameInstanceAs ref2.variablesSerializer
      }
    }
  }

  @Test
  fun ref_String_ShouldReturnAMutationRefThatIsEqualToRefVariables() {
    val variables = Arb.getFooByIdVariables().next(rs)
    val refFromString = connector.getFooById.ref(variables.id)

    val refFromVariables = connector.getFooById.ref(variables)
    refFromString shouldBe refFromVariables
  }

  private fun Arb.Companion.getFooByIdVariables(
    string: Arb<String> = alphanumericString(prefix = "getFooByIdVariables_")
  ): Arb<GetFooByIdQuery.Variables> = arbitrary { GetFooByIdQuery.Variables(string.bind()) }
}
