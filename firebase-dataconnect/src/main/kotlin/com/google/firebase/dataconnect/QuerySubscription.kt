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
package com.google.firebase.dataconnect

import kotlinx.coroutines.flow.*

public interface QuerySubscription<Data, Variables> {
  public val query: QueryRef<Data, Variables>

  public val lastResult: QuerySubscriptionResult<Data, Variables>?

  public val resultFlow: Flow<QuerySubscriptionResult<Data, Variables>>

  public suspend fun reload()

  public suspend fun update(variables: Variables)
}

public interface QuerySubscriptionResult<Data, Variables> {
  public val query: QueryRef<Data, Variables>
  public val result: Result<QueryResult<Data, Variables>>
}
