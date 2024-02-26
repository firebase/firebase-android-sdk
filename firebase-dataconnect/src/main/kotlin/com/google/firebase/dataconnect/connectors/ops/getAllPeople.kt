package com.google.firebase.dataconnect.connectors.ops

import com.google.firebase.dataconnect.*
import kotlinx.serialization.Serializable

@Serializable
data class getAllPeopleData(val people: List<Person>) {
  @Serializable data class Person(val id: String, val name: String, val age: Int?)
}
