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
data class CreateCommentVariables(val data: CommentData) {
  @Serializable data class CommentData(val content: String, val postId: String)
}

fun PostsOperationSet.Mutations.createComment(
  variables: CreateCommentVariables
): MutationRef<Unit, CreateCommentVariables> =
  operationSet.dataConnect.mutation(
    operationName = "createComment",
    variables = variables,
    responseDeserializer = serializer(),
    variablesSerializer = serializer()
  )

fun PostsOperationSet.Mutations.createComment(
  content: String,
  postId: String
): MutationRef<Unit, CreateCommentVariables> =
  createComment(
    CreateCommentVariables(
      data = CreateCommentVariables.CommentData(content = content, postId = postId)
    )
  )

suspend fun PostsOperationSet.createComment(
  variables: CreateCommentVariables
): DataConnectMutationResult<Unit, CreateCommentVariables> =
  mutations.createComment(variables).execute()

suspend fun PostsOperationSet.createComment(
  content: String,
  postId: String
): DataConnectMutationResult<Unit, CreateCommentVariables> =
  mutations.createComment(content = content, postId = postId).execute()
