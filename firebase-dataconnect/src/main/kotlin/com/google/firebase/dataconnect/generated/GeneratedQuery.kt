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

package com.google.firebase.dataconnect.generated

import com.google.firebase.dataconnect.ExperimentalFirebaseDataConnect
import com.google.firebase.dataconnect.FirebaseDataConnect
import com.google.firebase.dataconnect.QueryRef
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy

/**
 * The specialization of [GeneratedOperation] for queries.
 *
 * ### Safe for Concurrent Use
 *
 * All methods and properties of [GeneratedQuery] are thread-safe and may be safely called and/or
 * accessed concurrently from multiple threads and/or coroutines.
 *
 * ### Stable for Inheritance
 *
 * The [GeneratedQuery] interface _is_ stable for inheritance in third-party libraries, as new
 * methods will not be added to this interface and contracts of the existing methods will not be
 * changed, except possibly during major version number changes.
 */
public interface GeneratedQuery<Connector : GeneratedConnector<Connector>, Data, Variables> :
  GeneratedOperation<Connector, Data, Variables> {

  override fun ref(variables: Variables): QueryRef<Data, Variables> =
    connector.dataConnect.query(
      operationName,
      variables,
      dataDeserializer,
      variablesSerializer,
    ) {
      callerSdkType = FirebaseDataConnect.CallerSdkType.Generated
    }

  @ExperimentalFirebaseDataConnect
  override fun copy(
    connector: Connector,
    operationName: String,
    dataDeserializer: DeserializationStrategy<Data>,
    variablesSerializer: SerializationStrategy<Variables>,
  ): GeneratedQuery<Connector, Data, Variables>

  @ExperimentalFirebaseDataConnect
  override fun <NewVariables> withVariablesSerializer(
    variablesSerializer: SerializationStrategy<NewVariables>,
  ): GeneratedQuery<Connector, Data, NewVariables>

  @ExperimentalFirebaseDataConnect
  override fun <NewData> withDataDeserializer(
    dataDeserializer: DeserializationStrategy<NewData>,
  ): GeneratedQuery<Connector, NewData, Variables>
}
