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
import com.google.firebase.dataconnect.FirebaseDataConnect
import com.google.firebase.dataconnect.FirebaseDataConnectSettings
import com.google.firebase.dataconnect.MutationRef
import com.google.firebase.dataconnect.QueryRef
import com.google.firebase.dataconnect.mutation
import com.google.firebase.dataconnect.query
import kotlinx.serialization.serializer

class PostsOperationSet(
  app: FirebaseApp,
  serviceId: String,
  location: String,
  settings: FirebaseDataConnectSettings,
) {

  val dataConnect: FirebaseDataConnect =
    FirebaseDataConnect.getInstance(
      app,
      FirebaseDataConnect.ServiceConfig(
        serviceId = serviceId,
        location = location,
        connector = "crud"
      ),
      settings
    )

  // Use `lazy` to ensure that there is only one instance of the [QueryRef] so that the serializer
  // instances encapsulated therein are also singletons. This ensures that query caching works as
  // expected, since the cache key of query results includes the serializer references, compared
  // using referential equality. If [serializer()] was documented to guarantee that it always
  // returns the same instance, then this singleton-ness would not be necessary.
  val createPost: MutationRef<CreatePostMutation.Variables, Unit> by lazy {
    dataConnect.mutation(
      operationName = "createPost",
      variablesSerializer = serializer(),
      dataDeserializer = serializer()
    )
  }

  // Use `lazy` to ensure that there is only one instance of the [QueryRef] so that the serializer
  // instances encapsulated therein are also singletons. This ensures that query caching works as
  // expected, since the cache key of query results includes the serializer references, compared
  // using referential equality. If [serializer()] was documented to guarantee that it always
  // returns the same instance, then this singleton-ness would not be necessary.
  val createComment: MutationRef<CreateCommentMutation.Variables, Unit> by lazy {
    dataConnect.mutation(
      operationName = "createComment",
      variablesSerializer = serializer(),
      dataDeserializer = serializer()
    )
  }

  // Use `lazy` to ensure that there is only one instance of the [QueryRef] so that the serializer
  // instances encapsulated therein are also singletons. This ensures that query caching works as
  // expected, since the cache key of query results includes the serializer references, compared
  // using referential equality. If [serializer()] was documented to guarantee that it always
  // returns the same instance, then this singleton-ness would not be necessary.
  val getPost: QueryRef<GetPostQuery.Variables, GetPostQuery.Data> by lazy {
    dataConnect.query(
      operationName = "getPost",
      variablesSerializer = serializer(),
      dataDeserializer = serializer()
    )
  }
}
