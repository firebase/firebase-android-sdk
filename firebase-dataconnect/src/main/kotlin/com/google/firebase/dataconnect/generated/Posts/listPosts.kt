package com.google.firebase.dataconnect.connectors.Posts

import com.google.firebase.dataconnect.*
import kotlinx.serialization.Serializable

@Serializable
data class listPostsData(val posts: List<Post>) {
  @Serializable data class Post(val id: String, val content: String)
}
