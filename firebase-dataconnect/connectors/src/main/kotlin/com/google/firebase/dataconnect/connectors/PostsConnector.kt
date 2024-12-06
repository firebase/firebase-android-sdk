/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.dataconnect.connectors

import com.google.firebase.FirebaseApp
import com.google.firebase.dataconnect.*
import com.google.firebase.dataconnect.connectors.demo.DemoConnector
import java.util.WeakHashMap

public class PostsConnector private constructor(public val dataConnect: FirebaseDataConnect) {

  public val getPost: GetPost by lazy { GetPost(this) }
  public val createPost: CreatePost by lazy { CreatePost(this) }
  public val createComment: CreateComment by lazy { CreateComment(this) }

  public companion object {
    public val config: ConnectorConfig = DemoConnector.config.copy(connector = "posts")

    public val instance: PostsConnector
      get() = getInstance(FirebaseDataConnect.getInstance(config))

    public fun getInstance(app: FirebaseApp): PostsConnector =
      getInstance(FirebaseDataConnect.getInstance(app, config))

    public fun getInstance(settings: DataConnectSettings): PostsConnector =
      getInstance(FirebaseDataConnect.getInstance(config, settings))

    public fun getInstance(app: FirebaseApp, settings: DataConnectSettings): PostsConnector =
      getInstance(FirebaseDataConnect.getInstance(app, config, settings))

    private fun getInstance(dataConnect: FirebaseDataConnect): PostsConnector =
      synchronized(instances) { instances.getOrPut(dataConnect) { PostsConnector(dataConnect) } }

    private val instances = WeakHashMap<FirebaseDataConnect, PostsConnector>()
  }
}
