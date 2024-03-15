// Copyright 2023 Google LLC
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
package com.google.firebase.dataconnect.core

import com.google.firebase.dataconnect.*
import java.util.Objects
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy

internal class QueryRefImpl<Data, Variables>(
  dataConnect: FirebaseDataConnectInternal,
  operationName: String,
  variables: Variables,
  dataDeserializer: DeserializationStrategy<Data>,
  variablesSerializer: SerializationStrategy<Variables>,
) :
  QueryRef<Data, Variables>,
  OperationRefImpl<Data, Variables>(
    dataConnect = dataConnect,
    operationName = operationName,
    variables = variables,
    dataDeserializer = dataDeserializer,
    variablesSerializer = variablesSerializer,
  ) {
  override suspend fun execute(): QueryResultImpl =
    dataConnect.lazyQueryManager.get().execute(this).let { QueryResultImpl(it.ref.getOrThrow()) }

  override fun subscribe(): QuerySubscription<Data, Variables> = QuerySubscriptionImpl(this)

  override fun hashCode(): Int = Objects.hash("QueryRefImpl", super.hashCode())

  override fun equals(other: Any?): Boolean = other is QueryRefImpl<*, *> && super.equals(other)

  override fun toString(): String =
    "QueryRefImpl(" +
      "dataConnect=$dataConnect, " +
      "operationName=$operationName, " +
      "variables=$variables, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer" +
      ")"

  inner class QueryResultImpl(data: Data) :
    QueryResult<Data, Variables>, OperationRefImpl<Data, Variables>.OperationResultImpl(data) {

    override val ref = this@QueryRefImpl

    override fun equals(other: Any?) =
      other is QueryRefImpl<*, *>.QueryResultImpl && super.equals(other)

    override fun hashCode() = Objects.hash(QueryResultImpl::class, data, ref)

    override fun toString() = "QueryResultImpl(data=$data, ref=$ref)"
  }
}

internal fun <Data, Variables> QueryRefImpl<Data, Variables>.withVariables(variables: Variables) =
  QueryRefImpl(dataConnect, operationName, variables, dataDeserializer, variablesSerializer)
