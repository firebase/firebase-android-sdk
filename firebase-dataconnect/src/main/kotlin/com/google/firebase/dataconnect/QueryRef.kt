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
 * A specialization of [OperationRef] for _query_ operations.
 *
 * ### Safe for concurrent use
 *
 * All methods and properties of [QueryRef] are thread-safe and may be safely called and/or accessed
 * concurrently from multiple threads and/or coroutines.
 *
 * ### Not stable for inheritance
 *
 * The [QueryRef] interface is _not_ stable for inheritance in third-party libraries, as new methods
 * might be added to this interface or contracts of the existing methods can be changed.
 */
public interface QueryRef<Data, Variables> : OperationRef<Data, Variables> {
  override suspend fun execute(): QueryResult<Data, Variables>

  /**
   * Subscribes to a query to be notified of updates to the query's data when the query is executed.
   *
   * At this time the notifications are _not_ realtime, and are _not_ pushed from the server.
   * Instead, the notifications are sent whenever the query is explicitly executed by calling
   * [QueryRef.execute].
   *
   * @return an object that can be used to subscribe to query results.
   */
  public fun subscribe(): QuerySubscription<Data, Variables>

  @ExperimentalFirebaseDataConnect
  override fun copy(
    operationName: String,
    variables: Variables,
    dataDeserializer: DeserializationStrategy<Data>,
    variablesSerializer: SerializationStrategy<Variables>,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
    dataSerializersModule: SerializersModule?,
    variablesSerializersModule: SerializersModule?,
  ): QueryRef<Data, Variables>

  @ExperimentalFirebaseDataConnect
  override fun <NewVariables> withVariablesSerializer(
    variables: NewVariables,
    variablesSerializer: SerializationStrategy<NewVariables>,
    variablesSerializersModule: SerializersModule?,
  ): QueryRef<Data, NewVariables>

  @ExperimentalFirebaseDataConnect
  override fun <NewData> withDataDeserializer(
    dataDeserializer: DeserializationStrategy<NewData>,
    dataSerializersModule: SerializersModule?,
  ): QueryRef<NewData, Variables>
}

/**
 * A specialization of [OperationResult] for [QueryRef].
 *
 * ### Safe for concurrent use
 *
 * All methods and properties of [QueryResult] are thread-safe and may be safely called and/or
 * accessed concurrently from multiple threads and/or coroutines.
 *
 * ### Not stable for inheritance
 *
 * The [QueryResult] interface is _not_ stable for inheritance in third-party libraries, as new
 * methods might be added to this interface or contracts of the existing methods can be changed.
 */
public interface QueryResult<Data, Variables> : OperationResult<Data, Variables> {
  override val ref: QueryRef<Data, Variables>
}
