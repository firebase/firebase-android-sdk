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

import com.google.firebase.dataconnect.DataConnectMutationResult
import com.google.firebase.dataconnect.MutationRef
import com.google.firebase.dataconnect.mutation
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

@Serializable
data class CreatePostVariables(val data: PostData) {

  val builder
    get() = Builder(data = data)

  fun build(block: Builder.() -> Unit): CreatePostVariables = builder.apply(block).build()

  @DslMarker annotation class VariablesDsl

  @VariablesDsl
  class Builder(var data: PostData) {
    fun build() = CreatePostVariables(data = data)
    fun data(id: String, content: String) {
      data = PostData(id = id, content = content)
    }
    fun data(block: PostData.Builder.() -> Unit) {
      data = data.build(block)
    }
  }

  @Serializable
  data class PostData(val id: String, val content: String) {
    val builder
      get() = Builder(id = id, content = content)

    fun build(block: Builder.() -> Unit): PostData = builder.apply(block).build()

    @VariablesDsl
    class Builder(var id: String, var content: String) {
      fun build() = PostData(id = id, content = content)
    }
  }
}

fun PostsConnector.Mutations.createPost(
  variables: CreatePostVariables
): MutationRef<Unit, CreatePostVariables> =
  connector.dataConnect.mutation(
    operationName = "createPost",
    variables = variables,
    responseDeserializer = serializer(),
    variablesSerializer = serializer()
  )

fun PostsConnector.Mutations.createPost(
  id: String,
  content: String
): MutationRef<Unit, CreatePostVariables> =
  createPost(CreatePostVariables(data = CreatePostVariables.PostData(id = id, content = content)))

suspend fun PostsConnector.createPost(
  variables: CreatePostVariables
): DataConnectMutationResult<Unit, CreatePostVariables> = mutations.createPost(variables).execute()

suspend fun PostsConnector.createPost(
  id: String,
  content: String
): DataConnectMutationResult<Unit, CreatePostVariables> =
  mutations.createPost(id = id, content = content).execute()
