package com.google.firebase.dataconnect.connectors.ops

import com.google.firebase.dataconnect.*
import kotlinx.serialization.Serializable

@Serializable
data class updatePersonVariables(val id: String, val data: Person_Data) {
  constructor(
    id: String,
    name: String,
    age: Int?
  ) : this(id = id, data = Person_Data(name = name, age = age))
  @Serializable data class Person_Data(val name: String, val age: Int?)
}

@Serializable data class updatePersonData(val person_update: MutationRef.UpdateData)

typealias updatePersonMutationRef = MutationRef<updatePersonVariables, updatePersonData>

suspend fun MutationRef<updatePersonVariables, updatePersonData>.execute(
  id: String,
  name: String,
  age: Int?
) = execute(updatePersonVariables(id = id, name = name, age = age))
