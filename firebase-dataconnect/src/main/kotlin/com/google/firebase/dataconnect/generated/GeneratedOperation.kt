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

import com.google.firebase.dataconnect.OperationRef
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy

public interface GeneratedOperation<C : GeneratedConnector, Data, Variables> {
  public val connector: C

  public val operationName: String

  public val dataDeserializer: DeserializationStrategy<Data>
  public val variablesSerializer: SerializationStrategy<Variables>

  public fun ref(variables: Variables): OperationRef<Data, Variables> =
    connector.dataConnect.mutation(operationName, variables, dataDeserializer, variablesSerializer)
}
