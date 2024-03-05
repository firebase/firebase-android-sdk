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

import com.google.firebase.dataconnect.DataConnectQueryResult
import com.google.firebase.dataconnect.QueryRef
import com.google.firebase.dataconnect.QuerySubscription
import com.google.firebase.dataconnect.query
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

@Serializable
data class GetPostResponse(val post: Post?) {
  @Serializable
  data class Post(val content: String, val comments: List<Comment>) {
    @Serializable data class Comment(val id: String?, val content: String)
  }
}

@Serializable
data class GetPostVariables(val id: String) {

  val builder
    get() = Builder(id = id)

  fun build(block: Builder.() -> Unit): GetPostVariables = builder.apply(block).build()

  @DslMarker annotation class VariablesDsl

  @VariablesDsl
  class Builder(var id: String) {
    fun build() = GetPostVariables(id = id)
  }
}

fun PostsConnector.Queries.getPost(
  variables: GetPostVariables
): QueryRef<GetPostResponse, GetPostVariables> =
  connector.dataConnect.query(
    operationName = "getPost",
    variables = variables,
    responseDeserializer = serializer(),
    variablesSerializer = serializer()
  )

fun PostsConnector.Queries.getPost(id: String): QueryRef<GetPostResponse, GetPostVariables> =
  getPost(GetPostVariables(id = id))

suspend fun PostsConnector.getPost(
  variables: GetPostVariables
): DataConnectQueryResult<GetPostResponse, GetPostVariables> = queries.getPost(variables).execute()

suspend fun PostsConnector.getPost(
  id: String
): DataConnectQueryResult<GetPostResponse, GetPostVariables> = queries.getPost(id = id).execute()

fun PostsConnector.Subscriptions.getPost(
  variables: GetPostVariables
): QuerySubscription<GetPostResponse, GetPostVariables> =
  connector.queries.getPost(variables).subscribe()

fun PostsConnector.Subscriptions.getPost(
  id: String
): QuerySubscription<GetPostResponse, GetPostVariables> =
  connector.queries.getPost(id = id).subscribe()
