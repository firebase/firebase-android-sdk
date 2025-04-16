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
@file:OptIn(com.google.firebase.dataconnect.ExperimentalFirebaseDataConnect::class)

package com.google.firebase.dataconnect.core

import com.google.firebase.dataconnect.DataConnectUntypedVariables
import com.google.firebase.dataconnect.FirebaseDataConnect
import com.google.firebase.dataconnect.MutationRef
import com.google.firebase.dataconnect.MutationResult
import com.google.firebase.dataconnect.core.DataConnectGrpcClientGlobals.deserialize
import com.google.firebase.dataconnect.core.LoggerGlobals.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.warn
import com.google.firebase.dataconnect.util.ProtoUtil.encodeToStruct
import com.google.firebase.dataconnect.util.ProtoUtil.toStructProto
import com.google.firebase.util.nextAlphanumericString
import java.util.Objects
import kotlin.random.Random
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.SerializersModule

internal class MutationRefImpl<Data, Variables>(
  dataConnect: FirebaseDataConnectInternal,
  operationName: String,
  variables: Variables,
  dataDeserializer: DeserializationStrategy<Data>,
  variablesSerializer: SerializationStrategy<Variables>,
  callerSdkType: FirebaseDataConnect.CallerSdkType,
  dataSerializersModule: SerializersModule?,
  variablesSerializersModule: SerializersModule?,
) :
  MutationRef<Data, Variables>,
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

  internal val logger = Logger("MutationRefImpl[$operationName]")

  override suspend fun execute(): MutationResultImpl {
    val requestId = "mut" + Random.nextAlphanumericString(length = 10)
    return dataConnect.grpcClient
      .executeMutation(
        requestId = requestId,
        operationName = operationName,
        variables =
          withContext(dataConnect.blockingDispatcher) {
            if (variablesSerializer === DataConnectUntypedVariables.Serializer) {
              (variables as DataConnectUntypedVariables).variables.toStructProto()
            } else {
              encodeToStruct(variables, variablesSerializer, variablesSerializersModule)
            }
          },
        callerSdkType,
      )
      .runCatching {
        withContext(dataConnect.blockingDispatcher) {
          deserialize(dataDeserializer, dataSerializersModule)
        }
      }
      .onFailure {
        logger.warn(it) { "executeMutation() [rid=$requestId] decoding response data failed: $it" }
      }
      .getOrThrow()
      .let { MutationResultImpl(it) }
  }

  override fun withDataConnect(
    dataConnect: FirebaseDataConnectInternal
  ): MutationRefImpl<Data, Variables> =
    MutationRefImpl(
      dataConnect = dataConnect,
      operationName = operationName,
      variables = variables,
      dataDeserializer = dataDeserializer,
      variablesSerializer = variablesSerializer,
      callerSdkType = callerSdkType,
      dataSerializersModule = dataSerializersModule,
      variablesSerializersModule = variablesSerializersModule,
    )

  override fun copy(
    operationName: String,
    variables: Variables,
    dataDeserializer: DeserializationStrategy<Data>,
    variablesSerializer: SerializationStrategy<Variables>,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
    dataSerializersModule: SerializersModule?,
    variablesSerializersModule: SerializersModule?,
  ): MutationRefImpl<Data, Variables> =
    MutationRefImpl(
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
  ): MutationRefImpl<Data, NewVariables> =
    MutationRefImpl(
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
  ): MutationRefImpl<NewData, Variables> =
    MutationRefImpl(
      dataConnect = dataConnect,
      operationName = operationName,
      variables = variables,
      dataDeserializer = dataDeserializer,
      variablesSerializer = variablesSerializer,
      callerSdkType = callerSdkType,
      dataSerializersModule = dataSerializersModule,
      variablesSerializersModule = variablesSerializersModule,
    )

  override fun hashCode(): Int = Objects.hash("MutationRefImpl", super.hashCode())

  override fun equals(other: Any?): Boolean = other is MutationRefImpl<*, *> && super.equals(other)

  override fun toString(): String =
    "MutationRefImpl(" +
      "dataConnect=$dataConnect, " +
      "operationName=$operationName, " +
      "variables=$variables, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "callerSdkType=$callerSdkType, " +
      "dataSerializersModule=$dataSerializersModule, " +
      "variablesSerializersModule=$variablesSerializersModule" +
      ")"

  inner class MutationResultImpl(data: Data) :
    MutationResult<Data, Variables>, OperationRefImpl<Data, Variables>.OperationResultImpl(data) {

    override val ref = this@MutationRefImpl

    override fun equals(other: Any?) =
      other is MutationRefImpl<*, *>.MutationResultImpl && super.equals(other)

    override fun hashCode() = Objects.hash(MutationResultImpl::class, data, ref)

    override fun toString() = "MutationResultImpl(data=$data, ref=$ref)"
  }
}
