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
  variablesSerializersModule: SerializersModule?,
  dataSerializersModule: SerializersModule?,
) :
  OperationRefImpl<Data, Variables>(
    dataConnect = dataConnect,
    operationName = operationName,
    variables = variables,
    dataDeserializer = dataDeserializer,
    variablesSerializer = variablesSerializer,
    callerSdkType = callerSdkType,
    variablesSerializersModule = variablesSerializersModule,
    dataSerializersModule = dataSerializersModule,
  ) {
  override suspend fun execute(): OperationResultImpl {
    throw UnsupportedOperationException("this stub method is not supported")
  }
}

internal fun <Data, Variables> StubOperationRefImpl<Data, Variables>.copy(
  dataConnect: FirebaseDataConnectInternal = this.dataConnect,
  operationName: String = this.operationName,
  variables: Variables = this.variables,
  dataDeserializer: DeserializationStrategy<Data> = this.dataDeserializer,
  variablesSerializer: SerializationStrategy<Variables> = this.variablesSerializer,
  callerSdkType: FirebaseDataConnect.CallerSdkType = this.callerSdkType,
  variablesSerializersModule: SerializersModule? = this.variablesSerializersModule,
  dataSerializersModule: SerializersModule? = this.dataSerializersModule,
): StubOperationRefImpl<Data, Variables> =
  StubOperationRefImpl(
    dataConnect = dataConnect,
    operationName = operationName,
    variables = variables,
    dataDeserializer = dataDeserializer,
    variablesSerializer = variablesSerializer,
    callerSdkType = callerSdkType,
    variablesSerializersModule = variablesSerializersModule,
    dataSerializersModule = dataSerializersModule,
  )
