package com.google.firebase.dataconnect.connectors.Posts

import com.google.firebase.dataconnect.*
import kotlinx.serialization.Serializable

@Serializable data class deletePostVariables(val id: String)

@Serializable data class deletePostData(val post_delete: MutationRef.DeleteData?)

typealias deletePostMutationRef = MutationRef<deletePostVariables, deletePostData>

suspend fun MutationRef<deletePostVariables, deletePostData>.execute(id: String) =
  execute(deletePostVariables(id = id))
