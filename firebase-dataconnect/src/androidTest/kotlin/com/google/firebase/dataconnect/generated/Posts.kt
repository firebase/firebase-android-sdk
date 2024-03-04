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

import com.google.firebase.FirebaseApp
import com.google.firebase.dataconnect.ConnectorConfig
import com.google.firebase.dataconnect.DataConnectSettings
import com.google.firebase.dataconnect.FirebaseDataConnect
import com.google.firebase.dataconnect.MutationRef
import com.google.firebase.dataconnect.QueryRef
import com.google.firebase.dataconnect.mutation
import com.google.firebase.dataconnect.query
import kotlinx.serialization.serializer

class PostsOperationSet(
  app: FirebaseApp,
  service: String,
  location: String,
  settings: DataConnectSettings,
) {

  val dataConnect: FirebaseDataConnect =
    FirebaseDataConnect.getInstance(
      app,
      ConnectorConfig(
        connector = "crud",
        location = location,
        service = service,
      ),
      settings
    )

  // Use `lazy` to ensure that there is only one instance of the [QueryRef] so that the serializer
  // instances encapsulated therein are also singletons. This ensures that query caching works as
  // expected, since the cache key of query results includes the serializer references, compared
  // using referential equality. If [serializer()] was documented to guarantee that it always
  // returns the same instance, then this singleton-ness would not be necessary.
  val createPost: MutationRef<Unit, CreatePostVariables> by lazy {
    dataConnect.mutation(
      operationName = "createPost",
      responseDeserializer = serializer(),
      variablesSerializer = serializer(),
    )
  }

  // Use `lazy` to ensure that there is only one instance of the [QueryRef] so that the serializer
  // instances encapsulated therein are also singletons. This ensures that query caching works as
  // expected, since the cache key of query results includes the serializer references, compared
  // using referential equality. If [serializer()] was documented to guarantee that it always
  // returns the same instance, then this singleton-ness would not be necessary.
  val createComment: MutationRef<Unit, CreateCommentVariables> by lazy {
    dataConnect.mutation(
      operationName = "createComment",
      responseDeserializer = serializer(),
      variablesSerializer = serializer(),
    )
  }

  // Use `lazy` to ensure that there is only one instance of the [QueryRef] so that the serializer
  // instances encapsulated therein are also singletons. This ensures that query caching works as
  // expected, since the cache key of query results includes the serializer references, compared
  // using referential equality. If [serializer()] was documented to guarantee that it always
  // returns the same instance, then this singleton-ness would not be necessary.
  val getPost: QueryRef<GetPostResponse, GetPostVariables> by lazy {
    dataConnect.query(
      operationName = "getPost",
      responseDeserializer = serializer(),
      variablesSerializer = serializer(),
    )
  }
}
