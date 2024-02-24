package com.google.firebase.dataconnect.connectors.ops

import com.google.firebase.dataconnect.*
import kotlinx.serialization.Serializable

@Serializable data class createPersonVariables(val data: Person_Data) {
constructor(id: String, name: String, age: Int?) : this(data=Person_Data(id=id, name=name, age=age))
@Serializable data class Person_Data(val id: String, val name: String, val age: Int?)
}


@Serializable data class createPersonData(val person_insert: MutationRef.InsertData)


typealias createPersonMutationRef = MutationRef<createPersonVariables, createPersonData>

suspend fun MutationRef<createPersonVariables, createPersonData>.execute(id: String, name: String, age: Int?) = execute(
createPersonVariables(id=id, name=name, age=age))