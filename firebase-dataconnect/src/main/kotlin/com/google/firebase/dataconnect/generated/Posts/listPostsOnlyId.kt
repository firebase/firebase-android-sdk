package com.google.firebase.dataconnect.connectors.Posts

import com.google.firebase.dataconnect.*
import kotlinx.serialization.Serializable

@Serializable
data class listPostsOnlyIdData(val posts: List<Post>) {
  @Serializable data class Post(val id: String)
}
