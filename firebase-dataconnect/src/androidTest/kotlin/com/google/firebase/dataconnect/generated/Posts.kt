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

class PostsConnector(val dataConnect: FirebaseDataConnect) {

  class Mutations internal constructor(val connector: PostsConnector)

  val mutations = Mutations(this)

  class Queries internal constructor(val connector: PostsConnector)

  val queries = Queries(this)

  class Subscriptions internal constructor(val connector: PostsConnector)

  val subscriptions = Subscriptions(this)

  companion object {
    val config = ConnectorConfig(connector = "crud", location = "foo", service = "local")

    val instance
      get() = PostsConnector(FirebaseDataConnect.Companion.getInstance(config))

    fun getInstance(app: FirebaseApp) =
      PostsConnector(FirebaseDataConnect.Companion.getInstance(app, config))

    fun getInstance(settings: DataConnectSettings) =
      PostsConnector(FirebaseDataConnect.Companion.getInstance(config, settings))

    fun getInstance(app: FirebaseApp, settings: DataConnectSettings) =
      PostsConnector(FirebaseDataConnect.Companion.getInstance(app, config, settings))
  }
}
