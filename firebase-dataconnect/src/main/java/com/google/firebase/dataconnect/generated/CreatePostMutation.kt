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

class CreatePostMutation(dataConnect: FirebaseDataConnect) :
  MutationRef<CreatePostMutation.Variables, Unit>(
    dataConnect = dataConnect,
    operationName = "createPost",
    operationSet = "crud",
    revision = "1234567890abcdef",
  ) {

  data class Variables(val data: PostData) {
    data class PostData(val id: String, val content: String)
  }

  override fun encodeVariables(variables: Variables) =
    mapOf("data" to variables.data.run { mapOf("id" to id, "content" to content) })

  override fun decodeResult(map: Map<String, Any?>) {}
}

val FirebaseDataConnect.Mutations.createPost
  get() = CreatePostMutation(dataConnect)

suspend fun CreatePostMutation.execute(id: String, content: String) =
  execute(variablesFor(id = id, content = content))

private fun variablesFor(id: String, content: String) =
  CreatePostMutation.Variables(
    data = CreatePostMutation.Variables.PostData(id = id, content = content)
  )
