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

package com.google.firebase.dataconnect.connectors.demo

import com.google.common.truth.Truth.assertThat
import com.google.firebase.dataconnect.connectors.demo.testutil.DemoConnectorIntegrationTestBase
import com.google.firebase.dataconnect.testutil.containsWithNonAdjacentText
import kotlinx.coroutines.test.*
import org.junit.Test

class OperationBasicsIntegrationTest : DemoConnectorIntegrationTestBase() {

  @Test
  fun ref_Variables_ShouldReturnAMutationRefWithTheCorrectProperties() {
    val variables = GetFooByIdQuery.Variables("42")
    val ref = connector.getFooById.ref(variables)

    assertThat(ref.dataConnect).isSameInstanceAs(connector.dataConnect)
    assertThat(ref.variables).isSameInstanceAs(variables)
    assertThat(ref.operationName).isEqualTo(GetFooByIdQuery.operationName)
    assertThat(ref.dataDeserializer).isSameInstanceAs(GetFooByIdQuery.dataDeserializer)
    assertThat(ref.variablesSerializer).isSameInstanceAs(GetFooByIdQuery.variablesSerializer)
  }

  @Test
  fun ref_Variables_ShouldReturnsADistinctButEqualObjectOnEachInvocation() {
    val variables = GetFooByIdQuery.Variables("42")
    val ref1 = connector.getFooById.ref(variables)
    val ref2 = connector.getFooById.ref(variables)
    val ref3 = connector.getFooById.ref(variables)

    assertThat(ref1).isNotSameInstanceAs(ref2)
    assertThat(ref1).isNotSameInstanceAs(ref3)
    assertThat(ref1).isEqualTo(ref2)
    assertThat(ref1).isEqualTo(ref3)
  }

  @Test
  fun ref_Variables_AlwaysUsesTheExactSameSerializerAndDeserializerInstances() {
    // Note: This test is very important because the [QueryManager] uses object identity of the
    // variables serializer when fanning out results.
    val variables = GetFooByIdQuery.Variables("42")
    val connector1 = demoConnectorFactory.newInstance()
    val connector2 = demoConnectorFactory.newInstance()
    assertThat(connector1).isNotSameInstanceAs(connector2)

    val ref1 = demoConnectorFactory.newInstance().getFooById.ref(variables)
    val ref2 = demoConnectorFactory.newInstance().getFooById.ref(variables)

    assertThat(ref1.dataDeserializer).isSameInstanceAs(ref2.dataDeserializer)
    assertThat(ref1.variablesSerializer).isSameInstanceAs(ref2.variablesSerializer)
  }

  @Test
  fun ref_String_ShouldReturnAMutationRefThatIsEqualToRefVariables() {
    val variables = GetFooByIdQuery.Variables("42")
    val refFromString = connector.getFooById.ref("42")

    val refFromVariables = connector.getFooById.ref(variables)
    assertThat(refFromString).isEqualTo(refFromVariables)
  }

  @Test
  fun equals_ShouldReturnFalseWhenArgumentIsNull() {
    assertThat(connector.getFooById.equals(null)).isFalse()
  }

  @Test
  fun equals_ShouldReturnFalseWhenArgumentIsAnInstanceOfADifferentClass() {
    assertThat(connector.getFooById.equals("foo")).isFalse()
  }

  @Test
  fun equals_ShouldReturnFalseWhenInvokedOnADistinctObject() {
    val instance1 = demoConnectorFactory.newInstance().getFooById
    val instance2 = demoConnectorFactory.newInstance().getFooById
    assertThat(instance1).isNotSameInstanceAs(instance2)

    assertThat(instance1.equals(instance2)).isFalse()
  }

  @Test
  @Suppress("USELESS_IS_CHECK")
  fun equals_ShouldReturnFalseWhenInvokedOnAnApparentlyEqualButDifferentImplementation() {
    val instance = connector.getFooById
    val instanceAlternateImpl = GetFooByIdQueryAlternateImpl(instance)
    assertThat(instance is GetFooByIdQuery).isTrue()
    assertThat(instanceAlternateImpl is GetFooByIdQuery).isTrue()

    assertThat(instance.equals(instanceAlternateImpl)).isFalse()
  }

  @Test
  fun hashCode_ShouldReturnSameValueOnEachInvocation() {
    val hashCode1 = connector.getFooById.hashCode()
    val hashCode2 = connector.getFooById.hashCode()

    assertThat(hashCode1).isEqualTo(hashCode2)
  }

  @Test
  fun hashCode_ShouldReturnDistinctValuesOnDistinctInstances() {
    val hashCode1 = demoConnectorFactory.newInstance().getFooById.hashCode()
    val hashCode2 = demoConnectorFactory.newInstance().getFooById.hashCode()

    assertThat(hashCode1).isNotEqualTo(hashCode2)
  }

  @Test
  fun toString_ShouldReturnAStringThatStartsWithClassName() {
    val toStringResult = connector.getFooById.toString()

    assertThat(toStringResult).startsWith("GetFooByIdQueryImpl(")
    assertThat(toStringResult).endsWith(")")
  }

  @Test
  fun toString_ShouldReturnAStringThatContainsTheToStringOfTheConnectorInstance() {
    val toStringResult = connector.getFooById.toString()

    assertThat(toStringResult).containsWithNonAdjacentText("connector=${connector}")
  }

  class GetFooByIdQueryAlternateImpl(delegate: GetFooByIdQuery) : GetFooByIdQuery by delegate
}
