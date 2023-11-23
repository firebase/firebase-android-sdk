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
        operationSet = "crud",
        revision = "42"
      ),
      settings
    )

  val createPost: MutationRef<CreatePostMutation.Variables, Unit>
    get() =
      dataConnect.mutation(
        operationName = "createPost",
        variablesSerializer = serializer(),
        dataDeserializer = serializer()
      )

  val getPost: QueryRef<GetPostQuery.Variables, GetPostQuery.Data>
    get() =
      dataConnect.query(
        operationName = "getPost",
        variablesSerializer = serializer(),
        dataDeserializer = serializer()
      )
}
