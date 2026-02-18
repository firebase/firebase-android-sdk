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

import com.google.firebase.dataconnect.FirebaseDataConnect.CallerSdkType
import com.google.firebase.dataconnect.core.FirebaseDataConnectInternal
import com.google.firebase.dataconnect.testutil.StubOperationRefImpl.StubData
import com.google.firebase.dataconnect.testutil.StubOperationRefImpl.StubData2
import com.google.firebase.dataconnect.testutil.StubOperationRefImpl.StubVariables
import com.google.firebase.dataconnect.testutil.StubOperationRefImpl.StubVariables2
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.next
import io.mockk.mockk
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.SerializersModule
import org.junit.Rule
import org.junit.Test

class StubOperationRefImplUnitTest {

  @get:Rule val randomSeedTestRule = RandomSeedTestRule()

  private val rs: RandomSource by randomSeedTestRule.rs

  @Test
  fun `constructor should assign arguments to correspondingly-named properties`() {
    val dataConnect: FirebaseDataConnectInternal = mockk()
    val operationName: String = Arb.dataConnect.string().next(rs)
    val variables: StubVariables = mockk()
    val dataDeserializer: DeserializationStrategy<StubData> = mockk()
    val variablesSerializer: SerializationStrategy<StubVariables> = mockk()
    val callerSdkType: CallerSdkType = mockk()
    val dataSerializersModule: SerializersModule = mockk()
    val variablesSerializersModule: SerializersModule = mockk()

    val stubOperationRefImpl =
      StubOperationRefImpl(
        dataConnect = dataConnect,
        operationName = operationName,
        variables = variables,
        dataDeserializer = dataDeserializer,
        variablesSerializer = variablesSerializer,
        callerSdkType = callerSdkType,
        dataSerializersModule = dataSerializersModule,
        variablesSerializersModule = variablesSerializersModule,
      )

    stubOperationRefImpl.shouldHavePropertyInstances(
      dataConnect = dataConnect,
      operationName = operationName,
      variables = variables,
      dataDeserializer = dataDeserializer,
      variablesSerializer = variablesSerializer,
      callerSdkType = callerSdkType,
      dataSerializersModule = dataSerializersModule,
      variablesSerializersModule = variablesSerializersModule,
    )
  }

  @Test
  fun `constructor should accept null arguments for nullable parameters`() {
    val dataConnect: FirebaseDataConnectInternal = mockk()
    val operationName: String = Arb.dataConnect.string().next(rs)
    val variables: StubVariables = mockk()
    val dataDeserializer: DeserializationStrategy<StubData> = mockk()
    val variablesSerializer: SerializationStrategy<StubVariables> = mockk()
    val callerSdkType: CallerSdkType = mockk()

    val stubOperationRefImpl =
      StubOperationRefImpl(
        dataConnect = dataConnect,
        operationName = operationName,
        variables = variables,
        dataDeserializer = dataDeserializer,
        variablesSerializer = variablesSerializer,
        callerSdkType = callerSdkType,
        dataSerializersModule = null,
        variablesSerializersModule = null,
      )

    stubOperationRefImpl.shouldHavePropertyInstances(
      dataConnect = dataConnect,
      operationName = operationName,
      variables = variables,
      dataDeserializer = dataDeserializer,
      variablesSerializer = variablesSerializer,
      callerSdkType = callerSdkType,
      dataSerializersModule = null,
      variablesSerializersModule = null,
    )
  }

  @Test
  fun `withDataConnect() should return an equal object, except for the dataConnect property`() {
    val stubOperationRefImpl1 = Arb.dataConnect.stubOperationRefImpl().next(rs)
    val newDataConnect: FirebaseDataConnectInternal = mockk()

    val stubOperationRefImpl2 = stubOperationRefImpl1.withDataConnect(newDataConnect)

    stubOperationRefImpl2.shouldHaveSamePropertyInstancesAs(
      stubOperationRefImpl1,
      dataConnect = newDataConnect,
    )
  }

  @Test
  fun `withVariablesSerializer() should return an equal object, except for the given arguments`() {
    val stubOperationRefImpl1 = Arb.dataConnect.stubOperationRefImpl().next(rs)
    val newVariables: StubVariables2 = mockk()
    val newVariablesSerializer: SerializationStrategy<StubVariables2> = mockk()
    val newVariablesSerializersModule: SerializersModule = mockk()

    val stubOperationRefImpl2 =
      stubOperationRefImpl1.withVariablesSerializer(
        variables = newVariables,
        variablesSerializer = newVariablesSerializer,
        variablesSerializersModule = newVariablesSerializersModule,
      )

    stubOperationRefImpl2.shouldHaveSamePropertyInstancesAs(
      stubOperationRefImpl1,
      variables = newVariables,
      variablesSerializer = newVariablesSerializer,
      variablesSerializersModule = newVariablesSerializersModule,
    )
  }

  @Test
  fun `withVariablesSerializer() should properly handle variablesSerializersModule=null`() {
    val stubOperationRefImpl1 = Arb.dataConnect.stubOperationRefImpl().next(rs)
    val newVariables: StubVariables2 = mockk()
    val newVariablesSerializer: SerializationStrategy<StubVariables2> = mockk()

    val stubOperationRefImpl2 =
      stubOperationRefImpl1.withVariablesSerializer(
        variables = newVariables,
        variablesSerializer = newVariablesSerializer,
        variablesSerializersModule = null,
      )

    stubOperationRefImpl2.shouldHaveSamePropertyInstancesAs(
      stubOperationRefImpl1,
      variables = newVariables,
      variablesSerializer = newVariablesSerializer,
      variablesSerializersModule = null,
    )
  }

  @Test
  fun `withVariablesSerializer() should properly handle default argument values`() {
    val stubOperationRefImpl1 = Arb.dataConnect.stubOperationRefImpl().next(rs)
    val newVariables: StubVariables2 = mockk()
    val newVariablesSerializer: SerializationStrategy<StubVariables2> = mockk()

    val stubOperationRefImpl2 =
      stubOperationRefImpl1.withVariablesSerializer(
        variables = newVariables,
        variablesSerializer = newVariablesSerializer,
      )

    stubOperationRefImpl2.shouldHaveSamePropertyInstancesAs(
      stubOperationRefImpl1,
      variables = newVariables,
      variablesSerializer = newVariablesSerializer,
    )
  }

  @Test
  fun `withDataDeserializer() should return an equal object, except for the given arguments`() {
    val stubOperationRefImpl1 = Arb.dataConnect.stubOperationRefImpl().next(rs)
    val newDataDeserializer: DeserializationStrategy<StubData2> = mockk()
    val newDataSerializersModule: SerializersModule = mockk()

    val stubOperationRefImpl2 =
      stubOperationRefImpl1.withDataDeserializer(
        dataDeserializer = newDataDeserializer,
        dataSerializersModule = newDataSerializersModule,
      )

    stubOperationRefImpl2.shouldHaveSamePropertyInstancesAs(
      stubOperationRefImpl1,
      dataDeserializer = newDataDeserializer,
      dataSerializersModule = newDataSerializersModule,
    )
  }

  @Test
  fun `withDataDeserializer() should properly handle dataSerializersModule=null`() {
    val stubOperationRefImpl1 = Arb.dataConnect.stubOperationRefImpl().next(rs)
    val newDataDeserializer: DeserializationStrategy<StubData2> = mockk()

    val stubOperationRefImpl2 =
      stubOperationRefImpl1.withDataDeserializer(
        dataDeserializer = newDataDeserializer,
        dataSerializersModule = null,
      )

    stubOperationRefImpl2.shouldHaveSamePropertyInstancesAs(
      stubOperationRefImpl1,
      dataDeserializer = newDataDeserializer,
      dataSerializersModule = null,
    )
  }

  @Test
  fun `withDataDeserializer() should properly handle default argument values`() {
    val stubOperationRefImpl1 = Arb.dataConnect.stubOperationRefImpl().next(rs)
    val newDataDeserializer: DeserializationStrategy<StubData2> = mockk()

    val stubOperationRefImpl2 =
      stubOperationRefImpl1.withDataDeserializer(
        dataDeserializer = newDataDeserializer,
      )

    stubOperationRefImpl2.shouldHaveSamePropertyInstancesAs(
      stubOperationRefImpl1,
      dataDeserializer = newDataDeserializer,
    )
  }

  @Test
  fun `copy() should return a new object with the given arguments`() {
    val stubOperationRefImpl1 = Arb.dataConnect.stubOperationRefImpl().next(rs)
    val newOperationName: String = Arb.dataConnect.string().next(rs)
    val newVariables: StubVariables = mockk()
    val newDataDeserializer: DeserializationStrategy<StubData> = mockk()
    val newVariablesSerializer: SerializationStrategy<StubVariables> = mockk()
    val newCallerSdkType: CallerSdkType = mockk()
    val newDataSerializersModule: SerializersModule = mockk()
    val newVariablesSerializersModule: SerializersModule = mockk()

    val stubOperationRefImpl2 =
      stubOperationRefImpl1.copy(
        operationName = newOperationName,
        variables = newVariables,
        dataDeserializer = newDataDeserializer,
        variablesSerializer = newVariablesSerializer,
        callerSdkType = newCallerSdkType,
        dataSerializersModule = newDataSerializersModule,
        variablesSerializersModule = newVariablesSerializersModule,
      )

    stubOperationRefImpl2.shouldHaveSamePropertyInstancesAs(
      stubOperationRefImpl1,
      operationName = newOperationName,
      variables = newVariables,
      dataDeserializer = newDataDeserializer,
      variablesSerializer = newVariablesSerializer,
      callerSdkType = newCallerSdkType,
      dataSerializersModule = newDataSerializersModule,
      variablesSerializersModule = newVariablesSerializersModule,
    )
  }

  @Test
  fun `copy() should properly handle null arguments`() {
    val stubOperationRefImpl1 = Arb.dataConnect.stubOperationRefImpl().next(rs)

    val stubOperationRefImpl2 =
      stubOperationRefImpl1.copy(
        dataSerializersModule = null,
        variablesSerializersModule = null,
      )

    stubOperationRefImpl2.shouldHaveSamePropertyInstancesAs(
      stubOperationRefImpl1,
      dataSerializersModule = null,
      variablesSerializersModule = null,
    )
  }

  @Test
  fun `copy() should properly handle default argument values`() {
    val stubOperationRefImpl1 = Arb.dataConnect.stubOperationRefImpl().next(rs)
    val newOperationName: String = Arb.dataConnect.string().next(rs)
    val newVariables: StubVariables = mockk()
    val newDataDeserializer: DeserializationStrategy<StubData> = mockk()
    val newVariablesSerializer: SerializationStrategy<StubVariables> = mockk()
    val newCallerSdkType: CallerSdkType = mockk()

    val stubOperationRefImpl2 =
      stubOperationRefImpl1.copy(
        operationName = newOperationName,
        variables = newVariables,
        dataDeserializer = newDataDeserializer,
        variablesSerializer = newVariablesSerializer,
        callerSdkType = newCallerSdkType,
      )

    stubOperationRefImpl2.shouldHaveSamePropertyInstancesAs(
      stubOperationRefImpl1,
      operationName = newOperationName,
      variables = newVariables,
      dataDeserializer = newDataDeserializer,
      variablesSerializer = newVariablesSerializer,
      callerSdkType = newCallerSdkType,
    )
  }
}
