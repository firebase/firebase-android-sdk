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

import com.google.firebase.dataconnect.QueryRef
import com.google.firebase.dataconnect.QuerySubscriptionResult
import java.util.Objects
import kotlinx.coroutines.flow.Flow

internal class QuerySubscriptionImpl<Data, Variables>(query: QueryRefImpl<Data, Variables>) :
  QuerySubscriptionInternal<Data, Variables> {

  override val query: QueryRef<Data, Variables>
    get() = TODO("Not yet implemented")
  override val flow: Flow<QuerySubscriptionResult<Data, Variables>>
    get() = TODO("Not yet implemented")

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString(): String = "QuerySubscription(query=$query)"
  override val lastResult: QuerySubscriptionResult<Data, Variables>?
    get() = TODO("Not yet implemented")

  override suspend fun reload() {
    TODO("Not yet implemented")
  }

  override suspend fun update(variables: Variables) {
    TODO("Not yet implemented")
  }

  private inner class QuerySubscriptionResultImpl(
    override val query: QueryRefImpl<Data, Variables>,
  ) : QuerySubscriptionResult<Data, Variables> {
    override val result = TODO()

    override fun equals(other: Any?) =
      other is QuerySubscriptionImpl<*, *>.QuerySubscriptionResultImpl &&
        other.query == query &&
        other.result == result

    override fun hashCode() = Objects.hash(QuerySubscriptionResultImpl::class, query, result)

    override fun toString() = "QuerySubscriptionResultImpl(query=$query, result=$result)"
  }
}
