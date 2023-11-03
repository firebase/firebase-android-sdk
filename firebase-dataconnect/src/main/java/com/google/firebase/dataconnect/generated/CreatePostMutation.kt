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

import com.google.firebase.dataconnect.FirebaseDataConnect
import com.google.firebase.dataconnect.MutationRef

class CreatePostMutation(dataConnect: FirebaseDataConnect, variables: Variables) :
  MutationRef<CreatePostMutation.Variables, Unit>(
    dataConnect = dataConnect,
    operationName = "createPost",
    operationSet = "crud",
    revision = "1234567890abcdef",
    variables = variables,
  ) {

  data class Variables(val data: PostData) {
    data class PostData(val id: String, val content: String)
  }

  override val codec =
    object : Codec<Variables, Unit> {
      override fun encodeVariables(variables: Variables) =
        mapOf("data" to variables.data.run { mapOf("id" to id, "content" to content) })

      override fun decodeResult(map: Map<String, Any?>) {}
    }
}

fun FirebaseDataConnect.mutation(variables: CreatePostMutation.Variables): CreatePostMutation =
  CreatePostMutation(dataConnect = this, variables = variables)

fun FirebaseDataConnect.Mutations.createPost(id: String, content: String): CreatePostMutation =
  dataConnect.mutation(variablesFor(id = id, content = content))

fun CreatePostMutation.update(id: String, content: String): Unit =
  update(variablesFor(id = id, content = content))

private fun variablesFor(id: String, content: String) =
  CreatePostMutation.Variables(
    data = CreatePostMutation.Variables.PostData(id = id, content = content)
  )
