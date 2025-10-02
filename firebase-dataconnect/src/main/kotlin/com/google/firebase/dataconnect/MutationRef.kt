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

package com.google.firebase.dataconnect

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.SerializersModule

/**
 * A specialization of [OperationRef] for _mutation_ operations.
 *
 * ### Safe for concurrent use
 *
 * All methods and properties of [MutationRef] are thread-safe and may be safely called and/or
 * accessed concurrently from multiple threads and/or coroutines.
 *
 * ### Not stable for inheritance
 *
 * The [MutationRef] interface is _not_ stable for inheritance in third-party libraries, as new
 * methods might be added to this interface or contracts of the existing methods can be changed.
 */
public interface MutationRef<Data, Variables> : OperationRef<Data, Variables> {
  override suspend fun execute(): MutationResult<Data, Variables>

  @ExperimentalFirebaseDataConnect
  override fun copy(
    operationName: String,
    variables: Variables,
    dataDeserializer: DeserializationStrategy<Data>,
    variablesSerializer: SerializationStrategy<Variables>,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
    dataSerializersModule: SerializersModule?,
    variablesSerializersModule: SerializersModule?,
  ): MutationRef<Data, Variables>

  @ExperimentalFirebaseDataConnect
  override fun <NewVariables> withVariablesSerializer(
    variables: NewVariables,
    variablesSerializer: SerializationStrategy<NewVariables>,
    variablesSerializersModule: SerializersModule?,
  ): MutationRef<Data, NewVariables>

  @ExperimentalFirebaseDataConnect
  override fun <NewData> withDataDeserializer(
    dataDeserializer: DeserializationStrategy<NewData>,
    dataSerializersModule: SerializersModule?,
  ): MutationRef<NewData, Variables>
}

/**
 * A specialization of [OperationResult] for [MutationRef].
 *
 * ### Safe for concurrent use
 *
 * All methods and properties of [MutationResult] are thread-safe and may be safely called and/or
 * accessed concurrently from multiple threads and/or coroutines.
 *
 * ### Not stable for inheritance
 *
 * The [MutationResult] interface is _not_ stable for inheritance in third-party libraries, as new
 * methods might be added to this interface or contracts of the existing methods can be changed.
 */
public interface MutationResult<Data, Variables> : OperationResult<Data, Variables> {
  override val ref: MutationRef<Data, Variables>
}
