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
package com.google.firebase.dataconnect.generated

import com.google.firebase.dataconnect.QueryRef
import com.google.firebase.dataconnect.QuerySubscription
import kotlinx.serialization.Serializable

object GetPostQuery {

  @Serializable
  data class Variables(val id: String) {

    val builder
      get() = Builder(id = id)

    fun build(block: Builder.() -> Unit): Variables = builder.apply(block).build()

    @DslMarker annotation class VariablesDsl

    @VariablesDsl
    class Builder(var id: String) {
      fun build() = Variables(id = id)
    }
  }

  @Serializable
  data class Data(val post: Post?) {
    @Serializable
    data class Post(val content: String, val comments: List<Comment>) {
      @Serializable data class Comment(val id: String, val content: String)
    }
  }
}

typealias GetPostQueryRef = QuerySubscription<GetPostQuery.Variables, GetPostQuery.Data>

typealias GetPostQuerySubscription = QuerySubscription<GetPostQuery.Variables, GetPostQuery.Data>

suspend fun QueryRef<GetPostQuery.Variables, GetPostQuery.Data>.execute(id: String) =
  execute(variablesFor(id = id))

fun QueryRef<GetPostQuery.Variables, GetPostQuery.Data>.subscribe(id: String) =
  subscribe(variablesFor(id = id))

suspend fun QuerySubscription<GetPostQuery.Variables, GetPostQuery.Data>.update(
  block: GetPostQuery.Variables.Builder.() -> Unit
) = update(variables.build(block))

private fun variablesFor(id: String) = GetPostQuery.Variables(id = id)
