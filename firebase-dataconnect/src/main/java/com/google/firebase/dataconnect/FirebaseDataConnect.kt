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
package com.google.firebase.dataconnect

import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.app
import com.google.protobuf.Struct
import java.io.Closeable

interface FirebaseDataConnect : Closeable {

  var settings: FirebaseDataConnectSettings

  suspend fun executeQuery(ref: QueryRef, variables: Map<String, Any?>): Struct

  suspend fun executeMutation(ref: MutationRef, variables: Map<String, Any?>): Struct

  fun subscribeQuery(ref: QueryRef, variables: Map<String, Any?>): QuerySubscription

  override fun close()

  companion object {
    fun getInstance(location: String, service: String): FirebaseDataConnect =
      getInstance(Firebase.app, location, service)

    fun getInstance(app: FirebaseApp, location: String, service: String): FirebaseDataConnect =
      app.get(FirebaseDataConnectFactory::class.java).run { get(location, service) }
  }
}
