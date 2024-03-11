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
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.serializer

public class CreateComment internal constructor(public val connector: PostsConnector) {

  public fun ref(variables: Variables): MutationRef<Unit, Variables> =
    connector.dataConnect.mutation(
      operationName = operationName,
      variables = variables,
      dataDeserializer = dataDeserializer,
      variablesSerializer = variablesSerializer,
    )

  public fun ref(id: String, content: String, postId: String): MutationRef<Unit, Variables> =
    ref(Variables(data = Variables.CommentData(id=id, content = content, postId = postId)))

  @Serializable
  public data class Variables(val data: CommentData) {
    @Serializable public data class CommentData(val id: String, val content: String, val postId: String)
  }

  public companion object {
    public const val operationName: String = "createComment"
    public val dataDeserializer: DeserializationStrategy<Unit> = serializer<Unit>()
    public val variablesSerializer: SerializationStrategy<Variables> = serializer<Variables>()
  }
}

public suspend fun PostsConnector.createComment(
  id: String,
  content: String,
  postId: String
): DataConnectMutationResult<Unit, CreateComment.Variables> =
  createComment.ref(id=id, content = content, postId = postId).execute()
