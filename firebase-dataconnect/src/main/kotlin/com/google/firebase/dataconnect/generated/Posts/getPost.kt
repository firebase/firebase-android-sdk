package com.google.firebase.dataconnect.connectors.Posts

import com.google.firebase.dataconnect.*
import kotlinx.serialization.Serializable

@Serializable data class getPostVariables(val id: String)

// NOTE: The `getPostData` type was tweaked by hand to get it to compile and get it into the
// correct format.
@Serializable
data class getPostData(val post: Post?) {
  @Serializable data class Post(val content: String, val comments: List<Comment>)
  @Serializable data class Comment(val id: String?, val content: String)
}

typealias getPostQueryRef = QueryRef<getPostVariables, getPostData>

typealias getPostQuerySubscription = QuerySubscription<getPostVariables, getPostData>

suspend fun QueryRef<getPostVariables, getPostData>.execute(id: String) =
  execute(getPostVariables(id = id))

fun QueryRef<getPostVariables, getPostData>.subscribe(id: String) =
  subscribe(getPostVariables(id = id))
