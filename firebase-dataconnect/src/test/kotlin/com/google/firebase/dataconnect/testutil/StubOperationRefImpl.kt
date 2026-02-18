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

import com.google.firebase.dataconnect.FirebaseDataConnect
import com.google.firebase.dataconnect.core.FirebaseDataConnectInternal
import com.google.firebase.dataconnect.core.OperationRefImpl
import com.google.firebase.dataconnect.testutil.property.arbitrary.DataConnectArb
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.map
import io.mockk.mockk
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.SerializersModule

internal class StubOperationRefImpl<Data, Variables>(
  dataConnect: FirebaseDataConnectInternal,
  operationName: String,
  variables: Variables,
  dataDeserializer: DeserializationStrategy<Data>,
  variablesSerializer: SerializationStrategy<Variables>,
  callerSdkType: FirebaseDataConnect.CallerSdkType,
  dataSerializersModule: SerializersModule?,
  variablesSerializersModule: SerializersModule?,
) :
  OperationRefImpl<Data, Variables>(
    dataConnect = dataConnect,
    operationName = operationName,
    variables = variables,
    dataDeserializer = dataDeserializer,
    variablesSerializer = variablesSerializer,
    callerSdkType = callerSdkType,
    dataSerializersModule = dataSerializersModule,
    variablesSerializersModule = variablesSerializersModule,
  ) {

  override fun withDataConnect(
    dataConnect: FirebaseDataConnectInternal
  ): StubOperationRefImpl<Data, Variables> =
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

  override suspend fun execute() =
    throw UnsupportedOperationException("execute() is not implemented in StubOperationRefImpl")

  override fun copy(
    operationName: String,
    variables: Variables,
    dataDeserializer: DeserializationStrategy<Data>,
    variablesSerializer: SerializationStrategy<Variables>,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
    dataSerializersModule: SerializersModule?,
    variablesSerializersModule: SerializersModule?,
  ): StubOperationRefImpl<Data, Variables> =
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

  override fun <NewVariables> withVariablesSerializer(
    variables: NewVariables,
    variablesSerializer: SerializationStrategy<NewVariables>,
    variablesSerializersModule: SerializersModule?,
  ): StubOperationRefImpl<Data, NewVariables> =
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

  override fun <NewData> withDataDeserializer(
    dataDeserializer: DeserializationStrategy<NewData>,
    dataSerializersModule: SerializersModule?,
  ): StubOperationRefImpl<NewData, Variables> =
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

  interface StubVariables
  interface StubData
  interface StubVariables2
  interface StubData2
}

@JvmName("stubOperationRefImplForStubDataAndStubVariables")
internal fun DataConnectArb.stubOperationRefImpl(
  dataConnect: Arb<FirebaseDataConnectInternal> = arbitrary { mockk() },
  operationName: Arb<String> = string().map { "StubOperationRefImpl.operationName_$it" },
  variables: Arb<StubOperationRefImpl.StubVariables> = arbitrary { mockk() },
  dataDeserializer: Arb<DeserializationStrategy<StubOperationRefImpl.StubData>> = arbitrary {
    mockk()
  },
  variablesSerializer: Arb<SerializationStrategy<StubOperationRefImpl.StubVariables>> = arbitrary {
    mockk()
  },
  callerSdkType: Arb<FirebaseDataConnect.CallerSdkType> = arbitrary { mockk() },
  variablesSerializersModule: Arb<SerializersModule?> = arbitrary { mockk() },
  dataSerializersModule: Arb<SerializersModule?> = arbitrary { mockk() },
): Arb<StubOperationRefImpl<StubOperationRefImpl.StubData, StubOperationRefImpl.StubVariables>> =
  stubOperationRefImpl<StubOperationRefImpl.StubData, StubOperationRefImpl.StubVariables>(
    dataConnect = dataConnect,
    operationName = operationName,
    variables = variables,
    dataDeserializer = dataDeserializer,
    variablesSerializer = variablesSerializer,
    callerSdkType = callerSdkType,
    variablesSerializersModule = variablesSerializersModule,
    dataSerializersModule = dataSerializersModule,
  )

@JvmName("stubOperationRefImplGeneric")
internal inline fun <Data, reified Variables> DataConnectArb.stubOperationRefImpl(
  dataConnect: Arb<FirebaseDataConnectInternal> = arbitrary { mockk() },
  operationName: Arb<String> = string().map { "StubOperationRefImpl.operationName_$it" },
  variables: Arb<Variables> = arbitrary { mockk() },
  dataDeserializer: Arb<DeserializationStrategy<Data>> = arbitrary { mockk() },
  variablesSerializer: Arb<SerializationStrategy<Variables>> = arbitrary { mockk() },
  callerSdkType: Arb<FirebaseDataConnect.CallerSdkType> = arbitrary { mockk() },
  variablesSerializersModule: Arb<SerializersModule?> = arbitrary { mockk() },
  dataSerializersModule: Arb<SerializersModule?> = arbitrary { mockk() },
): Arb<StubOperationRefImpl<Data, Variables>> = arbitrary {
  StubOperationRefImpl(
    dataConnect = dataConnect.bind(),
    operationName = operationName.bind(),
    variables = variables.bind(),
    dataDeserializer = dataDeserializer.bind(),
    variablesSerializer = variablesSerializer.bind(),
    callerSdkType = callerSdkType.bind(),
    variablesSerializersModule = variablesSerializersModule.bind(),
    dataSerializersModule = dataSerializersModule.bind(),
  )
}
