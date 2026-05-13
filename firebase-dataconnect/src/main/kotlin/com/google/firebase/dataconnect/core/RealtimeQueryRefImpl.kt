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

import com.google.firebase.dataconnect.DataSource
import com.google.firebase.dataconnect.ExperimentalRealtimeQueries
import com.google.firebase.dataconnect.FirebaseDataConnect
import com.google.firebase.dataconnect.QueryRef
import com.google.firebase.dataconnect.QueryRef.FetchPolicy
import com.google.firebase.dataconnect.QueryResult
import java.util.Objects
import kotlin.random.Random
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.SerializersModule

/**
 * A temporary implementation of [QueryRef] that supports "realtime subscription updates".
 *
 * "Realtime subscription updates" is currently a work-in-progress; however, when it is completed,
 * this class will be renamed to [QueryRefImpl] and the existing [QueryRefImpl] will be deleted.
 */
@ExperimentalRealtimeQueries
internal class RealtimeQueryRefImpl<Data, Variables>(
  dataConnect: FirebaseDataConnectInternal,
  operationName: String,
  variables: Variables,
  dataDeserializer: DeserializationStrategy<Data>,
  variablesSerializer: SerializationStrategy<Variables>,
  callerSdkType: FirebaseDataConnect.CallerSdkType,
  dataSerializersModule: SerializersModule?,
  variablesSerializersModule: SerializersModule?,
) :
  QueryRef<Data, Variables>,
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
  override suspend fun execute(): RealtimeQueryResultImpl =
    TODO("RealtimeQueryRefImpl only implements subscribe() so far")

  override suspend fun execute(fetchPolicy: FetchPolicy): RealtimeQueryResultImpl =
    TODO("RealtimeQueryRefImpl only implements subscribe() so far")

  override fun subscribe(): RealtimeQuerySubscriptionImpl<Data, Variables> =
    RealtimeQuerySubscriptionImpl(this, Random)

  override fun withDataConnect(
    dataConnect: FirebaseDataConnectInternal
  ): RealtimeQueryRefImpl<Data, Variables> =
    RealtimeQueryRefImpl(
      dataConnect = dataConnect,
      operationName = this.operationName,
      variables = this.variables,
      dataDeserializer = this.dataDeserializer,
      variablesSerializer = this.variablesSerializer,
      callerSdkType = this.callerSdkType,
      dataSerializersModule = this.dataSerializersModule,
      variablesSerializersModule = this.variablesSerializersModule,
    )

  override fun copy(
    operationName: String,
    variables: Variables,
    dataDeserializer: DeserializationStrategy<Data>,
    variablesSerializer: SerializationStrategy<Variables>,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
    dataSerializersModule: SerializersModule?,
    variablesSerializersModule: SerializersModule?,
  ): RealtimeQueryRefImpl<Data, Variables> =
    RealtimeQueryRefImpl(
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
  ): RealtimeQueryRefImpl<Data, NewVariables> =
    RealtimeQueryRefImpl(
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
  ): RealtimeQueryRefImpl<NewData, Variables> =
    RealtimeQueryRefImpl(
      dataConnect = dataConnect,
      operationName = operationName,
      variables = variables,
      dataDeserializer = dataDeserializer,
      variablesSerializer = variablesSerializer,
      callerSdkType = callerSdkType,
      dataSerializersModule = dataSerializersModule,
      variablesSerializersModule = variablesSerializersModule,
    )

  override fun hashCode(): Int = Objects.hash("RealtimeQueryRefImpl", super.hashCode())

  override fun equals(other: Any?): Boolean =
    other is RealtimeQueryRefImpl<*, *> && super.equals(other)

  override fun toString(): String =
    "RealtimeQueryRefImpl(" +
      "dataConnect=$dataConnect, " +
      "operationName=$operationName, " +
      "variables=$variables, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer, " +
      "callerSdkType=$callerSdkType, " +
      "dataSerializersModule=$dataSerializersModule, " +
      "variablesSerializersModule=$variablesSerializersModule" +
      ")"

  inner class RealtimeQueryResultImpl(data: Data, override val dataSource: DataSource) :
    QueryResult<Data, Variables>, OperationRefImpl<Data, Variables>.OperationResultImpl(data) {

    override val ref = this@RealtimeQueryRefImpl

    override fun equals(other: Any?) =
      other is RealtimeQueryRefImpl<*, *>.RealtimeQueryResultImpl &&
        super.equals(other) &&
        other.dataSource == dataSource

    override fun hashCode() = Objects.hash(RealtimeQueryResultImpl::class, data, ref, dataSource)

    override fun toString() =
      "RealtimeQueryResultImpl(data=$data, ref=$ref, dataSource=$dataSource)"
  }
}
