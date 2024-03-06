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

import com.google.firebase.FirebaseApp
import com.google.firebase.dataconnect.ConnectorConfig
import com.google.firebase.dataconnect.DataConnectSettings
import com.google.firebase.dataconnect.FirebaseDataConnect

public class PostsConnector(public val dataConnect: FirebaseDataConnect) {

  public class Mutations internal constructor(public val connector: PostsConnector)

  public val mutations: Mutations = Mutations(this)

  public class Queries internal constructor(public val connector: PostsConnector)

  public val queries: Queries = Queries(this)

  public class Subscriptions internal constructor(public val connector: PostsConnector)

  public val subscriptions: Subscriptions = Subscriptions(this)

  public companion object {
    public val config: ConnectorConfig =
      ConnectorConfig(connector = "crud", location = "foo", service = "local")

    public val instance: PostsConnector
      get() = PostsConnector(FirebaseDataConnect.getInstance(config))

    public fun getInstance(app: FirebaseApp): PostsConnector =
      PostsConnector(FirebaseDataConnect.getInstance(app, config))

    public fun getInstance(settings: DataConnectSettings): PostsConnector =
      PostsConnector(FirebaseDataConnect.getInstance(config, settings))

    public fun getInstance(app: FirebaseApp, settings: DataConnectSettings): PostsConnector =
      PostsConnector(FirebaseDataConnect.getInstance(app, config, settings))
  }
}
