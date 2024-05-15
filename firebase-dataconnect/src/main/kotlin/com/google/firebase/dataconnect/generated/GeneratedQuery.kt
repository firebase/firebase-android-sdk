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

import com.google.firebase.dataconnect.QueryRef

/**
 * The specialization of [GeneratedOperation] for queries.
 *
 * ### Safe for Concurrent Use
 *
 * All methods and properties of [GeneratedQuery] are thread-safe and may be safely called and/or
 * accessed concurrently from multiple threads and/or coroutines.
 *
 * ### Stable for Inheritance (after graduating to "Generally Available")
 *
 * The [GeneratedQuery] interface _is_ stable for inheritance in third-party libraries, as new
 * methods will not be added to this interface and contracts of the existing methods will not be
 * changed. Note, however, that this interface is still subject to changes, up to and including
 * outright deletion, until the Firebase Data Connect product graduates from "alpha" and/or "beta"
 * to "Generally Available" status.
 */
public interface GeneratedQuery<C : GeneratedConnector, Data, Variables> :
  GeneratedOperation<C, Data, Variables> {
  override fun ref(variables: Variables): QueryRef<Data, Variables> =
    connector.dataConnect.query(operationName, variables, dataDeserializer, variablesSerializer)
}
