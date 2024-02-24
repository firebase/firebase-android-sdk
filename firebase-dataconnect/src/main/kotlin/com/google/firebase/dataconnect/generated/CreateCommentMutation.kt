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

import com.google.firebase.dataconnect.MutationRef
import kotlinx.serialization.Serializable

object CreateCommentMutation {

  @Serializable
  data class Variables(val data: CommentData) {
    constructor(
      content: String,
      postId: String
    ) : this(data = CommentData(content = content, postId = postId))
    @Serializable data class CommentData(val content: String, val postId: String)
  }
}

suspend fun MutationRef<CreateCommentMutation.Variables, Unit>.execute(
  content: String,
  postId: String
) = execute(CreateCommentMutation.Variables(content = content, postId = postId))