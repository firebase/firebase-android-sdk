package com.google.firebase.dataconnect.connectors.ops

import com.google.firebase.dataconnect.*
import kotlinx.serialization.Serializable

@Serializable data class getPersonVariables(val id: String)


@Serializable data class getPersonData(val person: Person) {
@Serializable data class Person(val name: String, val age: Int?)
}


typealias getPersonQueryRef = QueryRef<getPersonVariables, getPersonData>
typealias getPersonQuerySubscription = QuerySubscription<getPersonVariables, getPersonData>

suspend fun QueryRef<getPersonVariables, getPersonData>.execute(id: String) = execute(
getPersonVariables(id=id))

fun QueryRef<getPersonVariables, getPersonData>.subscribe(id: String) = subscribe(
getPersonVariables(id=id))