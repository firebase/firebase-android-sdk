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

package com.google.firebase.dataconnect.core

import com.google.firebase.dataconnect.*
import com.google.firebase.dataconnect.util.encodeToStruct
import com.google.firebase.dataconnect.util.toStructProto
import com.google.firebase.util.nextAlphanumericString
import java.util.Objects
import kotlin.random.Random
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy

internal class MutationRefImpl<Data, Variables>(
  dataConnect: FirebaseDataConnectInternal,
  operationName: String,
  variables: Variables,
  dataDeserializer: DeserializationStrategy<Data>,
  variablesSerializer: SerializationStrategy<Variables>,
  val isFromGeneratedSdk: Boolean,
) :
  MutationRef<Data, Variables>,
  OperationRefImpl<Data, Variables>(
    dataConnect = dataConnect,
    operationName = operationName,
    variables = variables,
    dataDeserializer = dataDeserializer,
    variablesSerializer = variablesSerializer,
  ) {

  internal val logger = Logger("MutationRefImpl[$operationName]")

  override suspend fun execute(): MutationResultImpl {
    val requestId = "mut" + Random.nextAlphanumericString(length = 10)
    return dataConnect.lazyGrpcClient
      .get()
      .executeMutation(
        requestId = requestId,
        operationName = operationName,
        variables =
          if (variablesSerializer === DataConnectUntypedVariables.Serializer)
            (variables as DataConnectUntypedVariables).variables.toStructProto()
          else {
            encodeToStruct(variablesSerializer, variables)
          },
        isFromGeneratedSdk = isFromGeneratedSdk,
      )
      .runCatching { withContext(dataConnect.blockingDispatcher) { deserialize(dataDeserializer) } }
      .onFailure {
        logger.warn(it) { "executeMutation() [rid=$requestId] decoding response data failed: $it" }
      }
      .getOrThrow()
      .let { MutationResultImpl(it) }
  }

  override fun hashCode(): Int = Objects.hash("MutationRefImpl", super.hashCode())

  override fun equals(other: Any?): Boolean = other is MutationRefImpl<*, *> && super.equals(other)

  override fun toString(): String =
    "MutationRefImpl(" +
      "dataConnect=$dataConnect, " +
      "operationName=$operationName, " +
      "variables=$variables, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer" +
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

internal fun <Data, Variables> MutationRefImpl<Data, Variables>.copy(
  dataConnect: FirebaseDataConnectInternal = this.dataConnect,
  operationName: String = this.operationName,
  variables: Variables = this.variables,
  dataDeserializer: DeserializationStrategy<Data> = this.dataDeserializer,
  variablesSerializer: SerializationStrategy<Variables> = this.variablesSerializer,
  isFromGeneratedSdk: Boolean = this.isFromGeneratedSdk,
) =
  MutationRefImpl(
    dataConnect = dataConnect,
    operationName = operationName,
    variables = variables,
    dataDeserializer = dataDeserializer,
    variablesSerializer = variablesSerializer,
    isFromGeneratedSdk = isFromGeneratedSdk,
  )

internal fun <Data, NewVariables> MutationRefImpl<Data, *>.withVariablesSerializer(
  variables: NewVariables,
  variablesSerializer: SerializationStrategy<NewVariables>,
): MutationRefImpl<Data, NewVariables> =
  MutationRefImpl(
    dataConnect = dataConnect,
    operationName = operationName,
    variables = variables,
    dataDeserializer = dataDeserializer,
    variablesSerializer = variablesSerializer,
    isFromGeneratedSdk = isFromGeneratedSdk,
  )

internal fun <NewData, Variables> MutationRefImpl<*, Variables>.withDataDeserializer(
  dataDeserializer: DeserializationStrategy<NewData>,
): MutationRefImpl<NewData, Variables> =
  MutationRefImpl(
    dataConnect = dataConnect,
    operationName = operationName,
    variables = variables,
    dataDeserializer = dataDeserializer,
    variablesSerializer = variablesSerializer,
    isFromGeneratedSdk = isFromGeneratedSdk,
  )
