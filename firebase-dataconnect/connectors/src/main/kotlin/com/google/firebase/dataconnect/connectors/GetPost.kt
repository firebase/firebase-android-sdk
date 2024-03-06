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

import com.google.firebase.dataconnect.DataConnectQueryResult
import com.google.firebase.dataconnect.QueryRef
import com.google.firebase.dataconnect.QuerySubscription
import com.google.firebase.dataconnect.query
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

public class GetPost internal constructor(public val connector: PostsConnector) {

  public fun ref(variables: Variables): QueryRef<Response, Variables> =
    connector.dataConnect.query(
      operationName = "getPost",
      variables = variables,
      responseDeserializer = responseDeserializer,
      variablesSerializer = variablesSerializer,
    )

  public fun ref(id: String): QueryRef<Response, Variables> = ref(Variables(id = id))

  @Serializable
  public data class Response(val post: Post?) {
    @Serializable
    public data class Post(val content: String, val comments: List<Comment>) {
      @Serializable public data class Comment(val id: String?, val content: String)
    }
  }

  @Serializable public data class Variables(val id: String)

  private companion object {
    val responseDeserializer = serializer<Response>()
    val variablesSerializer = serializer<Variables>()
  }
}

public suspend fun PostsConnector.getPost(
  id: String
): DataConnectQueryResult<GetPost.Response, GetPost.Variables> = getPost.ref(id = id).execute()

public fun GetPost.subscribe(id: String): QuerySubscription<GetPost.Response, GetPost.Variables> =
  ref(id = id).subscribe()
