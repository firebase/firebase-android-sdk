package com.google.firebase.dataconnect.connectors.ops

import com.google.firebase.dataconnect.*
import kotlinx.serialization.Serializable

@Serializable data class deletePersonVariables(val id: String)

@Serializable data class deletePersonData(val person_delete: MutationRef.DeleteData?)

typealias deletePersonMutationRef = MutationRef<deletePersonVariables, deletePersonData>

suspend fun MutationRef<deletePersonVariables, deletePersonData>.execute(id: String) =
  execute(deletePersonVariables(id = id))
