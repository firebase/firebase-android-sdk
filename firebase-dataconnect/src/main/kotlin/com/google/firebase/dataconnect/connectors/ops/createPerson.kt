package com.google.firebase.dataconnect.connectors.ops

import com.google.firebase.dataconnect.*
import kotlinx.serialization.Serializable

object createPerson {

@Serializable data class Variables(val data: Person_Data)

}