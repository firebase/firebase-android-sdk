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

internal abstract class OperationRefImpl<Data, Variables>(
  override val dataConnect: FirebaseDataConnectInternal,
  override val operationName: String,
  override val variables: Variables,
  override val dataDeserializer: DeserializationStrategy<Data>,
  override val variablesSerializer: SerializationStrategy<Variables>,
) : OperationRef<Data, Variables> {
  abstract override suspend fun execute(): OperationResultImpl

  override fun hashCode() =
    Objects.hash(dataConnect, operationName, variables, dataDeserializer, variablesSerializer)

  override fun equals(other: Any?) =
    other is OperationRefImpl<*, *> &&
      other.dataConnect == dataConnect &&
      other.operationName == operationName &&
      other.variables == variables &&
      other.dataDeserializer == dataDeserializer &&
      other.variablesSerializer == variablesSerializer

  override fun toString() =
    "OperationRefImpl(" +
      "dataConnect=$dataConnect, " +
      "operationName=$operationName, " +
      "variables=$variables, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer" +
      ")"

  abstract inner class OperationResultImpl(override val data: Data) :
    OperationResult<Data, Variables> {

    override val ref = this@OperationRefImpl

    override fun equals(other: Any?) =
      other is OperationRefImpl<*, *>.OperationResultImpl && other.data == data && other.ref == ref

    override fun hashCode() = Objects.hash(OperationResultImpl::class, data, ref)

    override fun toString() = "OperationResultImpl(data=$data, ref=$ref)"
  }
}
