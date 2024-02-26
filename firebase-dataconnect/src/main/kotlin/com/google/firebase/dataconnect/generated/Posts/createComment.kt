package com.google.firebase.dataconnect.connectors.Posts

import com.google.firebase.dataconnect.*
import kotlinx.serialization.Serializable

@Serializable
data class createCommentVariables(val data: Comment_Data) {
  constructor(content: String) : this(data = Comment_Data(content = content))
  @Serializable data class Comment_Data(val content: String)
}

@Serializable data class createCommentData(val comment_insert: MutationRef.InsertData)

typealias createCommentMutationRef = MutationRef<createCommentVariables, createCommentData>

suspend fun MutationRef<createCommentVariables, createCommentData>.execute(content: String) =
  execute(createCommentVariables(content = content))
