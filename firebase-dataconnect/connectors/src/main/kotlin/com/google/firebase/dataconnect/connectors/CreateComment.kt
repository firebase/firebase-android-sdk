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
package com.google.firebase.dataconnect.connectors

import com.google.firebase.dataconnect.DataConnectMutationResult
import com.google.firebase.dataconnect.MutationRef
import com.google.firebase.dataconnect.mutation
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

public class CreateComment internal constructor(public val connector: PostsConnector) {

  public fun ref(variables: Variables): MutationRef<Unit, Variables> =
    connector.dataConnect.mutation(
      operationName = "createComment",
      variables = variables,
      responseDeserializer = responseDeserializer,
      variablesSerializer = variablesSerializer,
    )

  public fun ref(content: String, postId: String): MutationRef<Unit, Variables> =
    ref(Variables(data = Variables.CommentData(content = content, postId = postId)))

  @Serializable
  public data class Variables(val data: CommentData) {
    @Serializable public data class CommentData(val content: String, val postId: String)
  }

  private companion object {
    val responseDeserializer = serializer<Unit>()
    val variablesSerializer = serializer<Variables>()
  }
}

public suspend fun PostsConnector.createComment(
  content: String,
  postId: String
): DataConnectMutationResult<Unit, CreateComment.Variables> =
  createComment.ref(content = content, postId = postId).execute()
