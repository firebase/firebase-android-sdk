package com.google.firebase.dataconnect.connectors.Posts

import com.google.firebase.dataconnect.*
import kotlinx.serialization.Serializable

@Serializable
data class createPostVariables(val data: Post_Data) {
  constructor(id: String, content: String) : this(data = Post_Data(id = id, content = content))
  @Serializable data class Post_Data(val id: String, val content: String)
}

@Serializable data class createPostData(val post_insert: MutationRef.InsertData)

typealias createPostMutationRef = MutationRef<createPostVariables, createPostData>

suspend fun MutationRef<createPostVariables, createPostData>.execute(id: String, content: String) =
  execute(createPostVariables(id = id, content = content))
